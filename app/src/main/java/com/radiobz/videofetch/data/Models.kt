package com.radiobz.videofetch.data

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    QUEUED, RUNNING, COMPLETED, FAILED, CANCELED
}

/** 用户可选的清晰度档位 */
enum class Quality(val label: String) {
    BEST("最佳画质"),
    Q1080("1080P"),
    Q720("720P"),
    Q480("480P"),
    AUDIO_MP3("仅音频 MP3"),
    SNIFF("网页资源(最佳)");

    /** 映射为 yt-dlp 参数 */
    fun toArgs(): List<String> = when (this) {
        BEST -> listOf("-f", "bv*+ba/b", "--merge-output-format", "mp4")
        Q1080 -> listOf("-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best", "--merge-output-format", "mp4")
        Q720 -> listOf("-f", "bestvideo[height<=720]+bestaudio/best[height<=720]/best", "--merge-output-format", "mp4")
        Q480 -> listOf("-f", "bestvideo[height<=480]+bestaudio/best[height<=480]/best", "--merge-output-format", "mp4")
        AUDIO_MP3 -> listOf("-f", "bestaudio", "--extract-audio", "--audio-format", "mp3", "--audio-quality", "0")
        SNIFF -> listOf("-f", "best", "--no-playlist")
    }
}

@Serializable
data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val qualityLabel: String,
    val qualityName: String = "",
    val source: String,          // "ytdlp" 链接解析 / "sniff" 网页嗅探
    val downloadMode: String = "ytdlp", // "ytdlp" 引擎下载 / "bili_api" B站官方API直连
    val status: TaskStatus,
    val progress: Float = 0f,    // 0-100
    val etaSeconds: Long = -1,
    val speedLine: String = "",
    val filePath: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** 解析结果的展示模型 */
data class ParseResult(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val uploader: String?,
    val duration: Int,
    val formats: List<FormatItem>,
    val downloadMode: String = "ytdlp" // "ytdlp" / "bili_api"
)

data class FormatItem(
    val formatId: String,
    val label: String,     // 清晰度/编码描述
    val ext: String,
    val height: Int,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val fileSize: Long
)
