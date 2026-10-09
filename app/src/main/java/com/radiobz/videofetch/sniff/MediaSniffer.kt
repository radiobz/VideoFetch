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

/** 网页媒体流嗅探器：识别 m3u8/mp4/ts 等媒体 URL 特征 */
object MediaSniffer {

    enum class MediaType(val label: String) {
        HLS("HLS 视频流"),
        MP4("MP4 视频"),
        TS("TS 切片"),
        WEBM("WebM 视频"),
        FLV("FLV 视频"),
        AUDIO("音频"),
        OTHER("媒体资源")
    }

    // 按优先级匹配：m3u8 关键词 → 常见媒体扩展名
    private val rules: List<Pair<Regex, MediaType>> = listOf(
        Regex("(?i)^https?://[^\\s]*m3u8") to MediaType.HLS,
        Regex("(?i)^https?://[^\\s]+\\.mp4($|[?#])") to MediaType.MP4,
        Regex("(?i)^https?://[^\\s]+\\.webm($|[?#])") to MediaType.WEBM,
        Regex("(?i)^https?://[^\\s]+\\.flv($|[?#])") to MediaType.FLV,
        Regex("(?i)^https?://[^\\s]+\\.ts($|[?#])") to MediaType.TS,
        Regex("(?i)^https?://[^\\s]+\\.(mp3|aac|m4a|ogg|wav|flac|opus)($|[?#])") to MediaType.AUDIO,
        Regex("(?i)^https?://[^\\s]+\\.(mov|mkv|m4v|3gp)($|[?#])") to MediaType.OTHER
    )

    fun detect(url: String): MediaType? =
        rules.firstOrNull { it.first.matches(url.trim()) }?.second

    fun isMedia(url: String): Boolean = detect(url) != null

    fun fileName(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val name = clean.substringAfterLast('/')
        return name.ifBlank { url.take(80) }
    }
}
