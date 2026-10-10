package com.radiobz.videofetch.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radiobz.videofetch.CrashLog
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
    var crashInfo by mutableStateOf<String?>(CrashLog.read(ctx))
        private set
    var showAllRequests by mutableStateOf(false)
        private set
    /** 是否有权限写公共下载目录 */
    var canWritePublic by mutableStateOf(DownloadPaths.canWritePublicDownloads())
        private set

    init {
        loadVersion()
        showAllRequests = ctx.getSharedPreferences("settings", 0)
            .getBoolean("sniff_show_all", false)
    }

    fun refreshStorageState() {
        canWritePublic = DownloadPaths.canWritePublicDownloads()
    }

    /** 跳转系统「所有文件访问」授权页（Android 11+） */
    fun openManageStorage() {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${ctx.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        }
    }

    fun toggleShowAllRequests() {
        showAllRequests = !showAllRequests
        ctx.getSharedPreferences("settings", 0).edit()
            .putBoolean("sniff_show_all", showAllRequests).apply()
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
            EngineManager.resetFailed()
            version = EngineManager.version(ctx) ?: "未知"
        }
    }

    fun clearCrashLog() {
        CrashLog.clear(ctx)
        crashInfo = null
    }

    fun downloadDir(): String = DownloadPaths.baseDir(ctx).absolutePath

    fun appVersion(): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull() ?: "1.0.0"
}
