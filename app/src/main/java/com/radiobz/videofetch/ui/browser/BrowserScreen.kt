package com.radiobz.videofetch.ui.browser

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radiobz.videofetch.sniff.MediaSniffer
import com.radiobz.videofetch.sniff.SniffEvents
import com.radiobz.videofetch.sniff.SniffedResource

/** 常用网站快捷入口 */
private val quickSites = listOf(
    "B站" to "https://www.bilibili.com",
    "抖音" to "https://www.douyin.com",
    "YouTube" to "https://www.youtube.com",
    "快手" to "https://www.kuaishou.com"
)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowserScreen(viewModel: BrowserViewModel = viewModel()) {
    val resources by viewModel::resources
    val address by viewModel::address
    val pageTitle by viewModel::pageTitle
    val hasPage = viewModel.currentUrl.isNotBlank()

    // 持有当前 WebView 引用（工具条操作），不提前创建实例
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    // WebView 创建失败标记（华为等定制系统 WebView 可能异常）
    var webViewError by remember { mutableStateOf(false) }

    // 手动添加链接对话框
    var showManualDialog by remember { mutableStateOf(false) }
    var manualLink by remember { mutableStateOf("") }

    // 进入嗅探页时清零导航角标（用户已看到新资源提示）
    LaunchedEffect(Unit) {
        SniffEvents.resetBadge()
    }

    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("手动添加视频链接") },
            text = {
                Column {
                    Text(
                        "粘贴网页中实际的视频直链（m3u8 / mp4 等）。\n可从网页开发者工具的 Network 面板获取。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualLink,
                        onValueChange = { manualLink = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://…/index.m3u8 或 …/video.mp4") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addManualLink(manualLink)
                    manualLink = ""
                    showManualDialog = false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) { Text("取消") }
            }
        )
    }

    // 离开嗅探页时统一释放 WebView，防止实例泄漏叠加（华为 WebView 对多实例/泄漏敏感）
    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.let { wv ->
                runCatching {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.removeAllViews()
                    wv.destroy()
                }
            }
            webViewRef.value = null
        }
    }

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            // ===== 地址栏（醒目入口） =====
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    "网页地址",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = viewModel::onAddressChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入网址，如 www.bilibili.com/video/…") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = viewModel::navigate,
                        enabled = address.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Navigation, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("打开")
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickSites.forEach { (name, url) ->
                        SuggestionChip(
                            onClick = { viewModel.openUrl(url) },
                            label = { Text(name) }
                        )
                    }
                }
            }

            // ===== 浏览器工具条 =====
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { webViewRef.value?.goBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退")
                }
                IconButton(onClick = { webViewRef.value?.goForward() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进")
                }
                IconButton(onClick = { webViewRef.value?.reload() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
                Text(
                    pageTitle.ifBlank { if (hasPage) "加载中…" else "尚未打开网页" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::clearResources) {
                    Icon(Icons.Filled.Clear, contentDescription = "清空资源")
                }
            }

            HorizontalDivider()

            // ===== 网页区：空态引导 / WebView（按需创建，异常兜底） =====
            if (hasPage) {
                if (webViewError) {
                    WebViewErrorBox(onRetry = {
                        webViewError = false
                        viewModel.openUrl(viewModel.currentUrl)
                    })
                } else {
                    AndroidView(
                        factory = { ctx ->
                            runCatching {
                                WebView(ctx).apply {
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        mediaPlaybackRequiresUserGesture = false
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                    }
                                    webViewClient = SniffWebViewClient(
                                        onResourceFound = viewModel::onResourceFound,
                                        onPageTitle = viewModel::onPageTitle
                                    )
                                }.also { webViewRef.value = it }
                            }.getOrElse {
                                webViewError = true
                                FrameLayout(ctx) // 占位，避免 AndroidView 返回 null
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        update = { wv ->
                            val real = wv as? WebView ?: return@AndroidView
                            viewModel.currentUrl.let { url ->
                                if (url.isNotBlank() && real.url != url) {
                                    runCatching { real.loadUrl(url) }
                                }
                            }
                        }
                    )
                }
            } else {
                EmptyGuide(onManualAdd = { showManualDialog = true })
            }

            HorizontalDivider()

            // ===== 嗅探到的资源列表 =====
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "检测到的媒体资源（${resources.size}）",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "播放网页视频后自动出现",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (resources.isEmpty()) {
                    Text(
                        "打开网页并播放视频后，m3u8 / mp4 等媒体流会自动出现在这里。\n" +
                                "部分网页使用加密或系统播放器加载视频，可能无法识别。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showManualDialog = true }) {
                        Text("没嗅探到？手动添加视频链接")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(resources) { r ->
                            ResourceItem(r, viewModel)
                        }
                    }
                }
            }
        }
    }
}

/** WebView 创建失败时的兜底提示 */
@Composable
private fun ColumnScope.WebViewErrorBox(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Filled.TravelExplore,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text("浏览器组件不可用", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "当前设备的系统浏览器组件异常，无法打开网页。\n" +
                        "请检查系统 WebView 是否被停用或更新，然后重试。",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

/** 未打开网页时的引导界面 */
@Composable
private fun ColumnScope.EmptyGuide(onManualAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Filled.TravelExplore,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text("打开网页，嗅探视频", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "在上方输入网址，点「打开」进入网页\n播放视频后，m3u8 / mp4 等媒体流\n会自动出现在下方列表中",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onManualAdd) { Text("手动添加视频链接") }
        }
    }
}

@Composable
private fun ResourceItem(resource: SniffedResource, viewModel: BrowserViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    resource.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    resource.typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (resource.typeLabel == MediaSniffer.MediaType.SUSPECT.label)
                        MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { viewModel.downloadResource(resource) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("下载", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
