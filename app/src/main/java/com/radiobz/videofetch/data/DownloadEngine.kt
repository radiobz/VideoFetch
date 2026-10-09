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
        if (!EngineManager.ready) EngineManager.init(appContext)
        check(EngineManager.ready) { EngineManager.initError ?: "下载引擎初始化失败" }
    }

    /** 解析链接信息（标题/封面/时长/清晰度列表），IO 线程阻塞调用 */
    suspend fun parse(url: String): ParseResult = withContext(Dispatchers.IO) {
        ensureInit()
        val req = YoutubeDLRequest(url.trim()).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
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
            url = url.trim(),
            title = info.title ?: info.fulltitle ?: info.webpageUrlBasename ?: url.trim(),
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
                true
            } catch (e: YoutubeDL.CanceledException) {
                repo.update(task.id) { it.copy(status = TaskStatus.CANCELED, speedLine = "") }
                false
            } catch (e: Exception) {
                repo.update(task.id) {
                    it.copy(
                        status = TaskStatus.FAILED,
                        speedLine = "",
                        error = (e.message ?: "下载失败").take(300)
                    )
                }
                false
            }
        }

    /** 取消任务 */
    fun cancel(taskId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(taskId) }
        repo.update(taskId) { it.copy(status = TaskStatus.CANCELED, speedLine = "") }
    }

    private fun buildRequest(task: DownloadTask, quality: Quality, referer: String?): YoutubeDLRequest {
        val req = YoutubeDLRequest(task.url)
        when (quality) {
            Quality.BEST -> {
                req.addOption("-f", "bv*+ba/b")
                req.addOption("--merge-output-format", "mp4")
            }
            Quality.Q1080 -> {
                req.addOption("-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best")
                req.addOption("--merge-output-format", "mp4")
            }
            Quality.Q720 -> {
                req.addOption("-f", "bestvideo[height<=720]+bestaudio/best[height<=720]/best")
                req.addOption("--merge-output-format", "mp4")
            }
            Quality.Q480 -> {
                req.addOption("-f", "bestvideo[height<=480]+bestaudio/best[height<=480]/best")
                req.addOption("--merge-output-format", "mp4")
            }
            Quality.AUDIO_MP3 -> {
                req.addOption("-f", "bestaudio")
                req.addOption("--extract-audio")
                req.addOption("--audio-format", "mp3")
                req.addOption("--audio-quality", "0")
            }
            Quality.SNIFF -> {
                req.addOption("-f", "best")
                req.addOption("--no-playlist")
            }
        }
        val outDir = DownloadPaths.baseDir(appContext)
        req.addOption("-o", File(outDir, "%(title)s [%(id)s].%(ext)s").absolutePath)
        req.addOption("--no-overwrites")
        req.addOption("--newline")
        if (task.source == "sniff" && !referer.isNullOrBlank()) {
            req.addOption("--referer", referer)
        }
        return req
    }
}
