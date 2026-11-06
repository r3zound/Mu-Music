package com.r3zound.music.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.data.repo.FavoriteRepository
import com.r3zound.music.ui.components.PlaylistOption
import com.r3zound.music.ui.components.TagOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 「加入歌单 / 打标签」的共享状态。
 *
 * 收藏页和搜索页都要用这套能力，抽成一个共享 ViewModel，
 * 避免两个 feature 包各写一份落库逻辑。
 */
data class SongActionsUiState(
    /** 正在打标签的收藏记录 id；为 null 表示标签选择器没打开。 */
    val tagPickerTarget: Long? = null,
    /** 标签选择器里已勾选的标签 id。 */
    val tagPickerSelected: Set<Long> = emptySet(),
)

@HiltViewModel
class SongActionsViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SongActionsUiState())
    val uiState: StateFlow<SongActionsUiState> = _uiState.asStateFlow()

    /** 可选歌单（歌单选择器用）。 */
    val playlistOptions: StateFlow<List<PlaylistOption>> =
        favoriteRepository.observePlaylists()
            .map { list -> list.map { PlaylistOption(id = it.id, name = it.name) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 可选标签（标签选择器用）。 */
    val tagOptions: StateFlow<List<TagOption>> =
        favoriteRepository.observeTags()
            .map { list -> list.map { TagOption(id = it.tag.id, name = it.tag.name, colorIndex = it.tag.colorIndex) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── 加入歌单 ────────────────────────────────────────────────

    /** 把歌曲加入指定歌单；歌单内已有则忽略。 */
    fun addToPlaylist(playlistId: Long, item: MusicItem) {
        viewModelScope.launch {
            favoriteRepository.addToPlaylist(playlistId, item)
        }
    }

    /** 新建歌单并把歌曲加进去。 */
    fun createPlaylistAndAdd(name: String, item: MusicItem) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val playlistId = favoriteRepository.createPlaylist(name)
            favoriteRepository.addToPlaylist(playlistId, item)
        }
    }

    // ── 标签 ────────────────────────────────────────────────────

    /**
     * 打开标签选择器。
     *
     * 打标签的前提是这首歌有收藏记录，所以这里会先补一条收藏
     * （未收藏的歌打标签，等于顺手收藏了它）。
     */
    fun openTagPicker(item: MusicItem) {
        viewModelScope.launch {
            val favoriteId = favoriteRepository.addFavorite(item)
            val current = favoriteRepository.tagIdsOf(favoriteId).toSet()
            _uiState.update { it.copy(tagPickerTarget = favoriteId, tagPickerSelected = current) }
        }
    }

    /** 用已知的收藏 id 直接打开（收藏页走这条，歌曲本来就已经是收藏）。 */
    fun openTagPickerForFavorite(favoriteId: Long) {
        viewModelScope.launch {
            val current = favoriteRepository.tagIdsOf(favoriteId).toSet()
            _uiState.update { it.copy(tagPickerTarget = favoriteId, tagPickerSelected = current) }
        }
    }

    fun closeTagPicker() {
        _uiState.update { it.copy(tagPickerTarget = null, tagPickerSelected = emptySet()) }
    }

    /** 勾选/取消一个标签，立即落库。 */
    fun toggleTag(tagId: Long) {
        val target = _uiState.value.tagPickerTarget ?: return
        viewModelScope.launch {
            val next = _uiState.value.tagPickerSelected.toMutableSet()
            if (!next.add(tagId)) next.remove(tagId)
            _uiState.update { it.copy(tagPickerSelected = next) }
            // setTags 是「全量覆盖」语义，这里传的是最新的完整集合
            favoriteRepository.setTags(target, next.toList())
        }
    }

    /** 新建标签并立刻勾上。 */
    fun createTagAndApply(name: String) {
        if (name.isBlank()) return
        val target = _uiState.value.tagPickerTarget ?: return
        viewModelScope.launch {
            val tagId = favoriteRepository.createTag(name)
            val next = _uiState.value.tagPickerSelected + tagId
            _uiState.update { it.copy(tagPickerSelected = next) }
            favoriteRepository.setTags(target, next.toList())
        }
    }

    // ── 批量打标签 ──────────────────────────────────────────────

    /**
     * 批量勾选/取消一个标签。
     *
     * 语义：所有选中歌曲**都**带这个标签 → 全部取消；否则 → 全部打上。
     */
    fun toggleTagForItems(items: List<MusicItem>, tagId: Long) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val favoriteIds = items.map { favoriteRepository.addFavorite(it) }
            val currentSets = favoriteIds.map { favoriteRepository.tagIdsOf(it).toSet() }
            val allHave = currentSets.isNotEmpty() && currentSets.all { tagId in it }
            favoriteIds.forEach { favoriteId ->
                val current = favoriteRepository.tagIdsOf(favoriteId).toSet()
                val next = if (allHave) current - tagId else current + tagId
                favoriteRepository.setTags(favoriteId, next.toList())
            }
        }
    }

    /** 新建标签并给所有选中歌曲打上。 */
    fun createTagForItems(name: String, items: List<MusicItem>) {
        if (name.isBlank() || items.isEmpty()) return
        viewModelScope.launch {
            val tagId = favoriteRepository.createTag(name)
            val favoriteIds = items.map { favoriteRepository.addFavorite(it) }
            favoriteIds.forEach { favoriteId ->
                val current = favoriteRepository.tagIdsOf(favoriteId).toSet()
                favoriteRepository.setTags(favoriteId, (current + tagId).toList())
            }
        }
    }

    /** 批量场景下：哪些标签是「所有选中歌曲都带」的。 */
    suspend fun tagsCommonToAll(items: List<MusicItem>): Set<Long> {
        if (items.isEmpty()) return emptySet()
        val favoriteIds = items.map { favoriteRepository.addFavorite(it) }
        val sets = favoriteIds.map { favoriteRepository.tagIdsOf(it).toSet() }
        if (sets.isEmpty()) return emptySet()
        return sets.reduce { acc, s -> acc.intersect(s) }
    }
}
