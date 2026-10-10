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

    @Volatile
    private var failed: Boolean = false

    /**
     * 初始化引擎（首次会解压 Python/ffmpeg 运行时，耗时数秒）。
     * 捕获所有异常（含 Error），任何失败都不会让应用崩溃；
     * 失败后不再重复尝试，避免反复解压。
     */
    fun init(context: Context): Boolean {
        if (ready) return true
        if (failed) return false
        return try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            ready = true
            true
        } catch (t: Throwable) {
            initError = t.message ?: t.javaClass.simpleName
            failed = true
            false
        }
    }

    /** 重置失败标记（更新引擎后允许重新初始化） */
    fun resetFailed() {
        failed = false
    }

    fun version(context: Context): String? =
        runCatching { YoutubeDL.version(context) }.getOrNull()

    fun update(context: Context): String = try {
        when (YoutubeDL.getInstance().updateYoutubeDL(context)) {
            YoutubeDL.UpdateStatus.DONE -> "引擎已更新到最新版"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "引擎已是最新版"
            null -> "更新失败：无可用更新"
        }
    } catch (t: Throwable) {
        "更新失败：${t.message?.take(120) ?: "未知错误"}"
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
