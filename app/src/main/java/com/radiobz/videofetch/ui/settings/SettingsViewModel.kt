package com.radiobz.videofetch.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radiobz.videofetch.data.DownloadPaths
import com.radiobz.videofetch.data.EngineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app

    var version by mutableStateOf("加载中…")
        private set
    var updateMsg by mutableStateOf("")
        private set

    init {
        loadVersion()
    }

    fun loadVersion() {
        viewModelScope.launch(Dispatchers.IO) {
            version = EngineManager.version(ctx) ?: "未知"
        }
    }

    fun updateEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            updateMsg = "正在检查更新…"
            updateMsg = EngineManager.update(ctx)
            version = EngineManager.version(ctx) ?: "未知"
        }
    }

    fun downloadDir(): String = DownloadPaths.baseDir(ctx).absolutePath

    fun appVersion(): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull() ?: "1.0.0"
}
