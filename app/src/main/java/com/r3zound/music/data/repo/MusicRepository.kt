package com.r3zound.music.data.repo

import com.r3zound.music.core.model.ChannelHealth
import com.r3zound.music.core.model.ChannelInfo
import com.r3zound.music.core.model.ChannelResult
import com.r3zound.music.core.model.LyricData
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.core.model.PlayInfo
import com.r3zound.music.core.util.SafeUrl
import com.r3zound.music.data.prefs.SettingsStore
import com.r3zound.music.data.remote.MusicApi
import com.r3zound.music.data.remote.ProviderRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音乐数据源。
 *
 * 多渠道并发搜索：每个渠道独立返回，快的先渲染，慢的后补；
 * 单个渠道失败不影响其它渠道。服务端对未知渠道会静默回落 netease，
 * 这里通过比对返回项的 provider 字段识别回落。
 */
@Singleton
class MusicRepository @Inject constructor(
    private val api: MusicApi,
    private val settings: SettingsStore,
) {

    private val healthLock = Mutex()
    private val healthCache = mutableMapOf<String, ChannelInfo>()

    suspend fun baseUrl(): String = settings.baseUrl.first()

    suspend fun search(query: String, providers: List<String>): List<ChannelResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val base = baseUrl()
        return coroutineScope {
            providers.map { provider ->
                async {
                    val start = System.currentTimeMillis()
                    try {
                        val items = api.search(base, trimmed, provider)
                        ChannelResult(provider, items, elapsedMs = System.currentTimeMillis() - start)
                    } catch (e: CancellationException) {
                        // 取消不是渠道故障，放行让协程正常结束
                        throw e
                    } catch (e: Exception) {
                        ChannelResult(
                            provider = provider,
                            items = emptyList(),
                            error = e.message?.takeIf { it.isNotBlank() } ?: "请求失败",
                            elapsedMs = System.currentTimeMillis() - start,
                        )
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun playInfo(item: MusicItem): PlayInfo =
        api.playInfo(baseUrl(), item.id, item.provider, item.extra)

    suspend fun lyric(item: MusicItem): LyricData =
        api.lyric(baseUrl(), item.id, item.provider, item.extra)

    /** 取得安全的直链；服务端只给了非安全 URL 时回退到代理通道。 */
    suspend fun resolveDirectUrl(item: MusicItem): String? =
        SafeUrl.normalize(runCatching { api.playInfo(baseUrl(), item.id, item.provider, item.extra).url }.getOrNull())

    /**
     * 渠道体检：并发探测每个渠道的「搜索 + 取流」，
     * 输出健康报告并缓存，供 UI 的渠道健康指示点使用。
     */
    /**
     * 渠道体检。
     *
     * 体检结果会**自动决定参与搜索的渠道**：能搜且能取流的渠道自动勾上，
     * 其余的自动排除，写进 [SettingsStore.enabledProviders]。
     * 用户不需要在搜索页手动挑渠道。
     */
    suspend fun checkChannels(providers: List<String> = ProviderRegistry.all.map { it.id }): List<ChannelInfo> {
        val base = baseUrl()
        val results = coroutineScope {
            providers.map { provider ->
                async {
                    val start = System.currentTimeMillis()
                    var health = ChannelHealth.DOWN
                    var detail: String? = null
                    try {
                        val items = api.search(base, "晴天", provider, limit = 1)
                        if (items.isEmpty()) {
                            health = ChannelHealth.DOWN
                            detail = "搜索无结果"
                        } else {
                            val item = items.first()
                            try {
                                val info = api.playInfo(base, item.id, provider, item.extra)
                                val url = SafeUrl.normalize(info.url)
                                if (url != null) {
                                    health = ChannelHealth.OK
                                    detail = "${info.type.uppercase()} ${info.bitrate ?: ""}".trim()
                                } else {
                                    health = ChannelHealth.PARTIAL
                                    detail = "直链不安全"
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                health = ChannelHealth.PARTIAL
                                detail = e.message?.take(40) ?: "取流失败"
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        health = ChannelHealth.DOWN
                        detail = e.message?.take(40) ?: "搜索失败"
                    }
                    ChannelInfo(
                        id = provider,
                        name = ProviderRegistry.name(provider),
                        health = health,
                        lastChecked = System.currentTimeMillis(),
                        detail = detail,
                    ).also { elapsed ->
                        healthLock.withLock { healthCache[provider] = elapsed }
                    }
                }
            }.awaitAll()
        }
        // 体检结果直接决定搜索时勾选哪些渠道，用户不用手动挑
        applyHealthResult(results)
        return results
    }

    /**
     * 把体检结果落库：勾选可用渠道 + 记录体检时间。
     *
     * 由 [checkChannels] 在拿到结果后调用。
     */
    suspend fun applyHealthResult(infos: List<ChannelInfo>) {
        val usable = infos.filter { it.health == ChannelHealth.OK }.map { it.id }.toSet()
        settings.setEnabledProviders(usable)
        settings.setLastHealthCheck(System.currentTimeMillis())
    }

    suspend fun cachedHealth(): List<ChannelInfo> = healthLock.withLock {
        ProviderRegistry.all.map { info ->
            healthCache[info.id]?.copy(name = info.name) ?: info
        }
    }
}
