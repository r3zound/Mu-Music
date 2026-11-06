package com.r3zound.music.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.data.local.FavoriteEntity
import com.r3zound.music.data.local.HistoryEntity
import com.r3zound.music.data.local.HistoryDao
import com.r3zound.music.data.local.FavoriteWithPlaylist
import com.r3zound.music.data.local.PlaylistEntity
import com.r3zound.music.data.local.TagWithCount
import com.r3zound.music.data.repo.FavoriteRepository
import com.r3zound.music.download.DownloadRepository
import com.r3zound.music.media.PlayerHolder
import com.r3zound.music.ui.components.songKeyOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 收藏夹顶部的三个分页。 */
enum class FavoritesTab {
    PLAYLISTS,
    SONGS,
    TAGS,
    /** 最近播放。 */
    HISTORY,
}

/** 一条播放历史。 */
data class HistorySong(
    val item: MusicItem,
    /** 最后播放时间戳。 */
    val playedAt: Long,
)

/** 一条收藏：既保留收藏记录 id（移除/加入歌单用），也带曲目元数据。 */
data class FavoriteSong(
    val favoriteId: Long,
    val item: MusicItem,
    /** 收藏（加入）时间，排序用。 */
    val createdAt: Long = 0L,
)

/** 收藏夹页面状态。 */
data class FavoritesUiState(
    val tab: FavoritesTab = FavoritesTab.PLAYLISTS,
    val playlists: List<PlaylistEntity> = emptyList(),
    /** 歌单 id → 曲目数。 */
    val playlistCounts: Map<Long, Int> = emptyMap(),
    /** 全部收藏，按加入时间倒序。 */
    val songs: List<FavoriteSong> = emptyList(),
    val tags: List<TagWithCount> = emptyList(),
    val query: String = "",
    /** 展开中的标签 id，为空表示没展开任何标签。 */
    val expandedTagId: Long? = null,
    /** 展开标签下的歌曲。 */
    val taggedSongs: List<FavoriteSong> = emptyList(),
    /** 当前正在播放的曲目键（provider:id），用于列表高亮与定位。 */
    val currentSongKey: String? = null,
    /** 最近播放的曲目，按播放时间倒序。 */
    val history: List<HistorySong> = emptyList(),
)

/**
 * 收藏夹：自建歌单 + 全部歌曲 + 标签。
 *
 * 数据全部来自 [FavoriteRepository] 的 Flow，界面只读 [uiState]。
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    private val downloadRepository: DownloadRepository,
    private val playerHolder: PlayerHolder,
    private val historyDao: HistoryDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        // 首次启动自动创建内置「默认收藏」歌单
        viewModelScope.launch {
            favoriteRepository.ensureDefaultPlaylist()
        }

        // 歌单列表 + 每个歌单的曲目数
        viewModelScope.launch {
            favoriteRepository.observePlaylists().collect { playlists ->
                val counts = HashMap<Long, Int>(playlists.size)
                playlists.forEach { playlist ->
                    counts[playlist.id] = favoriteRepository.playlistSongCount(playlist.id)
                }
                _uiState.update { it.copy(playlists = playlists, playlistCounts = counts) }
            }
        }

        // 全部收藏（DAO 已按加入时间倒序）
        viewModelScope.launch {
            favoriteRepository.observeFavorites().collect { favorites ->
                _uiState.update { state ->
                    state.copy(songs = favorites.map { it.toSong() })
                }
            }
        }

        // 标签
        viewModelScope.launch {
            favoriteRepository.observeTags().collect { tags ->
                _uiState.update { it.copy(tags = tags) }
            }
        }

        // 当前播放的曲目：列表高亮 + 准星定位都要用
        viewModelScope.launch {
            playerHolder.state.collect { playback ->
                val key = playback.current?.let { songKeyOf(it.provider, it.id) }
                _uiState.update { it.copy(currentSongKey = key) }
            }
        }

        // 播放历史
        viewModelScope.launch {
            historyDao.observeRecent(MAX_HISTORY).collect { list ->
                _uiState.update {
                    it.copy(
                        history = list.map { e ->
                            HistorySong(
                                item = favoriteRepository.entityToItem(e),
                                playedAt = e.playedAt,
                            )
                        }
                    )
                }
            }
        }

        // 展开标签下的歌曲：切换标签时取消上一个订阅
        viewModelScope.launch {
            _uiState.map { it.expandedTagId }
                .distinctUntilChanged()
                .collectLatest { tagId ->
                    if (tagId == null) {
                        _uiState.update { it.copy(taggedSongs = emptyList()) }
                    } else {
                        favoriteRepository.observeTaggedSongs(tagId).collect { entries ->
                            _uiState.update { state ->
                                state.copy(taggedSongs = entries.map { it.asFavoriteEntity().toSong() })
                            }
                        }
                    }
                }
        }
    }

    // ── 分页与筛选 ────────────────────────────────────────────
    fun selectTab(tab: FavoritesTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    /** 点击标签：展开 / 收起该标签下的歌曲。 */
    fun toggleTag(tagId: Long) {
        _uiState.update { it.copy(expandedTagId = if (it.expandedTagId == tagId) null else tagId) }
    }

    // ── 歌单 ──────────────────────────────────────────────────
    fun createPlaylist(name: String, description: String?, coverVariant: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            favoriteRepository.createPlaylist(name, description?.takeIf { it.isNotBlank() }, coverVariant)
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { favoriteRepository.renamePlaylist(id, name) }
    }

    fun updatePlaylistDescription(id: Long, description: String?) {
        viewModelScope.launch {
            favoriteRepository.updatePlaylistDescription(id, description?.takeIf { it.isNotBlank() })
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { favoriteRepository.deletePlaylist(id) }
    }

    // ── 标签 ──────────────────────────────────────────────────
    fun createTag(name: String, colorIndex: Int) {
        if (name.isBlank()) return
        viewModelScope.launch { favoriteRepository.createTag(name, colorIndex) }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch { favoriteRepository.deleteTag(id) }
    }

    // ── 收藏条目 ──────────────────────────────────────────────
    /** 播放一首收藏。 */
    fun play(item: MusicItem) {
        playerHolder.play(item)
    }

    /** 取消收藏（同时会从所有歌单与标签里摘掉）。 */
    fun removeFavorite(favoriteId: Long) {
        viewModelScope.launch { favoriteRepository.removeFavorite(favoriteId) }
    }

    /** 把歌曲加入下载队列，音质取该曲可选音质的第一个。 */
    fun download(items: List<MusicItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            items.forEach { item ->
                downloadRepository.enqueue(
                    item = item,
                    quality = item.qualityOptions.firstOrNull(),
                    fileName = "${item.artist} - ${item.title}",
                )
            }
        }
    }

    private fun FavoriteEntity.toSong(): FavoriteSong = toFavoriteSong(favoriteRepository::entityToItem)

    companion object {
        /** 播放历史最多保留多少条。 */
        const val MAX_HISTORY = 100
    }

}

/**
 * 把收藏实体转成页面用的曲目模型。
 *
 * 抽成顶层函数是为了让歌单详情页复用，避免两个 ViewModel 各写一份转换逻辑。
 */
internal fun FavoriteEntity.toFavoriteSong(convert: (FavoriteEntity) -> MusicItem): FavoriteSong =
    FavoriteSong(
        favoriteId = id,
        item = convert(this),
        createdAt = createdAt,
    )

/**
 * 取出收藏实体本身。
 *
 * 仓库里按标签查询的返回类型在不同版本可能是 [FavoriteEntity] 或 [FavoriteWithPlaylist]，
 * 这里用 `Any` 做一次归一，页面只认 [FavoriteEntity]。
 */
internal fun Any.asFavoriteEntity(): FavoriteEntity = when (this) {
    is FavoriteEntity -> this
    is FavoriteWithPlaylist -> favorite
    else -> error("不支持的收藏条目类型：${javaClass.name}")

}
