package com.radiobz.videofetch.data

import android.content.Context
import android.os.Build
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
     * synchronized 防止并发调用导致重复解压。
     */
    @Synchronized
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

/** 下载目录管理：优先公共 Download/VideoFetch（需存储权限），无权限时自动回退到 App 专属目录 */
object DownloadPaths {

    /** 当前实际使用的保存目录（无需权限即可写） */
    fun baseDir(context: Context): File {
        val dir = if (canWritePublicDownloads()) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VideoFetch")
        } else {
            // App 专属外部目录：Android 8~15 / 鸿蒙全兼容，无需任何存储权限
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                "VideoFetch"
            )
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 是否可写公共 Download 目录 */
    fun canWritePublicDownloads(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // API 29（Android 10）+ targetSdk 30+ 时 requestLegacyExternalStorage 不生效；
            // API <= 28 有 WRITE_EXTERNAL_STORAGE（运行时已申请）
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        }

    /** 保存目录的展示名（供任务页/设置页显示） */
    fun label(context: Context): String {
        val p = baseDir(context).absolutePath
        return if (canWritePublicDownloads()) "Download/VideoFetch" else p
    }
}
