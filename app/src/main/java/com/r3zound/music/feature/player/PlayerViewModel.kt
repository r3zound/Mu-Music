package com.r3zound.music.feature.player

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.core.util.ignoreErrors
import com.r3zound.music.core.model.QualityOption
import com.r3zound.music.data.remote.withLevel
import com.r3zound.music.data.repo.FavoriteRepository
import com.r3zound.music.data.repo.MusicRepository
import com.r3zound.music.download.DownloadRepository
import com.r3zound.music.media.LyricEntry
import com.r3zound.music.media.PlaybackState
import com.r3zound.music.media.PlayerHolder
import com.r3zound.music.media.SleepTimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 播放页自身的 UI 状态（与播放核心状态分开，减少整页重组）。 */
data class PlayerUiState(
    /** 当前歌曲是否已收藏。 */
    val favorited: Boolean = false,
    /** 轻提示文案。 */
    val message: String? = null,
    /** 当前选择的音质档位。 */
    val selectedQuality: QualityOption? = null,
)

/**
 * 播放页 / 播放队列页的 ViewModel。
 *
 * 播放内核（队列、进度、歌词行）由 [PlayerHolder] 单例持有，这里只做编排：
 * 歌词拉取、收藏同步、下载入队、音质切换。
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerHolder: PlayerHolder,
    private val musicRepository: MusicRepository,
    private val favoriteRepository: FavoriteRepository,
    private val downloadRepository: DownloadRepository,
    private val settingsStore: com.r3zound.music.data.prefs.SettingsStore,
) : ViewModel() {

    /** 播放核心状态（当前曲目、队列、歌词、播放中标记）。 */
    val playback: StateFlow<PlaybackState> = playerHolder.state

    /** 睡眠定时器状态（null 表示没有设置定时）。 */
    val sleepTimer: StateFlow<SleepTimerState> = playerHolder.sleepTimer

    /** 启动睡眠定时器。 */
    fun startSleepTimer(minutes: Int) =
        playerHolder.startSleepTimer(minutes * 60_000L)

    /** 取消睡眠定时器。 */
    fun cancelSleepTimer() = playerHolder.cancelSleepTimer()

    // ── 播放队列存成歌单 ────────────────────────────────────────

    /**
     * 把当前播放队列存成一个新歌单。
     *
     * @return 新歌单的 id；队列为空时返回 null。
     */
    suspend fun saveQueueAsPlaylist(name: String): Long? {
        val queue = playerHolder.state.value.queue
        if (queue.isEmpty()) return null
        val playlistId = favoriteRepository.createPlaylist(name)
        favoriteRepository.addFavoritesToPlaylist(playlistId, queue)
        return playlistId
    }

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    /** 随机播放开关。 */
    private val _shuffle = MutableStateFlow(playerHolder.player.shuffleModeEnabled)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    /** 循环模式：0=关闭 1=列表循环 2=单曲循环。 */
    private val _repeatMode = MutableStateFlow(playerHolder.player.repeatMode)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    /** 已拉过歌词的歌曲 key，避免切歌时重复请求。 */
    private var loadedLyricKey: String? = null

    // ── 歌词 ──────────────────────────────────────────────────────────────

    /**
     * 拉取歌词并写入播放内核。
     *
     * 服务端返回的时间单位是秒，这里统一换算成毫秒。
     */
    fun loadLyrics(item: MusicItem) {
        loadLyricOffset(item)
        val key = songKey(item)
        if (key == loadedLyricKey) return
        loadedLyricKey = key
        viewModelScope.launch {
            playerHolder.setLyrics(emptyList())
            playerHolder.setLyricsLoading(true)
            val entries = try {
                musicRepository.lyric(item).lines
                    .map { LyricEntry(timeMs = (it.time * 1000).toLong(), text = it.text) }
                    .sortedBy { it.timeMs }
            } catch (e: CancellationException) {
                // 切歌时旧请求会被取消，取消不是「没歌词」，放行让协程结束
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            playerHolder.setLyrics(entries)
        }
    }

    /** 当前播放位置（毫秒）。 */
    fun currentPositionMs(): Long = playerHolder.currentPositionMs()

    /** 播放位置对应的歌词行下标，没有歌词时返回 -1。 */
    fun lyricIndexAt(positionMs: Long): Int =
        playerHolder.lyricIndexAt(positionMs, _lyricOffset.value)

    // ── 歌词显示设置 ──────────────────────────────────────────
    /** 歌词字号（sp）。 */
    val lyricFontSize: StateFlow<Int> = settingsStore.lyricFontSize.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = com.r3zound.music.data.prefs.SettingsStore.DEFAULT_LYRIC_FONT_SIZE,
    )

    /** 当前歌曲的歌词时间偏移（毫秒）。 */
    private val _lyricOffset = MutableStateFlow(0L)
    val lyricOffset: StateFlow<Long> = _lyricOffset.asStateFlow()

    /** 设置歌词字号。 */
    fun setLyricFontSize(size: Int) {
        viewModelScope.launch { settingsStore.setLyricFontSize(size) }
    }

    /** 设置当前歌曲的歌词时间偏移（毫秒）。 */
    fun setLyricOffset(offsetMs: Long) {
        val item = playback.value.current ?: return
        _lyricOffset.value = offsetMs
        viewModelScope.launch {
            settingsStore.setLyricOffset(item.provider, item.id, offsetMs)
        }
    }

    /** 切歌时把该歌的偏移读回来。 */
    private fun loadLyricOffset(item: MusicItem) {
        viewModelScope.launch {
            settingsStore.lyricOffset(item.provider, item.id).collect { offset ->
                _lyricOffset.value = offset
            }
        }
    }

    // ── 曲目切换时的状态同步 ──────────────────────────────────────────────

    /** 进入页面或切歌时同步收藏状态与音质档位。 */
    fun syncItem(item: MusicItem?) {
        if (item == null) {
            _ui.update { it.copy(favorited = false, selectedQuality = null) }
            return
        }
        _ui.update { state ->
            val keep = state.selectedQuality?.takeIf { q -> item.qualityOptions.any { it.value == q.value } }
            state.copy(selectedQuality = keep ?: item.qualityOptions.firstOrNull())
        }
        viewModelScope.launch {
            val favorited = runCatching { favoriteRepository.isFavorited(item) }.getOrDefault(false)
            _ui.update { it.copy(favorited = favorited) }
        }
    }

    // ── 收藏 ──────────────────────────────────────────────────────────────

    /** 收藏 / 取消收藏。 */
    fun toggleFavorite(item: MusicItem) {
        viewModelScope.launch {
            val wasFavorited = _ui.value.favorited
            // 收藏会同时写入 favorites 表和内置的「默认收藏」歌单
            val now = ignoreErrors { favoriteRepository.setFavorite(item, !wasFavorited) }
            if (now == null) {
                _ui.update { it.copy(message = "收藏失败") }
            } else {
                _ui.update {
                    it.copy(
                        favorited = now,
                        message = if (now) "已收藏到默认收藏" else "已取消收藏",
                    )
                }
            }
        }
    }

    // ── 下载 ──────────────────────────────────────────────────────────────

    /** 加入下载队列。 */
    fun download(item: MusicItem, quality: QualityOption?) {
        viewModelScope.launch {
            val fileName = "${item.artist} - ${item.title}"
            runCatching { downloadRepository.enqueue(item, quality, fileName) }
                .onSuccess { _ui.update { it.copy(message = "已加入下载队列") } }
                .onFailure { e ->
                    _ui.update { it.copy(message = "下载失败：${e.message ?: "未知错误"}") }
                }
        }
    }

    // ── 音质 ──────────────────────────────────────────────────────────────

    /**
     * 切换音质：把选中的档位写进 extra 的 selectedLevel，再重新取流播放。
     *
     * 直链约 30 分钟过期且与音质绑定，必须重新解析，所以这里重新入队播放。
     */
    fun selectQuality(item: MusicItem, option: QualityOption) {
        val updated = item.copy(extra = item.extra?.withLevel(option.value))
        _ui.update { it.copy(selectedQuality = option, message = "已切换到 ${option.label}") }
        playerHolder.playQueue(listOf(updated), 0)
    }

    // ── 播放控制 ──────────────────────────────────────────────────────────

    fun togglePlayPause() = playerHolder.togglePlayPause()

    fun next() = playerHolder.next()

    fun previous() = playerHolder.previous()

    fun seekTo(positionMs: Long) = playerHolder.seekTo(positionMs)

    /** 随机播放开关。 */
    fun toggleShuffle() {
        val enabled = !_shuffle.value
        _shuffle.value = enabled
        playerHolder.setShuffle(enabled)
    }

    /** 循环模式：关闭 → 列表循环 → 单曲循环 → 关闭。 */
    fun cycleRepeat() {
        val next = when (_repeatMode.value) {
            REPEAT_OFF -> REPEAT_ALL
            REPEAT_ALL -> REPEAT_ONE
            else -> REPEAT_OFF
        }
        _repeatMode.value = next
        playerHolder.setRepeatMode(next)
    }

    // ── 队列 ──────────────────────────────────────────────────────────────

    /** 点击队列中的某一项，立刻播放它。 */
    fun playAt(index: Int) {
        val queue = playerHolder.state.value.queue
        if (index in queue.indices) playerHolder.playQueue(queue, index)
    }

    fun removeAt(index: Int) = playerHolder.removeAt(index)

    fun clearQueue() {
        playerHolder.clearQueue()
        _ui.update { it.copy(message = "队列已清空") }
    }

    /** 消费掉轻提示。 */
    fun consumeMessage() {
        _ui.update { it.copy(message = null) }
    }

    private fun songKey(item: MusicItem): String = "${item.provider}:${item.id}"

    companion object {
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2
    }
}
