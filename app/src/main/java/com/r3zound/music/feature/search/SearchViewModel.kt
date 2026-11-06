package com.r3zound.music.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.r3zound.music.core.model.ChannelInfo
import com.r3zound.music.core.model.ChannelResult
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.core.model.QualityOption
import com.r3zound.music.core.util.ignoreErrors
import com.r3zound.music.ui.components.songKeyOf
import com.r3zound.music.data.local.PlaylistEntity
import com.r3zound.music.data.local.SearchHistoryDao
import com.r3zound.music.data.local.SearchHistoryEntity
import com.r3zound.music.data.prefs.SettingsStore
import com.r3zound.music.data.remote.ProviderRegistry
import com.r3zound.music.data.remote.withLevel
import com.r3zound.music.data.repo.FavoriteRepository
import com.r3zound.music.data.repo.MusicRepository
import com.r3zound.music.download.DownloadRepository
import com.r3zound.music.media.PlayerHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 默认音质档位（与 SettingsStore.defaultQuality 的默认值一致）。 */
private const val DEFAULT_LEVEL = "lossless"

/** 结果展示模式。 */
enum class SearchGroupMode {
    /** 按渠道分组：每个渠道一节，渠道无结果也保留分组（让用户知道「搜过了，只是没搜到」）。 */
    BY_PROVIDER,

    /** 合并去重：所有渠道结果打平后按 (title, artist) 去重。 */
    MERGED,
}

/**
 * 搜索页 UI 状态。
 *
 * 设计要点：
 * - 结果按渠道存放于 [results]，每个渠道独立渲染：快的渠道先出、慢的后补，不做整页等待；
 * - [loading] 记录仍在等待的渠道，UI 用它决定该渠道显示骨架还是空态；
 * - 多选模式与搜索结果解耦，[selectedItems] 单独保存选中的曲目。
 */
data class SearchUiState(
    /** 输入框当前内容。 */
    val query: String = "",
    /** 最近一次真正发起搜索的关键字。 */
    val submittedQuery: String = "",
    /** 是否已经搜索过（决定展示热门歌手空态还是结果区）。 */
    val hasSearched: Boolean = false,
    /** 参与搜索的渠道集合；「全部」= 选中注册表里的所有渠道。 */
    val selectedProviders: Set<String> = ProviderRegistry.all.map { it.id }.toSet(),
    /** 结果展示模式。 */
    val groupMode: SearchGroupMode = SearchGroupMode.BY_PROVIDER,
    /** 渠道 id → 该渠道的结果（已返回的才有值）。 */
    val results: Map<String, ChannelResult> = emptyMap(),
    /** 仍在等待的渠道。 */
    val loading: Set<String> = emptySet(),
    /** 渠道健康信息（带健康指示点）。 */
    val channels: List<ChannelInfo> = ProviderRegistry.all,
    /** 最近 10 条搜索历史。 */
    val history: List<String> = emptyList(),
    /** songKey → 收藏记录 id，用于渲染收藏态与取消收藏。 */
    val favorites: Map<String, Long> = emptyMap(),
    /** 当前正在播放的曲目键（provider:id），用于列表高亮与定位。 */
    val currentSongKey: String? = null,
    /**
     * 本次搜索**临时**指定的渠道（首页分类用）。
     *
     * 分类搜索不应该改掉体检自动勾选的全局渠道，所以临时渠道只在这一次搜索里生效，
     * 搜完就清空。
     */
    val searchProviders: Set<String> = emptySet(),
    /** 是否处于多选模式。 */
    val selectionMode: Boolean = false,
    /** 多选模式下选中的曲目。 */
    val selectedItems: List<MusicItem> = emptyList(),
    /** 是否判定为网络不可用（顶部横幅）。 */
    val networkUnavailable: Boolean = false,
    /** 当前音质档位（与服务端 extra.selectedLevel 对应）。 */
    val selectedLevel: String = DEFAULT_LEVEL,
) {
    /** 本次实际参与搜索的渠道，按注册表顺序排列。 */
    val activeProviders: List<String>
        get() = ProviderRegistry.all.map { it.id }
            .filter { it in (searchProviders.ifEmpty { selectedProviders }) }

    /** 合并去重后的结果，按 (title, artist) 去重。 */
    val mergedItems: List<MusicItem>
        get() = results.values
            .flatMap { it.items }
            .distinctBy { "${it.title.trim()}|${it.artist.trim()}" }

    /** 是否还有渠道在等待。 */
    val searching: Boolean get() = loading.isNotEmpty()

    /** 是否全部渠道都失败（且已无渠道在等待）。 */
    val allFailed: Boolean
        get() = loading.isEmpty() && results.isNotEmpty() && results.values.all { it.error != null }
}

/**
 * 搜索页 ViewModel。
 *
 * 并发模型：一次搜索会对每个选中的渠道**单独**发起请求，
 * 哪个渠道先返回就先渲染哪个渠道的结果，慢的后补——
 * 这样即使某个渠道卡住 30 秒，用户也能立刻看到其余渠道的结果。
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val favoriteRepository: FavoriteRepository,
    private val downloadRepository: DownloadRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val settings: SettingsStore,
    private val playerHolder: PlayerHolder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** 全部歌单，供「加入歌单」对话框使用。 */
    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists.asStateFlow()

    private var searchJob: Job? = null


    /**
     * 长按后屏蔽紧随其后的点击。
     * TrackRow 内部自带 clickable，长按结束后抬手仍会触发一次 onClick，
     * 这里用时间窗把这一次「多余点击」吃掉，避免长按下载图标同时把歌下了。
     */
    private var suppressClickUntil: Long = 0L

    init {
        // 渠道健康度：先渲染缓存。
        // 只有「从未体检过」时才自动跑一次——否则每次进搜索页都覆盖掉
        // 用户在「渠道体检」页里手动做的勾选调整。
        viewModelScope.launch {
            _uiState.update { it.copy(channels = musicRepository.cachedHealth()) }
            val neverChecked = runCatching { settings.lastHealthCheck.first() }.getOrDefault(0L) <= 0L
            if (neverChecked) {
                runCatching { musicRepository.checkChannels() }.onSuccess { infos ->
                    _uiState.update { it.copy(channels = mergeHealth(infos)) }
                }
            }
        }
        // 渠道勾选由体检结果自动决定：订阅落库的 enabledProviders，
        // 体检完自动收敛到「能搜且能取流」的渠道，用户无需手动选。
        viewModelScope.launch {
            settings.activeProviders.collect { ids ->
                _uiState.update {
                    it.copy(
                        selectedProviders = ids ?: ProviderRegistry.all.map { p -> p.id }.toSet()
                    )
                }
            }
        }
        // 搜索历史
        viewModelScope.launch {
            searchHistoryDao.observeRecent().collect { list ->
                _uiState.update { it.copy(history = list.map { e -> e.keyword }.take(MAX_HISTORY)) }
            }
        }
        // 收藏状态
        viewModelScope.launch {
            favoriteRepository.observeFavorites().collect { list ->
                _uiState.update { it.copy(favorites = list.associate { f -> f.songKey to f.id }) }
            }
        }
        // 当前播放的曲目：列表高亮 + 准星定位都要用
        viewModelScope.launch {
            playerHolder.state.collect { playback ->
                val key = playback.current?.let { songKeyOf(it.provider, it.id) }
                _uiState.update { it.copy(currentSongKey = key) }
            }
        }
        // 歌单
        viewModelScope.launch {
            favoriteRepository.observePlaylists().collect { _playlists.value = it }
        }
        // 默认音质
        viewModelScope.launch {
            settings.defaultQuality.collect { level ->
                _uiState.update { it.copy(selectedLevel = level) }
            }
        }
    }

    // ── 搜索 ──────────────────────────────────────────────────

    /** 输入框内容变化。 */
    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    /**
     * 发起搜索。
     *
     * 每个渠道一条独立协程，谁先回来谁先渲染；
     * 新一轮搜索会取消上一轮未完成的请求。
     */
    fun submitSearch(query: String = _uiState.value.query, recordHistory: Boolean = true) {
        val keyword = query.trim()
        if (keyword.isEmpty()) return
        val providers = _uiState.value.activeProviders
        if (providers.isEmpty()) return

        searchJob?.cancel()
        _uiState.update {
            it.copy(
                // 临时渠道只对这一次生效，搜完就清
                searchProviders = emptySet(),
                query = query,
                submittedQuery = keyword,
                hasSearched = true,
                results = emptyMap(),
                loading = providers.toSet(),
                networkUnavailable = false,
                selectionMode = false,
                selectedItems = emptyList(),
            )
        }

        searchJob = viewModelScope.launch {
            providers.forEach { provider ->
                launch {
                    val result = try {
                        musicRepository.search(keyword, listOf(provider)).firstOrNull()
                            ?: ChannelResult(provider, emptyList(), error = "请求失败")
                    } catch (e: CancellationException) {
                        // 取消不是失败：新一轮搜索会取消旧请求，必须放行 CancellationException，
                        // 否则旧结果会被当成「请求失败」写回 uiState，污染新一轮的渲染。
                        throw e
                    } catch (e: Exception) {
                        ChannelResult(provider, emptyList(), error = e.message ?: "请求失败")
                    }
                    _uiState.update { state ->
                        val results = state.results + (provider to result)
                        state.copy(
                            results = results,
                            loading = state.loading - provider,
                            networkUnavailable = isNetworkDown(results),
                        )
                    }
                }
            }
        }

        if (recordHistory) {
            viewModelScope.launch {
                // 同名历史会追加新行，先删后插保证「最近使用」在最前
                searchHistoryDao.delete(keyword)
                searchHistoryDao.insert(SearchHistoryEntity(keyword = keyword))
            }
        }
    }

    /** 点击历史记录 / 热门歌手：直接搜。 */
    fun searchKeyword(keyword: String) {
        _uiState.update { it.copy(query = keyword) }
        submitSearch(keyword)
    }

    /**
     * 首页分类 / 带参跳转进来时的搜索。
     *
     * @param providers 限定参与检索的渠道；为空则沿用当前选择。
     */
    fun searchWithProviders(keyword: String, providers: Set<String>) {
        val valid = providers.filter { id -> ProviderRegistry.all.any { it.id == id } }
        // 只影响本次搜索，不动体检自动勾选的全局渠道
        _uiState.update { it.copy(searchProviders = valid.toSet()) }
        searchKeyword(keyword)
    }

    /** 重试：沿用上一次关键字。 */
    fun retry() {
        val keyword = _uiState.value.submittedQuery
        if (keyword.isNotBlank()) submitSearch(keyword, recordHistory = false)
    }

    // ── 渠道 ──────────────────────────────────────────────────

    /** 切换某个渠道是否参与搜索。 */
    fun toggleProvider(id: String) {
        val current = _uiState.value.selectedProviders
        val next = if (id in current) current - id else current + id
        _uiState.update { it.copy(selectedProviders = next) }
        rerunSearch()
    }

    /** 「全部」：选中所有渠道。 */
    fun selectAllProviders() {
        _uiState.update { it.copy(selectedProviders = ProviderRegistry.all.map { p -> p.id }.toSet()) }
        rerunSearch()
    }

    /** 筛选对话框确认：整体替换参与搜索的渠道集合。 */
    fun setProviders(ids: Set<String>) {
        _uiState.update { it.copy(selectedProviders = ids) }
        rerunSearch()
    }

    /** 切换结果展示模式。 */
    fun setGroupMode(mode: SearchGroupMode) {
        _uiState.update { it.copy(groupMode = mode) }
    }

    private fun rerunSearch() {
        val state = _uiState.value
        if (state.hasSearched && state.submittedQuery.isNotBlank()) {
            submitSearch(state.submittedQuery, recordHistory = false)
        }
    }

    // ── 多选模式 ──────────────────────────────────────────────

    /** 长按行：进入多选模式并选中该行。 */
    fun enterSelection(item: MusicItem) {
        suppressClickUntil = System.currentTimeMillis() + CLICK_SUPPRESS_MS
        _uiState.update { state ->
            val exists = state.selectedItems.any { sameSong(it, item) }
            state.copy(
                selectionMode = true,
                selectedItems = if (exists) state.selectedItems else state.selectedItems + item,
            )
        }
    }

    /** 切换某行的选中态。 */
    fun toggleSelection(item: MusicItem) {
        _uiState.update { state ->
            val exists = state.selectedItems.any { sameSong(it, item) }
            val next = if (exists) {
                state.selectedItems.filterNot { sameSong(it, item) }
            } else {
                state.selectedItems + item
            }
            state.copy(
                selectionMode = next.isNotEmpty(),
                selectedItems = next,
            )
        }
    }

    /** 全选：选中当前结果里的全部曲目。 */
    fun selectAll() {
        _uiState.update { state ->
            val all = state.results.values.flatMap { it.items }.distinctBy { songKeyOf(it) }
            state.copy(selectionMode = true, selectedItems = all)
        }
    }

    /** 退出多选模式。 */
    fun exitSelection() {
        suppressClickUntil = System.currentTimeMillis() + CLICK_SUPPRESS_MS
        _uiState.update { it.copy(selectionMode = false, selectedItems = emptyList()) }
    }

    /** 行点击：多选模式下切换选中，否则播放。返回 true 表示已开始播放（UI 需跳播放页）。 */
    fun onRowClick(item: MusicItem): Boolean {
        if (suppressClick()) return false
        val state = _uiState.value
        if (state.selectionMode) {
            toggleSelection(item)
            return false
        }
        play(item)
        return true
    }

    // ── 曲目动作 ──────────────────────────────────────────────

    /** 播放（带上当前音质档位，让取流时服务端按所选档位返回）。 */
    fun play(item: MusicItem) {
        playerHolder.play(item.withSelectedLevel())
        // 需求：搜索结果每次被点击播放，都自动进入「默认收藏」
        viewModelScope.launch {
            ignoreErrors { favoriteRepository.addToDefaultPlaylist(item) }
        }
    }

    /** 切换收藏。 */
    fun toggleFavorite(item: MusicItem) {
        viewModelScope.launch {
            // 收藏会同时写入 favorites 表和内置的「默认收藏」歌单
            val wasFavorited = isFavorite(item)
            ignoreErrors { favoriteRepository.setFavorite(item, !wasFavorited) }
        }
    }

    /** 是否已收藏。 */
    fun isFavorite(item: MusicItem): Boolean =
        _uiState.value.favorites.containsKey(songKeyOf(item))

    /** 点击下载图标：加入下载队列。 */
    fun onDownloadIcon(item: MusicItem) {
        if (suppressClick()) return
        download(item)
    }

    /** 加入下载队列。 */
    fun download(item: MusicItem) {
        viewModelScope.launch {
            downloadRepository.enqueue(
                item.withSelectedLevel(),
                qualityOf(item),
                "${item.artist} - ${item.title}",
            )
        }
    }

    /** 批量下载选中的曲目。 */
    fun downloadSelected() {
        val items = _uiState.value.selectedItems
        if (items.isEmpty()) return
        viewModelScope.launch {
            items.forEach { item ->
                downloadRepository.enqueue(
                    item.withSelectedLevel(),
                    qualityOf(item),
                    "${item.artist} - ${item.title}",
                )
            }
        }
    }

    /** 把选中的曲目加入指定歌单。 */
    fun addSelectedToPlaylist(playlistId: Long) {
        val items = _uiState.value.selectedItems
        if (items.isEmpty()) return
        viewModelScope.launch {
            favoriteRepository.addFavoritesToPlaylist(playlistId, items)
            exitSelection()
        }
    }

    /** 新建歌单并把选中的曲目加进去。 */
    fun createPlaylistAndAdd(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val items = _uiState.value.selectedItems
        viewModelScope.launch {
            val id = favoriteRepository.createPlaylist(trimmed)
            if (items.isNotEmpty()) favoriteRepository.addFavoritesToPlaylist(id, items)
            exitSelection()
        }
    }

    // ── 内部工具 ──────────────────────────────────────────────

    /** 命中的音质档位；服务端没给对应档位时取第一条。 */
    private fun qualityOf(item: MusicItem): QualityOption? {
        val level = _uiState.value.selectedLevel
        return item.qualityOptions.firstOrNull { it.value == level } ?: item.qualityOptions.firstOrNull()
    }

    /** 把当前音质档位写进 extra，取流/下载才会按所选档位返回。 */
    private fun MusicItem.withSelectedLevel(): MusicItem =
        copy(extra = extra?.withLevel(_uiState.value.selectedLevel))

    private fun songKeyOf(item: MusicItem): String = songKeyOf(item.provider, item.id)

    private fun sameSong(a: MusicItem, b: MusicItem): Boolean =
        a.provider == b.provider && a.id == b.id

    private fun suppressClick(): Boolean = System.currentTimeMillis() < suppressClickUntil

    /** 所有渠道都失败，且失败原因都指向网络 → 判定为网络不可用。 */
    private fun isNetworkDown(results: Map<String, ChannelResult>): Boolean {
        if (results.isEmpty()) return false
        return results.values.all { r -> r.error?.let { isNetworkError(it) } == true }
    }

    /** 把体检结果按注册表顺序合并回渠道列表。 */
    private fun mergeHealth(infos: List<ChannelInfo>): List<ChannelInfo> {
        val map = infos.associateBy { it.id }
        return ProviderRegistry.all.map { base -> map[base.id]?.copy(name = base.name) ?: base }
    }

    companion object {
        /** 搜索历史最多展示的条数。 */
        const val MAX_HISTORY = 10

        /** 长按后屏蔽多余点击的时间窗（毫秒）。 */
        private const val CLICK_SUPPRESS_MS = 700L

        /** 网络类错误的关键词（OkHttp / 系统异常文案差异较大，用关键字兜底）。 */
        private val NETWORK_HINTS = listOf(
            "unable to resolve host",
            "failed to connect",
            "connection reset",
            "connection refused",
            "network is unreachable",
            "no network",
            "timeout",
            "timed out",
            "socket",
            "ssl",
            "econn",
            "网络",
            "超时",
            "无法连接",
        )

        private fun isNetworkError(message: String): Boolean {
            val lower = message.lowercase()
            return NETWORK_HINTS.any { lower.contains(it) }
        }
    }
}
