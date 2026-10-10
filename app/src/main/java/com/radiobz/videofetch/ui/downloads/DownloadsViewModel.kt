package com.radiobz.videofetch.ui.downloads

import android.app.Application
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radiobz.videofetch.VideoFetchApp
import com.radiobz.videofetch.data.DownloadPaths
import com.radiobz.videofetch.data.DownloadTask
import com.radiobz.videofetch.data.Quality
import com.radiobz.videofetch.data.TaskStatus
import kotlinx.coroutines.launch
import java.io.File

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx = getApplication<VideoFetchApp>()
    private val engine = appCtx.downloadEngine
    val repo = appCtx.repository

    var filterRunningOnly by mutableStateOf(false)
        private set

    fun toggleFilter() {
        filterRunningOnly = !filterRunningOnly
    }

    fun cancel(id: String) = engine.cancel(id)

    fun retry(task: DownloadTask) {
        val quality = runCatching { Quality.valueOf(task.qualityName) }.getOrDefault(Quality.BEST)
        val retried = task.copy(
            status = TaskStatus.QUEUED,
            progress = 0f,
            etaSeconds = -1,
            speedLine = "",
            error = null
        )
        repo.update(task.id) { retried }
        viewModelScope.launch {
            engine.startDownload(retried, quality, referer = if (task.source == "sniff") task.url else null)
        }
    }

    fun remove(id: String) {
        engine.cancel(id)
        repo.remove(id)
    }

    fun clearFinished() = repo.clearFinished()

    fun saveLocation(): String = DownloadPaths.label(appCtx)

    fun openFile(task: DownloadTask) {
        val path = task.filePath ?: return
        val f = File(path)
        if (!f.exists()) return
        val ctx = appCtx
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(f.extension.lowercase()) ?: "video/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            ctx.startActivity(Intent.createChooser(intent, "打开文件"))
        }
    }
}
