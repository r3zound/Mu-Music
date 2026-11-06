package com.r3zound.music.core.util

import com.r3zound.music.core.model.QualityOption
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 音质档位的解析工具。
 *
 * 档位完全由服务端 `extra.qualityOptions` 驱动，客户端不硬编码。
 * 这个数组同时存在于：
 * - 搜索接口返回的 `extra` 里
 * - 收藏 / 播放历史落库后的 `extraJson` 里
 *
 * 所以解析逻辑抽出来共用，避免各处重复实现。
 */
object QualityOptions {

    /** 从 `extra` 里解析出音质档位列表。 */
    fun parse(extra: JsonObject?): List<QualityOption> {
        val arr = extra?.get("qualityOptions") as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val value = o["value"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            QualityOption(
                value = value,
                label = o["label"]?.jsonPrimitive?.contentOrNull ?: value,
                quality = o["quality"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                format = o["format"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }
}
