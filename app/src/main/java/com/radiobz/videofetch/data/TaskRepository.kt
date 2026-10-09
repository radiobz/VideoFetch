package com.radiobz.videofetch.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 下载任务仓库：内存状态 + JSON 文件持久化 */
class TaskRepository(private val context: Context) {

    private val file = java.io.File(context.filesDir, "tasks.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    init {
        load()
        // 应用重启后，把上次遗留的运行中/排队任务标记为中断
        val stale = _tasks.value.filter { it.status == TaskStatus.RUNNING || it.status == TaskStatus.QUEUED }
        if (stale.isNotEmpty()) {
            stale.forEach { t ->
                update(t.id) { it.copy(status = TaskStatus.FAILED, error = "应用退出，任务中断", speedLine = "") }
            }
        }
    }

    fun add(task: DownloadTask) {
        _tasks.value = listOf(task) + _tasks.value
        save()
    }

    fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
        save()
    }

    fun remove(id: String) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
        save()
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filter { it.status == TaskStatus.RUNNING || it.status == TaskStatus.QUEUED }
        save()
    }

    fun get(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    private fun load() {
        try {
            if (file.exists()) {
                _tasks.value = json.decodeFromString<List<DownloadTask>>(file.readText())
            }
        } catch (_: Exception) {
            file.delete()
        }
    }

    private fun save() {
        try {
            file.writeText(json.encodeToString<List<DownloadTask>>(_tasks.value))
        } catch (_: Exception) { }
    }
}
