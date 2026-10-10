package com.radiobz.videofetch.sniff

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 嗅探事件的全局状态（跨页面共享）：
 * - 导航栏「网页嗅探」角标：本次会话新嗅探到的资源数
 */
object SniffEvents {
    var badgeCount by mutableIntStateOf(0)
        private set

    /** 新嗅探到资源时 +1（由 BrowserViewModel 调用） */
    fun onNewResource() {
        badgeCount++
    }

    /** 用户进入嗅探页/查看列表后清零 */
    fun resetBadge() {
        badgeCount = 0
    }
}
