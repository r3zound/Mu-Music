package com.r3zound.music.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.r3zound.music.data.local.SearchHistoryDao
import com.r3zound.music.data.prefs.SettingsStore
import com.r3zound.music.data.repo.FavoriteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页状态。
 *
 * @param recentKeywords 最近搜索过的关键词，点一下直接搜。
 */
data class HomeUiState(
    val recentKeywords: List<String> = emptyList(),
    /** 内置「默认收藏」歌单 id，用于首页快捷入口直达。 */
    val defaultPlaylistId: Long = 0L,
    /** 音源站地址；为空表示还没配置，首页要给引导。 */
    val baseUrl: String = "",
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao,
    private val favoriteRepository: FavoriteRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * 每个分类的关键词轮换下标。
     *
     * 音源站只有搜索接口，分类点进去是「关键词搜索」的结果，
     * 如果永远用同一个词，用户每次点进来看到的都是同一批歌。
     * 这里按点击次数轮换主关键词，让同一个分类每次进来都有变化。
     */
    private val rotation = mutableMapOf<String, Int>()

    init {
        viewModelScope.launch {
            searchHistoryDao.observeRecent().collect { list ->
                _uiState.value = _uiState.value.copy(
                    recentKeywords = list.map { it.keyword }.take(8),
                )
            }
        }
        // 确保内置「默认收藏」存在，拿到 id 供首页快捷入口使用
        viewModelScope.launch {
            val id = favoriteRepository.ensureDefaultPlaylist()
            _uiState.value = _uiState.value.copy(defaultPlaylistId = id)
        }
        // 音源站地址：开源版不内置任何地址，没填就在首页给引导
        viewModelScope.launch {
            settingsStore.baseUrl.collect { url ->
                _uiState.value = _uiState.value.copy(baseUrl = url)
            }
        }
    }

    /** 取该分类本轮应使用的关键词，并在内部推进轮换下标。 */
    fun keywordFor(category: HomeCategory): String {
        if (category.keywords.size <= 1) return category.primaryKeyword
        val current = rotation[category.id] ?: 0
        rotation[category.id] = (current + 1) % category.keywords.size
        return category.keywords[current % category.keywords.size]
    }
}
