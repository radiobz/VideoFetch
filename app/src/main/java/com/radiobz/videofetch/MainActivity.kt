package com.radiobz.videofetch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.radiobz.videofetch.ui.AppNav
import com.radiobz.videofetch.ui.theme.VideoFetchTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** 外部（浏览器分享/打开）传入的链接，HomeScreen 消费后清空 */
        @Volatile
        var pendingLink: String? = null
    }

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        // Android 9 及以下写公共下载目录需要存储权限
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        enableEdgeToEdge()
        setContent {
            VideoFetchTheme {
                AppNav()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** 接收外部 http/https 链接（如浏览器"分享到 VideoFetch"） */
    private fun handleIntent(intent: Intent?) {
        intent?.dataString?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?.let { pendingLink = it }
    }
}
