package com.r3zound.music.media

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.core.util.SafeUrl
import com.r3zound.music.data.remote.MusicApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/** 播放队列状态。 */
data class PlaybackState(
    val current: MusicItem? = null,
    val queue: List<MusicItem> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lyrics: List<LyricEntry> = emptyList(),
    val lyricsLoading: Boolean = false,
    val error: String? = null,
)

data class LyricEntry(val timeMs: Long, val text: String)

/**
 * 睡眠定时器状态。
 *
 * @param remainingMs 剩余毫秒；null 表示没有设置定时。
 * @param totalMs 本次定时的总时长，用于算倒计时进度。
 */
data class SleepTimerState(
    val remainingMs: Long? = null,
    val totalMs: Long = 0L,
) {
    val active: Boolean get() = remainingMs != null
}

/**
 * 播放器持有者。
 *
 * ExoPlayer 常驻单例，UI 通过它读状态，[PlaybackService] 通过它拿到同一个实例。
 * 播放地址使用 `mu://` 占位 URI，由 [ResolvingDataSource] 在**真正读取时**解析成
 * CDN 直链——直链约 30 分钟过期，若在入队时就固定，队列靠后的歌会播放失败。
 */
@Singleton
class PlayerHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicApi: MusicApi,
    private val settings: com.r3zound.music.data.prefs.SettingsStore,
    private val historyDao: com.r3zound.music.data.local.HistoryDao,
) {

    /** mediaId(provider:id) → 曲目，供解析直链时回查。 */
    val itemsById = mutableMapOf<String, MusicItem>()


    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .apply {
            setMediaSourceFactory(
                ResolvingMediaSourceFactory(
                    context = context,
                    upstreamFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent("MuMusic/1.2 (Android)")
                        .setConnectTimeoutMs(15_000)
                        .setReadTimeoutMs(30_000)
                        .setAllowCrossProtocolRedirects(true),
                    resolve = { uri -> resolveBlocking(uri) },
                )
            )
        }
        .build()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // ── 睡眠定时器 ────────────────────────────────────────────
    private val _sleepTimer = MutableStateFlow(SleepTimerState())
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()

    private var sleepTimerJob: Job? = null

    /** 定时器用的协程作用域；跟随播放器生命周期，release() 时取消。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = player.currentMediaItemIndex
                val item = _state.value.queue.getOrNull(idx)
                _state.update {
                    it.copy(current = item, index = idx, positionMs = 0L, error = null)
                }
                // 记一条播放历史（收藏页的「最近播放」用）
                item?.let { scope.launch { recordHistory(it) } }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                _state.update { it.copy(durationMs = duration) }
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.update { it.copy(error = error.message ?: "播放失败") }
            }
        })
    }

    /** 设置播放队列并从 [startIndex] 开始播放。 */
    fun playQueue(items: List<MusicItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        items.forEach { itemsById["${it.provider}:${it.id}"] = it }
        _state.update { it.copy(queue = items, index = startIndex, current = items.getOrNull(startIndex)) }
        player.setMediaItems(items.map { it.toMediaItem() }, startIndex, 0L)
        player.prepare()
        player.play()
    }

    /** 播放单曲（替换队列）。 */
    fun play(item: MusicItem) = playQueue(listOf(item), 0)

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        if (player.hasNext()) player.seekToNextMediaItem()
    }

    fun previous() {
        if (player.currentPosition > 3000) player.seekTo(0) else player.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    // ── 睡眠定时器 ────────────────────────────────────────────
    /**
     * 启动睡眠定时器：到点自动停止播放。
     *
     * 重复调用会以新的时长重新计时（不是累加）。
     */
    fun startSleepTimer(durationMs: Long) {
        if (durationMs <= 0L) {
            cancelSleepTimer()
            return
        }
        sleepTimerJob?.cancel()
        _sleepTimer.value = SleepTimerState(remainingMs = durationMs, totalMs = durationMs)
        sleepTimerJob = scope.launch {
            val endAt = System.currentTimeMillis() + durationMs
            while (true) {
                val remain = endAt - System.currentTimeMillis()
                if (remain <= 0L) break
                _sleepTimer.value = _sleepTimer.value.copy(remainingMs = remain)
                delay(1000)
            }
            onSleepTimerFinished()
        }
    }

    /** 取消定时器。 */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimer.value = SleepTimerState()
    }

    /**
     * 写一条播放历史。
     *
     * 同一首歌重复播放时更新时间戳而不是插新行，历史里不会连出好几条一样的。
     */
    private suspend fun recordHistory(item: MusicItem) {
        val key = "${item.provider}:${item.id}"
        historyDao.deleteByKey(key)
        historyDao.insert(
            com.r3zound.music.data.local.HistoryEntity(
                songKey = key,
                provider = item.provider,
                songId = item.id,
                title = item.title,
                artist = item.artist,
                album = item.album,
                cover = item.cover,
                duration = item.duration,
                extraJson = item.extra?.toString(),
            )
        )
        // 只保留最近若干条，避免表无限增长
        historyDao.trimTo(MAX_HISTORY)
    }

    /** 定时到点时停止播放并清空状态（供 Job 内部与外部共用）。 */
    private fun onSleepTimerFinished() {
        player.stop()
        _sleepTimer.value = SleepTimerState()
    }

    fun setRepeatMode(repeat: Int) {
        player.repeatMode = repeat
    }

    fun setShuffle(enabled: Boolean) {
        player.shuffleModeEnabled = enabled
    }

    fun addToQueue(items: List<MusicItem>) {
        items.forEach { itemsById["${it.provider}:${it.id}"] = it }
        player.addMediaItems(items.map { it.toMediaItem() })
        _state.update { it.copy(queue = it.queue + items) }
    }

    fun removeAt(index: Int) {
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
        _state.update { s -> s.copy(queue = s.queue.filterIndexed { i, _ -> i != index }) }
    }

    fun clearQueue() {
        player.clearMediaItems()
        itemsById.clear()
        _state.update { PlaybackState() }
    }

    fun setLyrics(entries: List<LyricEntry>) = _state.update { it.copy(lyrics = entries, lyricsLoading = false) }

    fun setLyricsLoading(loading: Boolean) = _state.update { it.copy(lyricsLoading = loading) }

    fun currentPositionMs(): Long = player.currentPosition

    /** 依据播放位置找出当前歌词行索引。 */
    /**
     * 依据播放位置找出当前歌词行索引。
     *
     * @param offsetMs 歌词时间偏移（毫秒）。歌词实际时间 = 歌词时间 + offset，
     *                 用于修正某些源「歌词比歌慢半拍」的问题。
     */
    fun lyricIndexAt(positionMs: Long, offsetMs: Long = 0L): Int {
        val lines = _state.value.lyrics
        if (lines.isEmpty()) return -1
        var idx = -1
        for (i in lines.indices) {
            if (lines[i].timeMs + offsetMs <= positionMs) idx = i else break
        }
        return idx
    }

    /**
     * 同步解析直链（播放线程调用）。
     *
     * 解析必须发生在真正开始读数据的时候——直链约 30 分钟过期，
     * 队列里排得靠后的歌如果提前解析，到播放时链接已经失效了。
     */
    private fun resolveBlocking(uri: String): String? = runCatching {
        // URI 形如 mu://play/<provider>/<id>，而 itemsById 的键是 <provider>:<id>，
        // 取回后要把分隔符换回冒号，否则永远查不到曲目。
        val key = uri.removePrefix("mu://play/").replace('/', ':')
        val item = itemsById[key] ?: return@runCatching null
        val info = runBlocking {
            val url = settings.baseUrl.first()
            musicApi.playInfo(url, item.id, item.provider, item.extra)
        }
        SafeUrl.normalize(info.url)
    }.getOrNull()

    fun release() {
        sleepTimerJob?.cancel()
        scope.cancel()
        player.release()
    }

    companion object {
        /** 播放历史最多保留多少条。 */
        const val MAX_HISTORY = 100
    }
}

fun MusicItem.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId("$provider:$id")
    .setUri("mu://play/$provider/$id")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(cover?.let { android.net.Uri.parse(it) })
            .setIsPlayable(true)
            .build()
    )
    .build()
