package com.r3zound.music.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mu_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 全局设置。 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val AUTO_RETRY = intPreferencesKey("auto_retry")
        val THEME = stringPreferencesKey("theme_mode")
        val NOTIFICATION = booleanPreferencesKey("notification_enabled")
        val LYRIC_FONT_SIZE = intPreferencesKey("lyric_font_size")
        /** 歌词时间偏移（毫秒），按曲目分别存：key = "lyric_offset_<provider>_<id>" */
        val LYRIC_OFFSET_PREFIX = "lyric_offset_"
    }

    /**
     * 音源站地址。
     *
     * **默认为空**：本项目要开源到 GitHub，不能内置任何人的服务器地址，
     * 必须由使用者自己在「设置 → 服务器地址」里填。
     */
    val baseUrl: Flow<String> = context.dataStore.data.map { it[Keys.BASE_URL] ?: "" }

    /** 默认音质档位 value。 */
    val defaultQuality: Flow<String> = context.dataStore.data.map { it[Keys.DEFAULT_QUALITY] ?: "lossless" }

    /** 仅 WiFi 下载。 */
    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.WIFI_ONLY] ?: false }

    /** 并发下载数。 */
    val maxConcurrent: Flow<Int> = context.dataStore.data.map { it[Keys.MAX_CONCURRENT] ?: 2 }

    /** 失败自动重试次数。 */
    val autoRetry: Flow<Int> = context.dataStore.data.map { it[Keys.AUTO_RETRY] ?: 3 }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[Keys.THEME] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    val notificationEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFICATION] ?: true }

    /** 歌词字号（sp），全局生效。 */
    val lyricFontSize: Flow<Int> = context.dataStore.data.map { it[Keys.LYRIC_FONT_SIZE] ?: DEFAULT_LYRIC_FONT_SIZE }

    /** 取某首歌的歌词时间偏移（毫秒）。 */
    fun lyricOffset(provider: String, id: String): Flow<Long> {
        val key = intPreferencesKey(Keys.LYRIC_OFFSET_PREFIX + provider + "_" + id)
        return context.dataStore.data.map { it[key]?.toLong() ?: 0L }
    }

    suspend fun setBaseUrl(url: String) = context.dataStore.edit { it[Keys.BASE_URL] = url.trim().trimEnd('/') }

    suspend fun setDefaultQuality(value: String) = context.dataStore.edit { it[Keys.DEFAULT_QUALITY] = value }

    suspend fun setWifiOnly(value: Boolean) = context.dataStore.edit { it[Keys.WIFI_ONLY] = value }

    suspend fun setMaxConcurrent(value: Int) = context.dataStore.edit { it[Keys.MAX_CONCURRENT] = value }

    suspend fun setAutoRetry(value: Int) = context.dataStore.edit { it[Keys.AUTO_RETRY] = value }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }

    suspend fun setNotificationEnabled(value: Boolean) = context.dataStore.edit { it[Keys.NOTIFICATION] = value }

    /** 设置歌词字号（sp）。 */
    suspend fun setLyricFontSize(size: Int) = context.dataStore.edit {
        it[Keys.LYRIC_FONT_SIZE] = size.coerceIn(MIN_LYRIC_FONT_SIZE, MAX_LYRIC_FONT_SIZE)
    }

    /** 设置某首歌的歌词时间偏移（毫秒）。 */
    suspend fun setLyricOffset(provider: String, id: String, offsetMs: Long) = context.dataStore.edit {
        it[intPreferencesKey(Keys.LYRIC_OFFSET_PREFIX + provider + "_" + id)] = offsetMs.coerceIn(-MAX_LYRIC_OFFSET, MAX_LYRIC_OFFSET).toInt()
    }

    companion object {
        /** 是否已配置过音源站地址。 */
        fun isConfigured(baseUrl: String?): Boolean = !baseUrl.isNullOrBlank()

        /** 歌词默认字号（sp）。 */
        const val DEFAULT_LYRIC_FONT_SIZE = 16

        /** 歌词字号可调范围（sp）。 */
        const val MIN_LYRIC_FONT_SIZE = 12
        const val MAX_LYRIC_FONT_SIZE = 24

        /** 歌词时间偏移可调范围（毫秒），±5 秒。 */
        const val MAX_LYRIC_OFFSET = 5_000L
    }
}
