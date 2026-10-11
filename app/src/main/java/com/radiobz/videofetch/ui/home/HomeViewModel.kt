package com.radiobz.videofetch.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radiobz.videofetch.VideoFetchApp
import com.radiobz.videofetch.data.DownloadTask
import com.radiobz.videofetch.data.EngineManager
import com.radiobz.videofetch.data.ParseResult
import com.radiobz.videofetch.data.Quality
import com.radiobz.videofetch.data.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface HomeState {
    data object Idle : HomeState
    data object Parsing : HomeState
    data class Result(val parse: ParseResult) : HomeState
    data class Error(val msg: String) : HomeState
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx = getApplication<VideoFetchApp>()
    private val engine = appCtx.downloadEngine
    private val repo = appCtx.repository

    var input by mutableStateOf("")
        private set
    var state by mutableStateOf<HomeState>(HomeState.Idle)
        private set
    var selectedQuality by mutableStateOf(Quality.BEST)
        private set
    var started by mutableStateOf(false)
        private set
    var updateMsg by mutableStateOf("")
        private set

    fun onInputChange(v: String) {
        input = v
    }

    fun parse() {
        val url = input.trim()
        if (url.isBlank()) return
        state = HomeState.Parsing
        started = false
        viewModelScope.launch {
            try {
                state = HomeState.Result(engine.parse(url))
            } catch (e: Throwable) {
                state = HomeState.Error(e.message ?: "解析失败，请检查链接是否有效")
            }
        }
    }

    fun selectQuality(q: Quality) {
        selectedQuality = q
    }

    fun startDownload(parse: ParseResult) {
        val task = DownloadTask(
            id = genId(),
            url = parse.url,
            title = parse.title,
            qualityLabel = selectedQuality.label,
            qualityName = selectedQuality.name,
            source = "ytdlp",
            downloadMode = parse.downloadMode,
            status = TaskStatus.QUEUED
        )
        repo.add(task)
        started = true
        viewModelScope.launch {
            engine.startDownload(task, selectedQuality)
        }
    }

    fun resetStarted() {
        started = false
    }

    /** 更新下载引擎（yt-dlp）到最新版 */
    fun updateEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            updateMsg = "正在更新引擎…"
            updateMsg = EngineManager.update(appCtx)
        }
    }

    /** 判断错误是否疑似引擎版本过旧（网站接口变更）；B站官方通道错误不引导更新引擎 */
    fun isEngineStaleError(msg: String): Boolean =
        !msg.contains("B站") && (
                msg.contains("HTTP Error") || msg.contains("Unable to download") ||
                        msg.contains("412") || msg.contains("403") ||
                        msg.contains("JSON metadata")
                )

    private fun genId(): String =
        "dl${System.currentTimeMillis()}${Random.nextInt(100, 999)}"
}
