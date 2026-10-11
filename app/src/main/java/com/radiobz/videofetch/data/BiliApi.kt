package com.radiobz.videofetch.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * B站官方 API 直连（yt-dlp 被风控/失败时的兜底通道）：
 * - view 接口：标题、时长、cid（B站 2025+ 要求 wbi 签名）
 * - playurl 接口：直接可下载的 mp4 流地址（音视频一体）
 * B站对无 wbi 签名的接口请求返回 412；对无 cookie 的 CDN 下载请求返回 403。
 * 沙箱数据中心 IP 会被 B站全链路拒绝，真机（运营商 IP）正常。
 */
object BiliApi {

    const val MODE = "bili_api"

    private val UA = DownloadEngine.MOBILE_UA
    private const val REFERER = "https://www.bilibili.com/"

    private var cachedCookies: String? = null

    /** wbi 签名缓存（key + 时间戳，30s 有效期） */
    private var wbiKey: String? = null
    private var wbiTs = 0L
    private const val WBI_TTL = 25_000L

    /** getMixinKey() 重排表（来自 B站前端 JS） */
    private val MIXIN_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52
    )

    /**
     * B站 CDN（upos）对无 cookie（buvid）的下载请求会返回 403。
     * 真机网络下先访问 bilibili.com 主页即可种下 buvid3；沙箱数据中心 IP 会被全链路拒绝。
     */
    fun getCookies(): String {
        cachedCookies?.let { return it }
        val cookies = runCatching {
            val conn = (URL("https://www.bilibili.com/").openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            }
            try {
                conn.connect()
                // 跟随重定向后读取 Set-Cookie（buvid3 等会话标识）
                val sb = StringBuilder()
                var i = 1
                while (true) {
                    val key = conn.getHeaderFieldKey(i) ?: break
                    if (key.equals("Set-Cookie", true)) {
                        val value = conn.getHeaderField(i)
                        val name = value.substringBefore("=")
                        if (name == "buvid3" || name == "buvid4" || name == "CURRENT_FNVAL" || name == "b_nut") {
                            sb.append(name).append("=").append(value.substringAfter("=").substringBefore(";")).append("; ")
                        }
                    }
                    i++
                }
                conn.inputStream.use { }
                sb.toString()
            } finally {
                conn.disconnect()
            }
        }.getOrDefault("")
        cachedCookies = cookies
        return cookies
    }

    /** 获取 wbi mixin key（nav 接口拿 img_url/sub_url，30s 缓存） */
    private fun getWbiKey(): String {
        val now = System.currentTimeMillis()
        if (wbiKey != null && now - wbiTs < WBI_TTL) return wbiKey!!
        val nav = getJson("https://api.bilibili.com/x/web-interface/nav", signed = false)
        val wbi = nav.optJSONObject("data")?.optJSONObject("wbi_img")
            ?: throw IllegalStateException("B站接口未返回签名信息")
        val img = wbi.optString("img_url", "")
        val sub = wbi.optString("sub_url", "")
        val lookup = img.substringAfterLast("/").substringBefore(".") +
                sub.substringAfterLast("/").substringBefore(".")
        if (lookup.length < 64) throw IllegalStateException("B站签名信息异常")
        val key = MIXIN_TAB.joinToString("") { lookup[it].toString() }.take(32)
        wbiKey = key
        wbiTs = now
        return key
    }

    /**
     * 对参数做 wbi 签名：加 wts → 排序 → 过滤特殊字符 → urlencode → md5(query+key) → w_rid。
     * 算法与 B站前端/yt-dlp 一致。
     */
    private fun signWbi(params: MutableMap<String, String>): String {
        params["wts"] = (System.currentTimeMillis() / 1000).toString()
        val cleaned = params.toSortedMap().mapValues { (_, v) ->
            v.filter { it !in "!'()*" }
        }
        val query = cleaned.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        val wrid = md5(query + getWbiKey())
        return "$query&w_rid=$wrid"
    }

    private fun md5(s: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

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
        val view = getJson(
            "https://api.bilibili.com/x/web-interface/view",
            params = linkedMapOf("bvid" to bvid)
        )
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

    /**
     * 探测实际可用清晰度：B站无登录时 1080P 请求会被降级为 720P、480P 请求降级为 360P，
     * 因此以接口实际返回的 quality 为准标标签，避免显示虚假清晰度。
     */
    private fun probeFormats(bvid: String, cid: Long): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        val seen = mutableSetOf<Int>()
        for (qn in listOf(80, 64, 32, 16)) {
            val play = getJson(
                "https://api.bilibili.com/x/player/wbi/playurl",
                params = linkedMapOf("bvid" to bvid, "cid" to cid.toString(), "qn" to qn.toString(), "fnval" to "0")
            )
            val d = play.optJSONObject("data")
            val quality = d?.optInt("quality", 0)
            val durl = d?.optJSONArray("durl")
            if (d != null && quality != null && quality > 0 && durl != null && durl.length() > 0 && seen.add(quality)) {
                result.add(quality to qualityLabel(quality))
            }
        }
        result.sortByDescending { it.first }
        return result.ifEmpty { listOf(64 to "720P") }
    }

    private fun qualityLabel(qn: Int): String = when {
        qn >= 80 -> "1080P"
        qn >= 64 -> "720P"
        qn >= 32 -> "480P"
        qn >= 16 -> "360P"
        else -> "${qn}P"
    }

    /** 获取指定清晰度的 mp4 直链（音视频一体） */
    fun getDirectUrl(bvid: String, cid: Long, qn: Int): String {
        val play = getJson(
            "https://api.bilibili.com/x/player/wbi/playurl",
            params = linkedMapOf("bvid" to bvid, "cid" to cid.toString(), "qn" to qn.toString(), "fnval" to "0")
        )
        val d = play.optJSONObject("data")
        val durl = d?.optJSONArray("durl")
        if (durl == null || durl.length() == 0) {
            throw IllegalStateException("B站未返回可下载的${qn}P视频流")
        }
        return durl.getJSONObject(0).getString("url")
    }

    /** 请求 B站 JSON 接口；默认带 wbi 签名（B站 2025+ 要求） */
    private fun getJson(
        url: String,
        params: Map<String, String> = emptyMap(),
        signed: Boolean = true
    ): JSONObject {
        val cookies = getCookies()
        val finalUrl = if (params.isEmpty()) {
            url
        } else if (signed) {
            val p = LinkedHashMap(params)
            url + "?" + signWbi(p)
        } else {
            url + "?" + params.entries.joinToString("&") { (k, v) -> "$k=$v" }
        }
        val conn = (URL(finalUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Referer", REFERER)
            setRequestProperty("Origin", "https://www.bilibili.com")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            if (cookies.isNotBlank()) setRequestProperty("Cookie", cookies)
        }
        try {
            if (conn.responseCode != 200) {
                throw IllegalStateException("B站接口 HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            // 接口业务码非 0 也视为失败（如 -404/-412/风控）
            if (json.optInt("code", 0) != 0) {
                throw IllegalStateException(
                    "B站接口 ${json.optString("message").ifBlank { "错误 ${json.optInt("code")}" }}"
                )
            }
            return json
        } finally {
            conn.disconnect()
        }
    }
}
