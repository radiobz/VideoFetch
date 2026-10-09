package com.radiobz.videofetch.ui.browser

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radiobz.videofetch.sniff.SniffedResource

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(viewModel: BrowserViewModel = viewModel()) {
    val resources by viewModel::resources
    val address by viewModel::address
    val pageTitle by viewModel::pageTitle

    val context = LocalContext.current
    val webView = remember { WebView(context) }

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            // 地址栏
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = address,
                    onValueChange = viewModel::onAddressChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入网址，如 example.com/video") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = viewModel::navigate) {
                    Icon(Icons.Filled.Navigation, contentDescription = "前往")
                }
            }

            // 浏览器工具条
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { webView.goBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退")
                }
                IconButton(onClick = { webView.goForward() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进")
                }
                IconButton(onClick = { webView.reload() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
                Text(
                    pageTitle.ifBlank { "网页浏览" },
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

            // WebView
            AndroidView(
                factory = { ctx ->
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
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                update = { wv ->
                    viewModel.currentUrl.let { url ->
                        if (url.isNotBlank() && wv.url != url) {
                            wv.loadUrl(url)
                        }
                    }
                }
            )

            HorizontalDivider()

            // 嗅探到的资源列表
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
                        "在浏览器中打开网页并播放视频，m3u8 / mp4 等媒体流会被自动嗅探出来。\n" +
                                "部分网页使用加密或系统播放器加载视频，可能无法识别。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { viewModel.downloadResource(resource) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("下载", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
