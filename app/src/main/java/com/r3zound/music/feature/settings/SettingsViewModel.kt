package com.r3zound.music.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.r3zound.music.core.model.ChannelHealth
import com.r3zound.music.core.model.ChannelInfo
import com.r3zound.music.data.backup.BackupWriter
import com.r3zound.music.data.prefs.SettingsStore
import com.r3zound.music.data.prefs.ThemeMode
import com.r3zound.music.data.remote.ProviderRegistry
import com.r3zound.music.data.repo.FavoriteRepository
import com.r3zound.music.data.repo.ImportResult
import com.r3zound.music.data.repo.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * 音质档位。
 *
 * 服务端通过 `extra.qualityOptions` 下发档位列表；设置页需要在没有搜索结果时也能展示，
 * 因此这里按 netease 的实测档位做一份静态映射。
 */
data class QualityTier(
    val value: String,
    val label: String,
    val subtitle: String? = null,
)

/**
 * 后四个档位（Hi-Res / 高清环绕声 / 沉浸环绕声 / 超清母带）实测与无损返回的是同一份
 * 2000k FLAC。如实标注，避免给用户「越高越好」的错误预期。
 */
private const val SAME_BITRATE_NOTE = "实测位率与无损相同（2000k）"

/** netease 的 7 个音质档位。 */
val NETEASE_QUALITY_TIERS: List<QualityTier> = listOf(
    QualityTier("standard", "标准音质"),
    QualityTier("exhigh", "极高音质"),
    QualityTier("lossless", "无损音质"),
    QualityTier("hires", "Hi-Res", SAME_BITRATE_NOTE),
    QualityTier("jyeffect", "高清环绕声", SAME_BITRATE_NOTE),
    QualityTier("sky", "沉浸环绕声", SAME_BITRATE_NOTE),
    QualityTier("jymaster", "超清母带", SAME_BITRATE_NOTE),
)

/** 主题选项。 */
data class ThemeOption(
    val mode: ThemeMode,
    val label: String,
)

/** 主题候选项。 */
val THEME_OPTIONS: List<ThemeOption> = listOf(
    ThemeOption(ThemeMode.SYSTEM, "跟随系统"),
    ThemeOption(ThemeMode.LIGHT, "浅色"),
    ThemeOption(ThemeMode.DARK, "深色"),
)

/** 单选对话框的一项。 */
data class ChoiceOption(
    val value: String,
    val label: String,
    val subtitle: String? = null,
)

/** 连接测试结果。 */
data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
)

/** 设置页 UI 状态。所有设置项由 DataStore 的 Flow 合成，改动后立即刷新。 */
data class SettingsUiState(
    val baseUrl: String = "",
    val defaultQuality: String = "lossless",
    val wifiOnly: Boolean = false,
    val maxConcurrent: Int = 2,
    val autoRetry: Int = 3,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationEnabled: Boolean = true,
    /** 体检结果里可用（OK）的渠道数。 */
    val availableChannels: Int = 0,
    /** 注册渠道总数。 */
    val totalChannels: Int = ProviderRegistry.all.size,
    /** 最近一次体检时间，0 表示从未体检。 */
    val lastCheckedAt: Long = 0L,
)

/**
 * 设置页 ViewModel。
 *
 * 所有设置项都是 DataStore 的 Flow，这里用 combine 合成一个 [SettingsUiState]；
 * 写操作直接落到 DataStore，UI 会立刻收到新值。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val musicRepository: MusicRepository,
    private val favoriteRepository: FavoriteRepository,
    private val backupWriter: BackupWriter,
) : ViewModel() {

    /** 渠道体检缓存，供「N 可用」徽标使用。 */
    private val healthFlow = MutableStateFlow<List<ChannelInfo>>(emptyList())

    private val stateFlow = combine(
        // 音源与下载
        combine(
            settingsStore.baseUrl,
            settingsStore.defaultQuality,
            settingsStore.wifiOnly,
        ) { url, quality, wifiOnly -> Triple(url, quality, wifiOnly) },
        // 下载与播放
        combine(
            settingsStore.maxConcurrent,
            settingsStore.autoRetry,
            settingsStore.themeMode,
        ) { concurrent, retry, theme -> Triple(concurrent, retry, theme) },
        // 通知与体检
        combine(
            settingsStore.notificationEnabled,
            healthFlow,
        ) { notification, health -> notification to health },
    ) { basic, media, extra ->
        val health = extra.second
        SettingsUiState(
            baseUrl = basic.first,
            defaultQuality = basic.second,
            wifiOnly = basic.third,
            maxConcurrent = media.first,
            autoRetry = media.second,
            themeMode = media.third,
            notificationEnabled = extra.first,
            availableChannels = health.count { it.health == ChannelHealth.OK },
            totalChannels = ProviderRegistry.all.size,
            lastCheckedAt = health.maxOfOrNull { it.lastChecked } ?: 0L,
        )
    }

    val uiState: StateFlow<SettingsUiState> = stateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setBaseUrl(url: String) {
        viewModelScope.launch { settingsStore.setBaseUrl(url) }
    }

    fun setDefaultQuality(value: String) {
        viewModelScope.launch { settingsStore.setDefaultQuality(value) }
    }

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch { settingsStore.setWifiOnly(value) }
    }

    fun setMaxConcurrent(value: Int) {
        viewModelScope.launch { settingsStore.setMaxConcurrent(value) }
    }

    fun setAutoRetry(value: Int) {
        viewModelScope.launch { settingsStore.setAutoRetry(value) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setNotificationEnabled(value: Boolean) {
        viewModelScope.launch { settingsStore.setNotificationEnabled(value) }
    }

    /** 刷新渠道体检缓存（进入设置页时调用，保证徽标是最新的）。 */
    suspend fun refreshChannelHealth() {
        val cached = runCatching { musicRepository.cachedHealth() }.getOrDefault(emptyList())
        healthFlow.value = cached
    }

    // ── 收藏夹备份 ──────────────────────────────────────────────

    /**
     * 导出收藏夹到「下载/MuMusic」，返回给用户看的路径。
     *
     * @throws java.io.IOException 写入失败
     */
    suspend fun exportFavorites(): String {
        val json = favoriteRepository.exportJson()
        val fileName = BackupWriter.buildFileName()
        return backupWriter.writeToDownloads(json, fileName)
    }

    /** 从 JSON 文本导入收藏夹。 */
    suspend fun importFavorites(raw: String): ImportResult = favoriteRepository.importJson(raw)

    /** 从用户选中的文件导入收藏夹。 */
    suspend fun importFavoritesFromUri(uri: Uri): ImportResult {
        val raw = backupWriter.readText(uri) ?: throw IOException("无法读取所选文件")
        return favoriteRepository.importJson(raw)
    }

    /**
     * 测试连接。
     *
     * 检测走的是仓库里的当前 baseUrl，所以这里先把待测地址临时写进去，测完再恢复原值——
     * 是否正式生效由对话框的「确定」决定。
     */
    suspend fun testConnection(url: String): ConnectionTestResult {
        val normalized = url.trim().trimEnd('/')
        if (normalized.isEmpty()) return ConnectionTestResult(false, "请填写服务器地址")
        val original = settingsStore.baseUrl.first()
        return try {
            if (normalized != original) settingsStore.setBaseUrl(normalized)
            val results = musicRepository.checkChannels(listOf(ProviderRegistry.netease))
            val info = results.firstOrNull { it.id == ProviderRegistry.netease } ?: results.firstOrNull()
            when {
                info == null -> ConnectionTestResult(false, "服务无响应")
                info.health == ChannelHealth.OK -> ConnectionTestResult(true, "连接正常")
                else -> ConnectionTestResult(false, info.detail?.takeIf { it.isNotBlank() } ?: "连接失败")
            }
        } catch (e: Exception) {
            ConnectionTestResult(false, e.message?.take(60) ?: "连接失败")
        } finally {
            if (normalized != original) settingsStore.setBaseUrl(original)
        }
    }
}
