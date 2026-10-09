package com.radiobz.videofetch.ui.browser

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radiobz.videofetch.VideoFetchApp
import com.radiobz.videofetch.data.DownloadTask
import com.radiobz.videofetch.data.Quality
import com.radiobz.videofetch.data.TaskStatus
import com.radiobz.videofetch.sniff.SniffedResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class BrowserViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx = getApplication<VideoFetchApp>()
    private val engine = appCtx.downloadEngine
    private val repo = appCtx.repository

    var address by mutableStateOf("")
        private set
    var currentUrl by mutableStateOf("")
        private set
    var pageTitle by mutableStateOf("")
        private set
    var resources by mutableStateOf<List<SniffedResource>>(emptyList())
        private set

    fun onAddressChange(v: String) {
        address = v
    }

    fun navigate() {
        val raw = address.trim()
        if (raw.isBlank()) return
        currentUrl = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    }

    fun onPageTitle(title: String) {
        pageTitle = title
    }

    /** 来自 WebView 后台线程的嗅探回调，转主线程更新 */
    fun onResourceFound(resource: SniffedResource) {
        viewModelScope.launch(Dispatchers.Main) {
            if (resources.none { it.url == resource.url }) {
                resources = resources + resource
            }
        }
    }

    fun clearResources() {
        resources = emptyList()
    }

    fun downloadResource(resource: SniffedResource) {
        val task = DownloadTask(
            id = genId(),
            url = resource.url,
            title = resource.fileName,
            qualityLabel = Quality.SNIFF.label,
            qualityName = Quality.SNIFF.name,
            source = "sniff",
            status = TaskStatus.QUEUED
        )
        repo.add(task)
        viewModelScope.launch {
            engine.startDownload(task, Quality.SNIFF, referer = resource.pageUrl)
        }
    }

    private fun genId(): String =
        "sn${System.currentTimeMillis()}${Random.nextInt(100, 999)}"
}
