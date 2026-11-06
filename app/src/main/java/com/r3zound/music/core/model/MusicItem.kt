package com.r3zound.music.core.model

import kotlinx.serialization.json.JsonObject

/** 音质档位。完全由服务端 `extra.qualityOptions` 驱动，客户端不硬编码。 */
data class QualityOption(
    val value: String,
    val label: String,
    val quality: String,
    val format: String,
)

/**
 * 一条搜索结果。
 *
 * [extra] 必须原样透传回 /api/url、/api/download、/api/lyric，否则音质降级或取流失败。
 * [requestedProvider] 是我们请求的渠道，[provider] 是服务端实际返回的渠道——
 * 线上对未知渠道会静默回落 netease，两者不等即说明发生了回落。
 */
data class MusicItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val cover: String? = null,
    val duration: String? = null,
    val provider: String,
    val requestedProvider: String? = null,
    val extra: JsonObject? = null,
    val qualityOptions: List<QualityOption> = emptyList(),
) {
    /** 服务端把请求的渠道换成了别的渠道（未注册渠道会回落 netease）。 */
    val hasFallenBack: Boolean
        get() = requestedProvider != null && requestedProvider != provider

    /** 该渠道实际可下载（能返回音频直链），否则只能跳源站。 */
    val downloadable: Boolean
        get() = qualityOptions.isNotEmpty()

    /**
     * 歌手字段形如「周杰伦, 李硕, 张鑫」，取第一位作为「点击歌手搜索」的搜索词。
     * 分隔符覆盖服务端可能出现的多种写法。
     */
    val primaryArtist: String
        get() = artist.split(',', '、', '/', '&', '，')
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: artist
}

/** 播放地址。 */
data class PlayInfo(
    val url: String,
    val type: String,
    val bitrate: String? = null,
    val cover: String? = null,
    /** audio = 可直接下载；source-page = 只能去源站（网盘入口）。 */
    val delivery: String = "audio",
)

data class LyricLine(val time: Double, val text: String)

data class LyricData(
    val songId: String,
    val provider: String,
    val lines: List<LyricLine>,
)

/** 渠道健康度，来自设置页「渠道体检」的缓存。 */
enum class ChannelHealth {
    /** 搜索 + 取流均正常 */
    OK,

    /** 能搜到，但取流失败（如 500） */
    PARTIAL,

    /** 搜索无结果或超时 */
    DOWN,

    /** 尚未体检 */
    UNKNOWN;

    val label: String
        get() = when (this) {
            OK -> "可用"
            PARTIAL -> "取流失败"
            DOWN -> "不可用"
            UNKNOWN -> "未检测"
        }
}

data class ChannelInfo(
    val id: String,
    val name: String,
    val health: ChannelHealth = ChannelHealth.UNKNOWN,
    val lastChecked: Long = 0L,
    val detail: String? = null,
)

/** 某次搜索里单个渠道的结果与失败原因。 */
data class ChannelResult(
    val provider: String,
    val items: List<MusicItem>,
    val error: String? = null,
    val elapsedMs: Long = 0,
) {
    val ok: Boolean get() = error == null && items.isNotEmpty()
}

/** 文件格式。服务端 Content-Type 恒为 audio/mpeg，必须靠魔数与 /api/url 的 type 判定。 */
enum class AudioFormat(val extension: String, val mime: String) {
    MP3("mp3", "audio/mpeg"),
    FLAC("flac", "audio/flac"),
    M4A("m4a", "audio/mp4"),
    OGG("ogg", "audio/ogg"),
    AAC("aac", "audio/aac"),
    WAV("wav", "audio/wav"),
    UNKNOWN("mp3", "audio/mpeg");

    companion object {
        /**
         * 按文件头魔数判定真实格式。
         * 服务端 `/api/download` 的 Content-Type 恒为 audio/mpeg（实测 flac 内容也标 mp3），
         * 因此不能信任响应头，必须读 body 前若干字节。
         */
        fun detect(bytes: ByteArray): AudioFormat {
            fun b(i: Int) = bytes[i].toInt() and 0xFF
            return when {
                bytes.size >= 3 && b(0) == 'I'.code && b(1) == 'D'.code && b(2) == '3'.code -> MP3
                bytes.size >= 4 && b(0) == 0x66 && b(1) == 0x4C && b(2) == 0x61 && b(3) == 0x43 -> FLAC
                bytes.size >= 4 && b(0) == 0x4F && b(1) == 0x67 && b(2) == 0x67 && b(3) == 0x53 -> OGG
                bytes.size >= 12 && b(0) == 0x52 && b(1) == 0x49 && b(2) == 0x46 && b(3) == 0x46 -> WAV
                bytes.size >= 8 && b(4) == 0x66 && b(5) == 0x74 && b(6) == 0x79 -> AAC
                else -> UNKNOWN
            }
        }

        /** 从 /api/url 返回的 type 字段推断扩展名，作为魔数判定失败时的兜底。 */
        fun fromType(type: String?): AudioFormat = when (type?.lowercase()) {
            "flac" -> FLAC
            "m4a", "mp4" -> M4A
            "ogg" -> OGG
            "aac" -> AAC
            "wav" -> WAV
            "mp3" -> MP3
            else -> UNKNOWN
        }
    }
}
