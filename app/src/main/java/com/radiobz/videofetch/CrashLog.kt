package com.radiobz.videofetch

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 简易崩溃日志：捕获未处理异常写入 filesDir/crash.log，
 * 供用户反馈定位问题（设置页可查看/清除）。
 */
object CrashLog {

    private const val FILE_NAME = "crash.log"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val f = File(context.filesDir, FILE_NAME)
                f.writeText(
                    buildString {
                        appendLine("time=${System.currentTimeMillis()}")
                        appendLine("thread=${thread.name}")
                        appendLine(Log.getStackTraceString(throwable))
                    }
                )
            }
            // 交给系统默认处理器，保留系统崩溃对话框
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? = runCatching {
        val f = File(context.filesDir, FILE_NAME)
        if (f.exists()) f.readText().take(2000) else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
