package com.radiobz.videofetch.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.radiobz.videofetch.data.ParseResult
import com.radiobz.videofetch.data.Quality
import java.util.Locale

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val state by viewModel::state
    val input by viewModel::input

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("视频下载", style = MaterialTheme.typography.headlineSmall)
            Text("粘贴抖音 / 快手 / B站 / YouTube 等链接，一键解析下载", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            // 输入区
            OutlinedTextField(
                value = input,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("粘贴视频链接…") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.primaryClip?.getItemAt(0)?.text?.toString()?.let {
                            if (it.isNotBlank()) viewModel.onInputChange(it)
                        }
                    }) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "粘贴")
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.parse()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = input.isNotBlank() && state != HomeState.Parsing
            ) {
                if (state == HomeState.Parsing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("解析中…")
                } else {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("解析链接")
                }
            }

            Spacer(Modifier.height(16.dp))

            when (val s = state) {
                is HomeState.Idle -> HintCard()
                is HomeState.Parsing -> { }
                is HomeState.Error -> ErrorCard(s.msg, viewModel)
                is HomeState.Result -> ResultCard(s.parse, viewModel)
            }
        }
    }
}

@Composable
private fun HintCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("使用说明", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "1. 在抖音 / 快手 / B站 / 小红书 / YouTube 等 App 中复制视频分享链接\n" +
                        "2. 粘贴到上方输入框，点「解析链接」\n" +
                        "3. 选择清晰度，开始下载\n\n" +
                        "需要登录才能观看的内容可能无法下载；网页内嵌视频请用「网页嗅探」页",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ErrorCard(msg: String, viewModel: HomeViewModel) {
    val updateMsg by viewModel::updateMsg
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            }
            if (viewModel.isEngineStaleError(msg)) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "这通常是下载引擎版本过旧、网站接口已变更导致的，更新引擎后重试通常可以解决：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = viewModel::updateEngine,
                    enabled = updateMsg != "正在更新引擎…"
                ) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (updateMsg.isBlank()) "更新引擎后重试" else updateMsg)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(parse: ParseResult, viewModel: HomeViewModel) {
    val selected by viewModel::selectedQuality
    val started by viewModel::started

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row {
                AsyncImage(
                    model = parse.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        parse.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    parse.uploader?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (parse.duration > 0) {
                        Text(
                            formatDuration(parse.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("选择清晰度", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Quality.entries.filter { it != Quality.SNIFF }.forEach { q ->
                    FilterChip(
                        selected = selected == q,
                        onClick = { viewModel.selectQuality(q) },
                        label = { Text(q.label) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { viewModel.startDownload(parse) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !started
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (started) "已加入任务列表" else "开始下载")
            }
            if (started) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "任务已创建，请到「任务」页查看进度",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
