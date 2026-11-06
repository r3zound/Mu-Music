package com.r3zound.music.core.util

import java.net.URI

/**
 * 直链安全策略（沿用上游 coco-downloader `download-url.ts` 的规则）：
 * - 强制升级为 https
 * - 拒绝带账号密码 / 自定义端口 / 内网主机名的 URL
 *
 * 直链通常由第三方解析站返回，APP 直接下载前必须过这道闸。
 */
object SafeUrl {

    private val blockedSuffixes = listOf(".localhost", ".local", ".internal", ".lan")

    fun isSafe(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        if (!uri.userInfo.isNullOrBlank()) return false
        if (uri.port != -1) return false
        val host = uri.host?.lowercase() ?: return false
        if (!host.contains('.')) return false
        if (host.matches(Regex("^[\\d.]+$"))) return false
        if (host.contains(':')) return false
        return blockedSuffixes.none { host.endsWith(it) }
    }

    /** 返回安全的 https 直链，不安全则返回 null。 */
    fun normalize(raw: String?): String? {
        if (!isSafe(raw)) return null
        val uri = URI(raw!!.trim())
        return uri.toString().replaceFirst(Regex("^http://"), "https://")
    }

    /**
     * 只做协议升级，不做安全校验。用于封面图等静态资源。
     *
     * 服务端返回的封面地址是 `http://p3.music.126.net/...`，而本 APP 的
     * `android:usesCleartextTraffic="false"` 会拦掉明文请求，Coil 拿不到图。
     * 网易云的图床同时支持 https（实测 200），因此统一升协议即可。
     */
    fun upgradeToHttps(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://")) {
            "https://" + trimmed.substring("http://".length)
        } else {
            trimmed
        }
    }
}
