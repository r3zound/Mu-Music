package com.r3zound.music.data.remote

import com.r3zound.music.core.util.QualityOptions
import com.r3zound.music.core.model.LyricData
import com.r3zound.music.core.model.LyricLine
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.core.model.PlayInfo
import com.r3zound.music.core.model.QualityOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * m.resound.vip 的 API 客户端。
 *
 * 接口契约（全部实测，无鉴权）：
 * - GET /api/search?q=&provider=&limit=&offset=  -> {"items":[...]}
 * - GET /api/url?id=&provider=&extra=            -> {"url","type","bitrate",...}
 * - GET /api/lyric?id=&provider=&extra=          -> {"songid","provider","lines":[{time,text}]}
 * - GET /api/download?id=&provider=&filename=&extra= -> 二进制音频流
 *
 * 未知渠道会被服务端静默回落到 netease，所以这里始终显式传 provider，
 * 并由 [MusicItem.hasFallenBack] 暴露回落情况。
 */
@Singleton
class MusicApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {

    suspend fun search(
        baseUrl: String,
        query: String,
        provider: String,
        limit: Int = 20,
        offset: Int = 0,
    ): List<MusicItem> {
        val url = requireBase(baseUrl).newBuilder()
            .addPathSegments("api/search")
            .addQueryParameter("q", query)
            .addQueryParameter("provider", provider)
            .addQueryParameter("limit", limit.coerceIn(1, 50).toString())
            .addQueryParameter("offset", offset.coerceAtLeast(0).toString())
            .build()
        val body = getString(url)
        if (body.isBlank()) return emptyList()
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse { return emptyList() }
        val items = root["items"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { parseItem(it, provider) }
    }

    suspend fun playInfo(
        baseUrl: String,
        id: String,
        provider: String,
        extra: JsonObject?,
    ): PlayInfo {
        val builder = requireBase(baseUrl).newBuilder()
            .addPathSegments("api/url")
            .addQueryParameter("id", id)
            .addQueryParameter("provider", provider)
        if (extra != null) builder.addQueryParameter("extra", extra.toString())
        val body = getString(builder.build())
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw IOException("解析播放地址失败")
        val url = root["url"]?.jsonPrimitive?.contentOrNull
            ?: throw IOException(root["error"]?.jsonPrimitive?.contentOrNull ?: "无法获取播放地址")
        return PlayInfo(
            url = url,
            type = root["type"]?.jsonPrimitive?.contentOrNull ?: "mp3",
            bitrate = root["bitrate"]?.jsonPrimitive?.contentOrNull,
            cover = root["cover"]?.jsonPrimitive?.contentOrNull,
            delivery = root["delivery"]?.jsonPrimitive?.contentOrNull ?: "audio",
        )
    }

    suspend fun lyric(
        baseUrl: String,
        id: String,
        provider: String,
        extra: JsonObject?,
    ): LyricData {
        val builder = requireBase(baseUrl).newBuilder()
            .addPathSegments("api/lyric")
            .addQueryParameter("id", id)
            .addQueryParameter("provider", provider)
        if (extra != null) builder.addQueryParameter("extra", extra.toString())
        val body = getString(builder.build())
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw IOException("解析歌词失败")
        val lines = (root["lines"] as? JsonArray).orEmpty().mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val time = obj["time"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val text = obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (text.isEmpty()) null else LyricLine(time, text)
        }
        return LyricData(
            songId = root["songid"]?.jsonPrimitive?.contentOrNull ?: id,
            provider = root["provider"]?.jsonPrimitive?.contentOrNull ?: provider,
            lines = lines,
        )
    }

    /**
     * 下载代理。仅作为**兜底**通道使用：
     * - 不支持 Range（实测恒返回 200），断点续传无效
     * - Content-Type 恒为 audio/mpeg，真实格式必须靠 body 魔数判定
     */
    fun downloadRequest(
        baseUrl: String,
        id: String,
        provider: String,
        filename: String,
        extra: JsonObject?,
    ): Request {
        val builder = requireBase(baseUrl).newBuilder()
            .addPathSegments("api/download")
            .addQueryParameter("id", id)
            .addQueryParameter("provider", provider)
            .addQueryParameter("filename", filename)
        if (extra != null) builder.addQueryParameter("extra", extra.toString())
        return Request.Builder().url(builder.build()).get().build()
    }

    private suspend fun getString(url: HttpUrl): String = suspendCoroutine { cont ->
        client.newCall(Request.Builder().url(url).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val err = runCatching { it.body?.string() }.getOrNull().orEmpty()
                        cont.resumeWithException(IOException("HTTP ${it.code} ${err.take(120)}"))
                        return
                    }
                    cont.resume(it.body?.string().orEmpty())
                }
            }
        })
    }

    private fun parseItem(element: JsonElement, requestedProvider: String): MusicItem? {
        val obj = element as? JsonObject ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val extra = obj["extra"] as? JsonObject
        val qualityOptions = parseQualityOptions(extra)
        return MusicItem(
            id = id,
            title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "未知歌曲",
            artist = obj["artist"]?.jsonPrimitive?.contentOrNull ?: "未知歌手",
            album = obj["album"]?.jsonPrimitive?.contentOrNull,
            cover = obj["cover"]?.jsonPrimitive?.contentOrNull ?: extra?.get("cover")?.jsonPrimitive?.contentOrNull,
            duration = obj["duration"]?.jsonPrimitive?.contentOrNull,
            provider = obj["provider"]?.jsonPrimitive?.contentOrNull ?: requestedProvider,
            requestedProvider = requestedProvider,
            extra = extra,
            qualityOptions = qualityOptions,
        )
    }

    private fun parseQualityOptions(extra: JsonObject?): List<QualityOption> =
        QualityOptions.parse(extra)

    /**
     * 解析音源站根地址。
     *
     * App 不内置任何默认地址（开源项目不能写死别人的服务器），
     * 用户没在设置里填就抛 [NO_BASE_URL]，UI 会把它显示成一句人话。
     */
    private fun requireBase(baseUrl: String): HttpUrl {
        val trimmed = baseUrl.trim()
        if (trimmed.isEmpty()) throw IOException(NO_BASE_URL)
        return runCatching { trimmed.toHttpUrl() }
            .getOrElse { throw IOException("音源站地址不合法：$trimmed") }
    }

    companion object {
        /** 未配置音源站地址时的提示文案。 */
        const val NO_BASE_URL = "未设置音源站地址，请到「设置 → 服务器地址」填写"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}

/** 构造带 extra 的查询串，供日志与调试使用。 */
fun JsonObject.withLevel(level: String): JsonObject = buildJsonObject {
    for ((k, v) in this@withLevel) put(k, v)
    put("selectedLevel", JsonPrimitive(level))

}
