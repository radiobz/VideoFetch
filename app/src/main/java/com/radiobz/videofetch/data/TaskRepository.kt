package com.radiobz.videofetch.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 下载任务仓库：内存状态 + JSON 文件持久化（写盘节流 + 原子写入） */
class TaskRepository(private val context: Context) {

    private val file = java.io.File(context.filesDir, "tasks.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    // 写盘节流：下载进度高频更新时，最多每 400ms 落盘一次；任务结束显式 flush
    private val SAVE_MIN_INTERVAL_MS = 400L
    private var lastSaveAt = 0L
    private var pending = false

    init {
        load()
        // 应用重启后，把上次遗留的运行中/排队任务标记为中断
        val stale = _tasks.value.filter { it.status == TaskStatus.RUNNING || it.status == TaskStatus.QUEUED }
        if (stale.isNotEmpty()) {
            stale.forEach { t ->
                update(t.id) { it.copy(status = TaskStatus.FAILED, error = "应用退出，任务中断", speedLine = "") }
            }
            flush()
        }
    }

    fun add(task: DownloadTask) {
        _tasks.value = listOf(task) + _tasks.value
        flush()
    }

    fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
        save()
    }

    fun remove(id: String) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
        flush()
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filter { it.status == TaskStatus.RUNNING || it.status == TaskStatus.QUEUED }
        flush()
    }

    fun get(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    /** 强制落盘（任务结束/关键状态变更后调用） */
    @Synchronized
    fun flush() {
        pending = false
        persist()
    }

    private fun load() {
        try {
            if (file.exists()) {
                _tasks.value = json.decodeFromString<List<DownloadTask>>(file.readText())
            }
        } catch (_: Exception) {
            file.delete()
        }
    }

    @Synchronized
    private fun save() {
        val now = System.currentTimeMillis()
        if (now - lastSaveAt < SAVE_MIN_INTERVAL_MS) {
            pending = true
            return
        }
        lastSaveAt = now
        pending = false
        persist()
    }

    /** 原子写：先写临时文件再改名，避免写入一半被杀导致 JSON 损坏 */
    private fun persist() {
        try {
            val tmp = java.io.File(context.filesDir, "tasks.json.tmp")
            tmp.writeText(json.encodeToString<List<DownloadTask>>(_tasks.value))
            if (tmp.exists()) tmp.renameTo(file)
        } catch (_: Exception) { }
    }
}
