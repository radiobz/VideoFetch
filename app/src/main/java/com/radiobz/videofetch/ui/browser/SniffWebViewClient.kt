package com.radiobz.videofetch.ui.browser

import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.radiobz.videofetch.sniff.MediaSniffer
import com.radiobz.videofetch.sniff.SniffedResource

/**
 * 网页嗅探客户端：拦截所有子资源请求，识别媒体流 URL（m3u8/mp4/ts 等），
 * 记录后放行，不干扰网页正常播放。
 *
 * 兼容性加固：
 * - 所有回调均 try-catch，防止 WebView 后台线程异常导致崩溃
 * - onRenderProcessGone（API 26+）：渲染进程崩溃时接管处理，避免整进程被杀
 *   （华为/定制系统 WebView 渲染进程崩溃是"屡次停止运行"的高发原因）
 */
class SniffWebViewClient(
    private val onResourceFound: (SniffedResource) -> Unit,
    private val onPageTitle: (String) -> Unit
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        runCatching {
            request?.url?.toString()?.let { url ->
                sniff(url, view?.url)
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        runCatching {
            url?.let { sniff(it, view?.url) }
        }
        return null
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        runCatching {
            super.onPageFinished(view, url)
            onPageTitle(view?.title ?: "")
        }
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        // 渲染进程崩溃（华为等定制 WebView 常见）：接管处理，阻止整个应用被杀
        return true
    }

    private fun sniff(url: String, pageUrl: String?) {
        val type = MediaSniffer.detect(url) ?: return
        onResourceFound(
            SniffedResource(
                url = url,
                typeLabel = type.label,
                pageUrl = pageUrl ?: "",
                fileName = MediaSniffer.fileName(url)
            )
        )
    }
}
