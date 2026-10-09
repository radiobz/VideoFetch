package com.radiobz.videofetch

import android.app.Application
import com.radiobz.videofetch.data.DownloadEngine
import com.radiobz.videofetch.data.EngineManager
import com.radiobz.videofetch.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VideoFetchApp : Application() {

    lateinit var repository: TaskRepository
        private set
    lateinit var downloadEngine: DownloadEngine
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        repository = TaskRepository(this)
        downloadEngine = DownloadEngine(repository, this)
        // 后台初始化引擎（解压运行时），不阻塞启动
        appScope.launch {
            EngineManager.init(applicationContext)
        }
    }
}
