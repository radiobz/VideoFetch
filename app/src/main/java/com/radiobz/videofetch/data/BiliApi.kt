package com.radiobz.videofetch.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * B站官方 API 直连（yt-dlp 被风控/失败时的兜底通道）：
 * - view 接口：标题、时长、cid
 * - playurl 接口：直接可下载的 mp4 流地址（音视频一体）
 * 真机（运营商 IP）上 B站 API 与 CDN 正常放行；数据中心 IP 会被 403 拒绝。
 */
object BiliApi {

    const val MODE = "bili_api"

    private val UA = DownloadEngine.MOBILE_UA
    private const val REFERER = "https://www.bilibili.com/"

    data class BiliInfo(
        val bvid: String,
        val title: String,
        val duration: Int,
        val thumbnail: String?,
        val uploader: String?,
        val cid: Long,
        /** 可用清晰度（qn 值 → 描述），已按清晰度降序 */
        val formats: List<Pair<Int, String>>
    )

    /** 从 URL 中提取 bvid（支持完整链接 / b23.tv 展开后） */
    fun extractBvid(url: String): String? {
        val m = Regex("""(?i)(?:BV[0-9A-Za-z]{10}|[bB][vV][0-9A-Za-z]{10})""").find(url)
        return m?.value
    }

    fun getInfo(url: String): BiliInfo {
        val bvid = extractBvid(url) ?: throw IllegalArgumentException("无法识别 B站视频链接")
        val view = getJson("https://api.bilibili.com/x/web-interface/view?bvid=$bvid")
        val data = view.optJSONObject("data") ?: throw IllegalStateException("B站接口返回异常：${view.optString("message")}")
        val cid = data.optLong("cid", 0)
        if (cid <= 0) throw IllegalStateException("B站接口未返回视频信息")
        return BiliInfo(
            bvid = bvid,
            title = data.optString("title", bvid),
            duration = data.optInt("duration", 0),
            thumbnail = data.optString("pic").ifBlank { null },
            uploader = data.optJSONObject("owner")?.optString("name"),
            cid = cid,
            formats = probeFormats(bvid, cid)
        )
    }

    /** 探测可用清晰度（32=480P / 64=720P / 80=1080P / 16=360P） */
    private fun probeFormats(bvid: String, cid: Long): List<Pair<Int, String>> {
        val order = listOf(
            80 to "1080P", 64 to "720P", 32 to "480P", 16 to "360P"
        )
        val result = mutableListOf<Pair<Int, String>>()
        for ((qn, label) in order) {
            val play = getJson(
                "https://api.bilibili.com/x/player/playurl?bvid=$bvid&cid=$cid&qn=$qn&fnval=0"
            )
            val d = play.optJSONObject("data")
            val durl = d?.optJSONArray("durl")
            if (d != null && durl != null && durl.length() > 0) {
                result.add(qn to label)
            }
        }
        return result.ifEmpty { listOf(64 to "720P") }
    }

    /** 获取指定清晰度的 mp4 直链（音视频一体） */
    fun getDirectUrl(bvid: String, cid: Long, qn: Int): String {
        val play = getJson(
            "https://api.bilibili.com/x/player/playurl?bvid=$bvid&cid=$cid&qn=$qn&fnval=0"
        )
        val d = play.optJSONObject("data")
        val durl = d?.optJSONArray("durl")
        if (durl == null || durl.length() == 0) {
            throw IllegalStateException("B站未返回可下载的${qn}P视频流")
        }
        return durl.getJSONObject(0).getString("url")
    }

    private fun getJson(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Referer", REFERER)
            setRequestProperty("Origin", "https://www.bilibili.com")
        }
        try {
            if (conn.responseCode != 200) {
                throw IllegalStateException("B站接口 HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }
}
