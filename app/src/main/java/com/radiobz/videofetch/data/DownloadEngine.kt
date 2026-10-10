package com.radiobz.videofetch.data

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** yt-dlp 引擎封装：链接解析 + 下载执行 */
class DownloadEngine(
    private val repo: TaskRepository,
    private val appContext: Context
) {

    private val speedRe = Regex("at ([\\d.]+[KMG]?i?B/s)")
    private val destRe = Regex("Destination: (.+)")
    private val mergeRe = Regex("Merging formats into \"(.+)\"")

    private fun ensureInit() {
        if (!EngineManager.ready) {
            val ok = EngineManager.init(appContext)
            check(ok) { EngineManager.initError ?: "下载引擎初始化失败" }
        }
    }

    companion object {
        /** 模拟手机浏览器请求特征，绕过部分站点的基础风控 */
        val MOBILE_UA: String =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /** 已知需要 Referer 的站点域名 → 默认 Referer */
        private val refererMap = mapOf(
            "bilibili.com" to "https://www.bilibili.com/",
            "b23.tv" to "https://www.bilibili.com/",
            "douyin.com" to "https://www.douyin.com/",
            "iesdouyin.com" to "https://www.douyin.com/"
        )

        fun defaultReferer(url: String): String? =
            refererMap.entries.firstOrNull { (host, _) -> url.contains(host) }?.value

        /** 校验并规整 URL：仅允许 http/https，防止选项注入与非预期协议 */
        fun requireHttpUrl(raw: String): String {
            val u = raw.trim()
            if (!u.startsWith("http://") && !u.startsWith("https://")) {
                throw IllegalArgumentException("仅支持 http/https 链接：${u.take(80)}")
            }
            return u
        }
    }

    /** 解析链接信息（标题/封面/时长/清晰度列表），IO 线程阻塞调用 */
    suspend fun parse(url: String): ParseResult = withContext(Dispatchers.IO) {
        ensureInit()
        val u = requireHttpUrl(url)
        // 注意：URL 通过 addCommands 追加到参数末尾，并以 "--" 分隔，
        // 防止 URL 以 "-" 开头被 yt-dlp 当作选项（选项注入防护）
        val req = YoutubeDLRequest(emptyList()).apply {
            addCommands(listOf("--", u))
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--user-agent", MOBILE_UA)
            defaultReferer(u)?.let { addOption("--referer", it) }
        }
        val info = YoutubeDL.getInstance().getInfo(req)
        val formats = (info.formats ?: emptyList())
            .filter { !it.url.isNullOrBlank() }
            .distinctBy { it.formatId }
            .sortedByDescending { it.height }
            .map { f ->
                FormatItem(
                    formatId = f.formatId ?: "unknown",
                    label = buildLabel(f),
                    ext = f.ext ?: "?",
                    height = f.height,
                    hasVideo = !f.vcodec.isNullOrBlank() && f.vcodec != "none",
                    hasAudio = !f.acodec.isNullOrBlank() && f.acodec != "none",
                    fileSize = if (f.fileSize > 0) f.fileSize else f.fileSizeApproximate
                )
            }
        ParseResult(
            url = u,
            title = info.title ?: info.fulltitle ?: info.webpageUrlBasename ?: u,
            thumbnail = info.thumbnail,
            uploader = info.uploader,
            duration = info.duration,
            formats = formats
        )
    }

    private fun buildLabel(f: com.yausername.youtubedl_android.mapper.VideoFormat): String {
        val parts = mutableListOf<String>()
        val formatNote = f.formatNote
        val ext = f.ext
        if (f.height > 0) parts.add("${f.height}p")
        if (!formatNote.isNullOrBlank()) parts.add(formatNote)
        if (f.fps >= 50) parts.add("${f.fps}fps")
        if (!ext.isNullOrBlank()) parts.add(ext)
        val hasV = !f.vcodec.isNullOrBlank() && f.vcodec != "none"
        val hasA = !f.acodec.isNullOrBlank() && f.acodec != "none"
        parts.add(
            when {
                hasV && hasA -> "音视频"
                hasV -> "仅视频"
                hasA -> "仅音频"
                else -> "未知"
            }
        )
        return parts.joinToString(" ")
    }

    /** 执行下载，阻塞直到完成/失败/取消；进度实时写回仓库 */
    suspend fun startDownload(task: DownloadTask, quality: Quality, referer: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            try {
                ensureInit()
                repo.update(task.id) {
                    it.copy(status = TaskStatus.RUNNING, progress = 0f, speedLine = "准备中…", error = null)
                }
                val req = buildRequest(task, quality, referer)
                YoutubeDL.getInstance().execute(req, task.id) { progress, eta, line ->
                    // 取消后忽略进度回写，防止状态被覆盖回 RUNNING
                    val current = repo.get(task.id)
                    if (current == null || current.status == TaskStatus.CANCELED) return@execute
                    val speed = speedRe.find(line)?.groupValues?.get(1) ?: ""
                    var path: String? = null
                    destRe.find(line)?.groupValues?.get(1)?.let { path = it }
                    mergeRe.find(line)?.groupValues?.get(1)?.let { path = it }
                    val p = path
                    repo.update(task.id) {
                        it.copy(
                            status = TaskStatus.RUNNING,
                            progress = if (progress >= 0f) progress else it.progress,
                            etaSeconds = eta,
                            speedLine = if (speed.isNotBlank()) "速度 $speed" else it.speedLine,
                            filePath = p ?: it.filePath
                        )
                    }
                }
                repo.update(task.id) {
                    it.copy(status = TaskStatus.COMPLETED, progress = 100f, speedLine = "已完成")
                }
                repo.flush()
                true
            } catch (e: YoutubeDL.CanceledException) {
                repo.update(task.id) { it.copy(status = TaskStatus.CANCELED, speedLine = "") }
                repo.flush()
                false
            } catch (e: Throwable) {
                repo.update(task.id) {
                    it.copy(
                        status = TaskStatus.FAILED,
                        speedLine = "",
                        error = (e.message ?: "下载失败").take(300)
                    )
                }
                repo.flush()
                false
            }
        }

    /** 取消任务 */
    fun cancel(taskId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(taskId) }
        repo.update(taskId) { it.copy(status = TaskStatus.CANCELED, speedLine = "") }
    }

    private fun buildRequest(task: DownloadTask, quality: Quality, referer: String?): YoutubeDLRequest {
        val u = requireHttpUrl(task.url)
        val req = YoutubeDLRequest(emptyList()).apply {
            addCommands(listOf("--", u))
            when (quality) {
                Quality.BEST -> {
                    addOption("-f", "bv*+ba/b")
                    addOption("--merge-output-format", "mp4")
                }
                Quality.Q1080 -> {
                    addOption("-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best")
                    addOption("--merge-output-format", "mp4")
                }
                Quality.Q720 -> {
                    addOption("-f", "bestvideo[height<=720]+bestaudio/best[height<=720]/best")
                    addOption("--merge-output-format", "mp4")
                }
                Quality.Q480 -> {
                    addOption("-f", "bestvideo[height<=480]+bestaudio/best[height<=480]/best")
                    addOption("--merge-output-format", "mp4")
                }
                Quality.AUDIO_MP3 -> {
                    addOption("-f", "bestaudio")
                    addOption("--extract-audio")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                }
                Quality.SNIFF -> {
                    addOption("-f", "best")
                    addOption("--no-playlist")
                }
            }
            val outDir = DownloadPaths.baseDir(appContext)
            addOption("-o", File(outDir, "%(title)s [%(id)s].%(ext)s").absolutePath)
            addOption("--no-overwrites")
            addOption("--newline")
            addOption("--user-agent", MOBILE_UA)
            val siteReferer = defaultReferer(u)
            // referer 仅接受 http/https，防止注入
            if (task.source == "sniff" && !referer.isNullOrBlank() && referer.startsWith("http")) {
                addOption("--referer", referer)
            } else if (siteReferer != null) {
                addOption("--referer", siteReferer)
            }
        }
        return req
    }
}
