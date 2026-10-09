package com.radiobz.videofetch.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radiobz.videofetch.VideoFetchApp
import com.radiobz.videofetch.data.DownloadTask
import com.radiobz.videofetch.data.ParseResult
import com.radiobz.videofetch.data.Quality
import com.radiobz.videofetch.data.TaskStatus
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
            } catch (e: Exception) {
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

    private fun genId(): String =
        "dl${System.currentTimeMillis()}${Random.nextInt(100, 999)}"
}
