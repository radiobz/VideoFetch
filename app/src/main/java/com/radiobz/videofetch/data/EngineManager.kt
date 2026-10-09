package com.radiobz.videofetch.data

import android.content.Context
import android.os.Environment
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File

/** yt-dlp 引擎的初始化、版本与更新管理 */
object EngineManager {
    @Volatile
    var ready: Boolean = false
        private set

    @Volatile
    var initError: String? = null
        private set

    /** 必须在后台线程调用（首次会解压 Python/yt-dlp 运行时，耗时数秒） */
    fun init(context: Context) {
        if (ready) return
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            ready = true
        } catch (e: Exception) {
            initError = e.message
        }
    }

    fun version(context: Context): String? =
        runCatching { YoutubeDL.version(context) }.getOrNull()

    fun update(context: Context): String = try {
        when (YoutubeDL.getInstance().updateYoutubeDL(context)) {
            YoutubeDL.UpdateStatus.DONE -> "引擎已更新到最新版"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "引擎已是最新版"
            null -> "更新失败：无可用更新"
        }
    } catch (e: Exception) {
        "更新失败：${e.message?.take(120) ?: "未知错误"}"
    }
}

/** 下载目录管理：固定保存到公共 Download/VideoFetch */
object DownloadPaths {
    fun baseDir(context: Context): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, "VideoFetch")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
