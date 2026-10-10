package com.radiobz.videofetch.ui.browser

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radiobz.videofetch.VideoFetchApp
import com.radiobz.videofetch.data.DownloadTask
import com.radiobz.videofetch.data.Quality
import com.radiobz.videofetch.data.TaskStatus
import com.radiobz.videofetch.sniff.MediaSniffer
import com.radiobz.videofetch.sniff.SniffEvents
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

    /** 上次 Toast 提醒时间（节流，避免轰炸） */
    private var lastToastAt = 0L

    /** 显示全部请求（含疑似流，调试用），来自设置开关 */
    var showAllRequests by mutableStateOf(false)
        private set

    init {
        showAllRequests = appCtx.getSharedPreferences("settings", 0)
            .getBoolean("sniff_show_all", false)
    }

    fun onAddressChange(v: String) {
        address = v
    }

    fun navigate() {
        val raw = address.trim()
        if (raw.isBlank()) return
        currentUrl = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    }

    /** 快捷入口：直接填充并打开网址 */
    fun openUrl(url: String) {
        address = url
        currentUrl = url
    }

    fun onPageTitle(title: String) {
        pageTitle = title
    }

    /** 来自 WebView 后台线程的嗅探回调，转主线程更新 */
    fun onResourceFound(resource: SniffedResource) {
        runCatching {
            // 未开启「显示全部请求」时，忽略非媒体/非疑似的一般请求
            if (!showAllRequests && resource.typeLabel == "其他请求") return
            viewModelScope.launch(Dispatchers.Main) {
                if (resources.none { it.url == resource.url }) {
                    resources = (resources + resource).take(200)
                    SniffEvents.onNewResource()
                    maybeToast(resource.typeLabel)
                }
            }
        }
    }

    /** 节流 Toast：首次发现立即提醒，之后 5 秒内不重复 */
    private fun maybeToast(label: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastAt > 5000) {
            lastToastAt = now
            runCatching {
                Toast.makeText(appCtx, "嗅探到媒体资源：$label，已加入下方列表", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearResources() {
        resources = emptyList()
        SniffEvents.resetBadge()
    }

    /** 手动添加视频链接（从开发者工具/其他渠道拿到的直链） */
    fun addManualLink(link: String) {
        val u = link.trim()
        if (u.isBlank()) return
        // 仅允许 http/https，防止 yt-dlp 选项注入
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            Toast.makeText(appCtx, "仅支持 http/https 链接", Toast.LENGTH_SHORT).show()
            return
        }
        val type = MediaSniffer.detect(u) ?: MediaSniffer.MediaType.OTHER
        val resource = SniffedResource(
            url = u,
            typeLabel = type.label,
            pageUrl = currentUrl,
            fileName = MediaSniffer.fileName(u)
        )
        if (resources.none { it.url == u }) {
            resources = resources + resource
            SniffEvents.onNewResource()
            maybeToast(type.label)
        }
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
