package com.r3zound.music.feature.downloads

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.data.local.DownloadStatus
import com.r3zound.music.data.local.DownloadTaskEntity
import com.r3zound.music.download.DownloadRepository
import com.r3zound.music.media.PlayerHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

/** 下载中心标签页。 */
enum class DownloadTab(val label: String) {
    ACTIVE("下载中"),
    COMPLETED("已完成"),
    FAILED("失败"),
}

/** 顶栏的排序方式。 */
enum class DownloadSort(val label: String) {
    NEWEST("最新在前"),
    OLDEST("最早在前"),
    TITLE("按标题排序"),
}

/** 下载中心界面状态。 */
data class DownloadsUiState(
    /** 下载中（含排队、暂停、正在恢复直链的任务）。 */
    val active: List<DownloadTaskEntity> = emptyList(),
    val completed: List<DownloadTaskEntity> = emptyList(),
    val failed: List<DownloadTaskEntity> = emptyList(),
    val tab: DownloadTab = DownloadTab.ACTIVE,
    val sort: DownloadSort = DownloadSort.NEWEST,
    /** taskId → 估算速度（字节/秒）。 */
    val speeds: Map<String, Long> = emptyMap(),
    /** 正在自动恢复（直链过期）的任务。 */
    val recovering: Set<String> = emptySet(),
)

/** 本地播放请求：由 ViewModel 发出，界面交给 [LocalPlayback] 真正出声。 */
data class LocalPlayRequest(val taskId: String, val path: String)

/** 用户主动暂停时仓库写入的错误文案。 */
internal const val DOWNLOAD_ERROR_CANCELLED = "已取消"

/**
 * 下载中心 ViewModel。
 *
 * - 用一次 `observeAll` 拿全量任务，再在本地分组（比订阅三个 Flow 更简单，也不会漏更新）
 * - 速度用 `downloadedBytes` 与 `updatedAt` 的变化量粗略估算
 * - 直链过期（直链约 30 分钟过期）的失败任务会自动重取直链并**断点续传**，不是普通失败
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val playerHolder: PlayerHolder,
) : ViewModel() {

    private val tab = MutableStateFlow(DownloadTab.ACTIVE)
    private val sort = MutableStateFlow(DownloadSort.NEWEST)
    private val speeds = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** 本地播放请求通道（避免界面重组时重复消费）。 */
    private val _localPlay = Channel<LocalPlayRequest>(Channel.BUFFERED)
    val localPlay = _localPlay.receiveAsFlow()

    /** 「直链过期」自动重取直链的次数，避免无限循环。 */
    private val autoRetryCount = mutableMapOf<String, Int>()

    /** 已经排队等待自动恢复的任务。 */
    private val recovering = mutableSetOf<String>()

    /** 速度采样：taskId → (已下载字节, updatedAt)。 */
    private val samples = mutableMapOf<String, Pair<Long, Long>>()

    val uiState: StateFlow<DownloadsUiState> = combine(
        downloadRepository.observeAll(),
        tab,
        sort,
        speeds,
    ) { tasks, currentTab, currentSort, speedMap ->
        buildState(tasks, currentTab, currentSort, speedMap)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadsUiState(),
    )

    init {
        // 采样下载速度
        viewModelScope.launch {
            downloadRepository.observeAll().collect { tasks -> sampleSpeeds(tasks) }
        }
        // 直链过期的失败任务：自动重取直链并断点续传
        viewModelScope.launch {
            downloadRepository.observeAll().collect { tasks ->
                tasks.filter { it.status == DownloadStatus.FAILED && isExpiredLink(it.error) }
                    .forEach { scheduleRecover(it) }
            }
        }
    }

    // ── 分组 ──────────────────────────────────────────────────

    private fun buildState(
        tasks: List<DownloadTaskEntity>,
        currentTab: DownloadTab,
        currentSort: DownloadSort,
        speedMap: Map<String, Long>,
    ): DownloadsUiState {
        val active = mutableListOf<DownloadTaskEntity>()
        val completed = mutableListOf<DownloadTaskEntity>()
        val failed = mutableListOf<DownloadTaskEntity>()
        tasks.forEach { task ->
            when {
                task.status == DownloadStatus.COMPLETED -> completed += task
                // 用户主动暂停的任务仍留在「下载中」，可以继续续传
                task.status == DownloadStatus.FAILED && isUserPaused(task) -> active += task
                task.status == DownloadStatus.FAILED -> failed += task
                else -> active += task
            }
        }
        val comparator = comparatorFor(currentSort)
        return DownloadsUiState(
            active = active.sortedWith(comparator),
            completed = completed.sortedWith(comparator),
            failed = failed.sortedWith(comparator),
            tab = currentTab,
            sort = currentSort,
            speeds = speedMap,
            recovering = failed
                .filter { task ->
                    val budget = autoRetryCount[task.taskId] ?: 0
                    isExpiredLink(task.error) && (task.taskId in recovering || budget < MAX_AUTO_RETRY)
                }
                .map { it.taskId }
                .toSet(),
        )
    }

    private fun comparatorFor(sort: DownloadSort): Comparator<DownloadTaskEntity> = when (sort) {
        DownloadSort.NEWEST -> compareByDescending { it.updatedAt }
        DownloadSort.OLDEST -> compareBy { it.updatedAt }
        DownloadSort.TITLE -> compareBy { it.title }
    }

    // ── 速度估算 ───────────────────────────────────────────────

    private fun sampleSpeeds(tasks: List<DownloadTaskEntity>) {
        val next = speeds.value.toMutableMap()
        var changed = false
        val running = mutableSetOf<String>()
        tasks.forEach { task ->
            if (task.status != DownloadStatus.RUNNING) return@forEach
            running += task.taskId
            if (task.downloadedBytes <= 0L) return@forEach
            val previous = samples[task.taskId]
            samples[task.taskId] = task.downloadedBytes to task.updatedAt
            if (previous == null) return@forEach
            val elapsed = task.updatedAt - previous.second
            val delta = task.downloadedBytes - previous.first
            if (elapsed >= 400 && delta > 0) {
                next[task.taskId] = delta * 1000L / elapsed
                changed = true
            }
        }
        // 已结束的任务不再显示速度
        next.keys.filter { it !in running }.forEach {
            next.remove(it)
            changed = true
        }
        if (changed) speeds.value = next
    }

    // ── 直链过期的自动恢复 ─────────────────────────────────────

    /**
     * 直链约 30 分钟过期。下载中断后 APP 会自动重新取直链并断点续传，
     * 所以这类失败不是「彻底失败」，界面上要显示为「链接已过期，正在恢复」。
     */
    private fun scheduleRecover(task: DownloadTaskEntity) {
        if (task.taskId in recovering) return
        if ((autoRetryCount[task.taskId] ?: 0) >= MAX_AUTO_RETRY) return
        recovering += task.taskId
        viewModelScope.launch {
            delay(RECOVER_DELAY_MS)
            recovering -= task.taskId
            val used = autoRetryCount[task.taskId] ?: 0
            if (used >= MAX_AUTO_RETRY) return@launch
            autoRetryCount[task.taskId] = used + 1
            // 重新取直链，WorkManager 会用 Range 从断点继续，不会从头重下
            downloadRepository.retry(task.taskId)
        }
    }

    private fun isExpiredLink(error: String?): Boolean {
        val text = error ?: return false
        return text.contains("过期") || text.contains("直链已失效")
    }

    /** 用户主动暂停：仓库 cancel() 会写入「已取消」。 */
    private fun isUserPaused(task: DownloadTaskEntity): Boolean =
        task.status == DownloadStatus.PAUSED || task.error == DOWNLOAD_ERROR_CANCELLED

    // ── 操作 ──────────────────────────────────────────────────

    fun selectTab(tab: DownloadTab) {
        this.tab.value = tab
    }

    fun selectSort(sort: DownloadSort) {
        this.sort.value = sort
    }

    /** 暂停：停掉当前任务但保留断点文件，随时可以继续续传。 */
    fun pause(task: DownloadTaskEntity) {
        viewModelScope.launch { downloadRepository.cancel(task.taskId) }
    }

    /** 取消：彻底移除这条下载任务。 */
    fun cancel(task: DownloadTaskEntity) {
        viewModelScope.launch {
            downloadRepository.delete(task.taskId)
            samples.remove(task.taskId)
        }
    }

    /** 继续：重新排队，下载会从断点续传。 */
    fun resume(task: DownloadTaskEntity) {
        viewModelScope.launch { downloadRepository.retry(task.taskId) }
    }

    /** 重试失败的任务。 */
    fun retry(task: DownloadTaskEntity) {
        viewModelScope.launch {
            autoRetryCount.remove(task.taskId)
            downloadRepository.retry(task.taskId)
        }
    }

    /** 移除失败的任务。 */
    fun remove(task: DownloadTaskEntity) = cancel(task)

    /** 清空已完成的下载记录。 */
    fun clearCompleted() {
        viewModelScope.launch { downloadRepository.clearCompleted() }
    }

    /**
     * 播放已下载的本地文件。
     *
     * PlayerHolder 只认网络直链（`mu://` 占位 URI），已下载的文件是本地 content:// Uri，
     * 因此这里把任务拼成 MusicItem 登记进播放队列（Mini Player 能看到这首歌），
     * 真正的音频交给 [LocalPlayback] 用 MediaPlayer 播放，并把请求发给界面。
     */
    fun playLocal(task: DownloadTaskEntity) {
        val path = task.filePath?.takeIf { it.isNotBlank() } ?: return
        val item = localItem(task, path)
        playerHolder.playQueue(listOf(item), 0)
        // 避免与网络流同时出声：本地文件才是真正要放的声音
        runCatching { playerHolder.player.pause() }
        _localPlay.trySend(LocalPlayRequest(task.taskId, path))
    }

    /** 用任务字段拼装 MusicItem，extra 里带上本地文件路径。 */
    private fun localItem(task: DownloadTaskEntity, path: String): MusicItem = MusicItem(
        id = task.songId,
        title = task.title,
        artist = task.artist,
        album = task.album,
        cover = task.cover,
        duration = task.duration,
        provider = task.provider,
        extra = buildJsonObject {
            put("localPath", JsonPrimitive(path))
        },
    )

    companion object {
        /** 直链过期后自动重取直链的最大次数。 */
        private const val MAX_AUTO_RETRY = 2

        /** 自动恢复前的等待时间，留出时间让界面显示「正在恢复」。 */
        private const val RECOVER_DELAY_MS = 3_000L
    }
}

/** 本地播放状态。 */
data class LocalPlaybackState(
    /** 正在播放的下载任务。 */
    val taskId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val error: String? = null,
)

/**
 * 本地文件播放器。
 *
 * Android 10+ 的下载文件落在 MediaStore，路径是 content:// Uri，
 * 而 PlayerHolder 只负责网络直链，因此本地文件单独用 Android MediaPlayer 播放，
 * 并把播放状态暴露给界面（正在播放的条目高亮、按钮切成暂停）。
 */
class LocalPlayback(private val context: Context) {

    private val _state = MutableStateFlow(LocalPlaybackState())
    val state: StateFlow<LocalPlaybackState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            val mp = player ?: return
            if (!_state.value.isPlaying) return
            runCatching { _state.value = _state.value.copy(positionMs = mp.currentPosition) }
            handler.postDelayed(this, 500L)
        }
    }

    /** 播放指定任务；同一任务再次点击则暂停/继续。 */
    fun toggle(taskId: String, path: String) {
        if (_state.value.taskId == taskId && player != null) {
            if (_state.value.isPlaying) pause() else resume()
            return
        }
        play(taskId, path)
    }

    /** 开始播放本地文件。 */
    fun play(taskId: String, path: String) {
        releasePlayer()
        _state.value = LocalPlaybackState(taskId = taskId)
        val mp = MediaPlayer()
        player = mp
        try {
            when {
                path.startsWith("content://") || path.startsWith("file://") ->
                    mp.setDataSource(context, Uri.parse(path))
                else -> mp.setDataSource(path)
            }
            mp.setOnPreparedListener {
                it.start()
                _state.value = _state.value.copy(isPlaying = true)
                handler.postDelayed(ticker, 500L)
            }
            mp.setOnCompletionListener {
                _state.value = _state.value.copy(isPlaying = false, positionMs = 0)
            }
            mp.setOnErrorListener { _, what, extra ->
                _state.value = _state.value.copy(isPlaying = false, error = "播放失败（$what/$extra）")
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            _state.value = _state.value.copy(isPlaying = false, error = e.message ?: "播放失败")
            releasePlayer()
        }
    }

    fun pause() {
        val mp = player ?: return
        runCatching { mp.pause() }
        _state.value = _state.value.copy(isPlaying = false)
        handler.removeCallbacks(ticker)
    }

    fun resume() {
        val mp = player ?: return
        runCatching { mp.start() }
        _state.value = _state.value.copy(isPlaying = true)
        handler.postDelayed(ticker, 500L)
    }

    /** 释放资源。 */
    fun release() {
        releasePlayer()
        _state.value = LocalPlaybackState()
    }

    private fun releasePlayer() {
        handler.removeCallbacks(ticker)
        val mp = player
        player = null
        if (mp != null) runCatching { mp.release() }
    }
}
