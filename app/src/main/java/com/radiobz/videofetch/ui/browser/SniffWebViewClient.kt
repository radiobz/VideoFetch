package com.radiobz.videofetch.ui.browser

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.radiobz.videofetch.sniff.MediaSniffer
import com.radiobz.videofetch.sniff.SniffedResource

/**
 * 网页嗅探客户端：拦截所有子资源请求，识别媒体流 URL（m3u8/mp4/ts 等），
 * 记录后放行，不干扰网页正常播放。
 */
class SniffWebViewClient(
    private val onResourceFound: (SniffedResource) -> Unit,
    private val onPageTitle: (String) -> Unit
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        request?.url?.toString()?.let { url ->
            sniff(url, view?.url)
        }
        return null
    }

    @Suppress("DEPRECATION")
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        url?.let { sniff(it, view?.url) }
        return null
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageTitle(view?.title ?: "")
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
