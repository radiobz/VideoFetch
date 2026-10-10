package com.radiobz.videofetch.sniff

import kotlinx.serialization.Serializable

/** 嗅探到的媒体资源 */
@Serializable
data class SniffedResource(
    val url: String,
    val typeLabel: String,
    val pageUrl: String,
    val fileName: String,
    val foundAt: Long = System.currentTimeMillis()
)

/**
 * 网页媒体流嗅探器。
 *
 * 识别策略（按优先级）：
 * 1. 常见媒体扩展名（mp4/m3u8/webm/flv/ts/mp3…）
 * 2. 流关键词（m3u8/playlist/mpd 等，即使无扩展名）
 * 3. 疑似流路径特征（/live/ /stream/ /play/ /video/ /vod/ /hls/ /dash/ 等，
 *    覆盖加密播放器、无扩展名直链等非标准 URL）
 * 4. 过滤噪音（js/css/图片/字体/接口等）
 */
object MediaSniffer {

    enum class MediaType(val label: String) {
        HLS("HLS 视频流"),
        MP4("MP4 视频"),
        TS("TS 切片"),
        DASH("DASH 视频流"),
        WEBM("WebM 视频"),
        FLV("FLV 视频"),
        AUDIO("音频"),
        OTHER("媒体资源"),
        SUSPECT("疑似视频流")
    }

    /** 确定媒体扩展名（按优先级匹配） */
    private val mediaExtOrdered = listOf(
        "m3u8" to MediaType.HLS,
        "mp4" to MediaType.MP4,
        "webm" to MediaType.WEBM,
        "flv" to MediaType.FLV,
        "ts" to MediaType.TS,
        "mpd" to MediaType.DASH,
        "m4s" to MediaType.DASH,
        "mp3" to MediaType.AUDIO,
        "aac" to MediaType.AUDIO,
        "m4a" to MediaType.AUDIO,
        "ogg" to MediaType.AUDIO,
        "wav" to MediaType.AUDIO,
        "flac" to MediaType.AUDIO,
        "opus" to MediaType.AUDIO,
        "mov" to MediaType.MP4,
        "mkv" to MediaType.MP4,
        "m4v" to MediaType.MP4,
        "3gp" to MediaType.MP4,
        "avi" to MediaType.MP4,
        "mpeg" to MediaType.MP4,
        "ogv" to MediaType.MP4,
        "wmv" to MediaType.MP4,
        "f4v" to MediaType.FLV,
        "m3u" to MediaType.HLS
    )

    /** 无扩展名但路径含这些特征 → 疑似视频流 */
    private val suspectPathPatterns = listOf(
        "/live/", "/stream", "/play/", "/video/", "/media/", "/vod/", "/hls/",
        "/dash/", "/cdn/", "/pull", "/push", "/record", "/download/",
        "manifest", "playlist", "videoplayback", "getvideo", "geturl",
        "playurl", "streamurl", "mediaurl", "videourl", "m3u8", "m3u?",
        "index.m3u8", "getvideourl", "get_play"
    )

    /** 噪音类型（不展示） */
    private val noiseExtPattern = Regex(
        "(?i)\\.(js|css|png|jpe?g|gif|svg|ico|webp|woff2?|ttf|eot|otf|json|xml|map|txt|htm|html|php|asp|aspx|jsp|do|action|vtt|srt|pdf|zip|webmanifest|scss|less|tsx|vue)\\b"
    )

    private val suspectRegexList = suspectPathPatterns.map { Regex("(?i)" + Regex.escape(it)) }

    fun detect(url: String): MediaType? {
        val u = url.trim()
        // 仅处理 http(s) 请求；blob:/data:/ws: 无法直接下载，交给 JS 层逻辑
        if (!u.startsWith("http://") && !u.startsWith("https://")) return null
        val path = u.substringBefore('?').substringBefore('#')
        val lowerPath = path.lowercase()
        val lowerUrl = u.lowercase()

        // 1. 扩展名精确匹配（最可靠）
        val lastSegment = lowerPath.substringAfterLast('/')
        for ((ext, type) in mediaExtOrdered) {
            if (lastSegment.endsWith(".$ext")) return type
        }

        // 2. 噪音过滤（无扩展名匹配时才考虑路径特征，避免误报）
        if (noiseExtPattern.containsMatchIn(path)) return null

        // 3. 流关键词（URL 任意位置，覆盖带参数/重写 URL）
        if (lowerUrl.contains("m3u8") || lowerUrl.contains("playlist.m3u")) return MediaType.HLS
        if (lowerUrl.contains(".mpd") || lowerUrl.contains(".m4s")) return MediaType.DASH

        // 4. 疑似流路径特征（无扩展名直链、CDN 拉流）
        if (suspectRegexList.any { it.containsMatchIn(lowerUrl) }) return MediaType.SUSPECT

        return null
    }

    fun isMedia(url: String): Boolean = detect(url) != null

    fun isNoise(url: String): Boolean {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) return true
        val path = u.substringBefore('?').substringBefore('#')
        if (noiseExtPattern.containsMatchIn(path)) return true
        // 纯数字/短路径且不含流特征也视为噪音
        return false
    }

    fun fileName(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val name = clean.substringAfterLast('/')
        return name.ifBlank { url.take(80) }
    }
}
