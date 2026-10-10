package com.radiobz.videofetch.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radiobz.videofetch.data.DownloadTask
import com.radiobz.videofetch.data.TaskStatus
import java.util.Locale

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = viewModel()) {
    val tasks by viewModel.repo.tasks.collectAsStateWithLifecycle()
    val filterRunningOnly by viewModel::filterRunningOnly

    val visible = if (filterRunningOnly) {
        tasks.filter { it.status == TaskStatus.RUNNING || it.status == TaskStatus.QUEUED }
    } else tasks

    Scaffold { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("下载任务", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = filterRunningOnly,
                    onClick = viewModel::toggleFilter,
                    label = { Text("只看进行中") }
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = viewModel::clearFinished) {
                    Icon(Icons.Filled.ClearAll, contentDescription = "清理记录")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "保存目录：${viewModel.saveLocation()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (visible.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("暂无任务", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "去「下载」页解析链接，或到「网页嗅探」页抓取网页视频",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(visible, key = { it.id }) { task ->
                        TaskCard(task, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: DownloadTask, viewModel: DownloadsViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${if (task.source == "sniff") "网页嗅探" else "链接解析"} · ${task.qualityLabel}" +
                                if (task.status == TaskStatus.RUNNING && task.etaSeconds >= 0) " · 剩余 ${formatEta(task.etaSeconds)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusIcon(task.status)
            }

            if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.QUEUED) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (task.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append(String.format(Locale.US, "%.1f%%", task.progress))
                        if (task.speedLine.isNotBlank()) append("  ·  ${task.speedLine}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (task.status == TaskStatus.FAILED && !task.error.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    task.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End) {
                when (task.status) {
                    TaskStatus.RUNNING, TaskStatus.QUEUED -> {
                        ActionIcon(
                            icon = { Icon(Icons.Filled.Stop, contentDescription = "取消", Modifier.size(20.dp)) },
                            onClick = { viewModel.cancel(task.id) }
                        )
                    }
                    TaskStatus.FAILED -> {
                        ActionIcon(
                            icon = { Icon(Icons.Filled.Refresh, contentDescription = "重试", Modifier.size(20.dp)) },
                            onClick = { viewModel.retry(task) }
                        )
                    }
                    TaskStatus.COMPLETED -> {
                        ActionIcon(
                            icon = { Icon(Icons.Filled.PlayArrow, contentDescription = "打开", Modifier.size(20.dp)) },
                            onClick = { viewModel.openFile(task) }
                        )
                    }
                    else -> { }
                }
                ActionIcon(
                    icon = { Icon(Icons.Filled.Delete, contentDescription = "删除", Modifier.size(20.dp)) },
                    onClick = { viewModel.remove(task.id) }
                )
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: @Composable () -> Unit, onClick: () -> Unit) {
    IconButton(onClick = onClick) { icon() }
}

@Composable
private fun StatusIcon(status: TaskStatus) {
    val (icon, tint) = when (status) {
        TaskStatus.RUNNING -> Triple(Icons.Filled.PlayArrow, MaterialTheme.colorScheme.primary, "下载中")
        TaskStatus.QUEUED -> Triple(Icons.Filled.PlayArrow, MaterialTheme.colorScheme.onSurfaceVariant, "排队中")
        TaskStatus.COMPLETED -> Triple(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary, "已完成")
        TaskStatus.FAILED -> Triple(Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.error, "失败")
        TaskStatus.CANCELED -> Triple(Icons.Filled.Stop, MaterialTheme.colorScheme.onSurfaceVariant, "已取消")
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
}

private fun formatEta(seconds: Long): String {
    if (seconds < 0) return ""
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m${s}s" else "${s}s"
}
