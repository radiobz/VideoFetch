package com.radiobz.videofetch

import android.app.Application
import com.radiobz.videofetch.data.DownloadEngine
import com.radiobz.videofetch.data.TaskRepository

class VideoFetchApp : Application() {

    lateinit var repository: TaskRepository
        private set
    lateinit var downloadEngine: DownloadEngine
        private set

    override fun onCreate() {
        super.onCreate()
        // 崩溃日志捕获（任何未处理异常写入 filesDir/crash.log，设置页可查看）
        CrashLog.install(this)
        repository = TaskRepository(this)
        downloadEngine = DownloadEngine(repository, this)
        // 注意：引擎改为懒初始化——首次解析/下载时才解压运行时，
        // 避免启动阶段执行重 IO 导致进程被杀或异常
    }
}
