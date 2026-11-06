package com.r3zound.music.feature.favorites

import com.r3zound.music.ui.nav.rememberSearchNavState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.data.local.PlaylistEntity
import com.r3zound.music.data.local.TagWithCount
import com.r3zound.music.data.repo.FavoriteRepository
import com.r3zound.music.download.DownloadRepository
import com.r3zound.music.media.PlayerHolder
import com.r3zound.music.ui.components.CheckBox
import com.r3zound.music.ui.components.CoverImage
import com.r3zound.music.ui.components.PlayingLocator
import com.r3zound.music.ui.components.songKeyOf
import com.r3zound.music.ui.components.EmptyState
import com.r3zound.music.ui.components.IconAction
import com.r3zound.music.ui.components.PrimaryButton
import com.r3zound.music.ui.components.SecondaryButton
import com.r3zound.music.ui.components.TrackRow
import com.r3zound.music.ui.nav.Routes
import com.r3zound.music.ui.theme.MuPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** 歌单内排序方式。 */
enum class PlaylistSort(val label: String) {
    /** 按加入时间（收藏时间）升序，跟歌单里的存放顺序一致。 */
    ADDED("按加入时间"),
    TITLE("按歌名"),
    ARTIST("按歌手"),
}

/** 歌单详情页状态。 */
data class PlaylistDetailUiState(
    val playlist: PlaylistEntity? = null,
    /** 全部歌单，「加入歌单」时选目标。 */
    val playlists: List<PlaylistEntity> = emptyList(),
    /** 当前排序下的曲目。 */
    val tracks: List<FavoriteSong> = emptyList(),
    /** 歌单内歌曲用到的标签。 */
    val songTags: List<TagWithCount> = emptyList(),
    val sortMode: PlaylistSort = PlaylistSort.ADDED,
    /** 多选模式下选中的收藏 id。 */
    val selection: Set<Long> = emptySet(),
    val selectionMode: Boolean = false,
    /** 当前正在播放的曲目键（provider:id），用于列表高亮与定位。 */
    val currentSongKey: String? = null,
)

/**
 * 歌单详情：曲目列表、下载/播放、排序、多选批量操作。
 */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val favoriteRepository: FavoriteRepository,
    private val downloadRepository: DownloadRepository,
    private val playerHolder: PlayerHolder,
) : ViewModel() {

    /** 歌单 id：优先取导航参数，页面回填作为兜底。 */
    private val _playlistId = MutableStateFlow(savedStateHandle.get<Long>("playlistId") ?: 0L)

    init {
        // 当前播放的曲目：列表高亮 + 准星定位都要用
        viewModelScope.launch {
            playerHolder.state.collect { playback ->
                val key = playback.current?.let { songKeyOf(it.provider, it.id) }
                _uiState.update { it.copy(currentSongKey = key) }
            }
        }
    }

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        // 歌单信息 + 曲目
        viewModelScope.launch {
            _playlistId.collectLatest { id ->
                if (id <= 0L) return@collectLatest
                combine(
                    favoriteRepository.observePlaylists(),
                    favoriteRepository.observePlaylistItems(id),
                ) { playlists, items -> playlists to items }
                    .collect { (playlists, items) ->
                        val playlist = playlists.firstOrNull { it.id == id }
                        val tracks = items.map { it.favorite.toSong() }
                        _uiState.update { state ->
                            state.copy(
                                playlist = playlist,
                                playlists = playlists,
                                tracks = applySort(tracks, state.sortMode),
                            )
                        }
                    }
            }
        }

        // 歌单内歌曲用到的标签
        viewModelScope.launch {
            _playlistId.collectLatest { id ->
                if (id <= 0L) return@collectLatest
                combine(
                    favoriteRepository.observePlaylistItems(id),
                    favoriteRepository.observeTags(),
                ) { items, tags -> items to tags }
                    .collect { (items, tags) ->
                        val tagById = tags.associate { it.tag.id to it.tag }
                        val counts = HashMap<Long, Int>()
                        items.map { it.favorite.id }.distinct().forEach { favoriteId ->
                            favoriteRepository.tagIdsOf(favoriteId).forEach { tagId ->
                                counts[tagId] = (counts[tagId] ?: 0) + 1
                            }
                        }
                        val songTags = counts.mapNotNull { (tagId, count) ->
                            tagById[tagId]?.let { TagWithCount(it, count) }
                        }.sortedBy { it.tag.name }
                        _uiState.update { it.copy(songTags = songTags) }
                    }
            }
        }
    }

    /** 页面回填歌单 id（导航参数缺失时的兜底）。 */
    fun attachPlaylistId(id: Long) {
        if (id > 0L && _playlistId.value == 0L) {
            _playlistId.value = id
        }
    }

    private val playlistId: Long get() = _playlistId.value

    // ── 排序 ──────────────────────────────────────────────────
    fun setSortMode(mode: PlaylistSort) {
        _uiState.update { it.copy(sortMode = mode, tracks = applySort(it.tracks, mode)) }
        if (mode == PlaylistSort.ADDED) {
            // 按加入时间排序时把顺序写回数据库，刷新后仍然一致
            viewModelScope.launch {
                val ordered = _uiState.value.tracks.map { it.favoriteId }
                favoriteRepository.reorder(playlistId, ordered)
            }
        }
    }

    private fun applySort(tracks: List<FavoriteSong>, mode: PlaylistSort): List<FavoriteSong> = when (mode) {
        PlaylistSort.ADDED -> tracks.sortedBy { it.createdAt }
        PlaylistSort.TITLE -> tracks.sortedBy { it.item.title.lowercase() }
        PlaylistSort.ARTIST -> tracks.sortedBy { it.item.artist.lowercase() }
    }

    // ── 多选 ──────────────────────────────────────────────────
    fun startSelection(favoriteId: Long) {
        _uiState.update { it.copy(selection = setOf(favoriteId), selectionMode = true) }
    }

    fun toggleSelection(favoriteId: Long) {
        _uiState.update { state ->
            val selection = state.selection.toMutableSet()
            if (!selection.add(favoriteId)) selection.remove(favoriteId)
            state.copy(selection = selection, selectionMode = selection.isNotEmpty())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selection = emptySet(), selectionMode = false) }
    }

    private fun selectedTracks(): List<FavoriteSong> {
        val state = _uiState.value
        return state.tracks.filter { it.favoriteId in state.selection }
    }

    // ── 操作 ──────────────────────────────────────────────────
    fun downloadAll() {
        download(_uiState.value.tracks.map { it.item })
    }

    fun downloadSelected() {
        download(selectedTracks().map { it.item })
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

    fun playAll() {
        playerHolder.playQueue(_uiState.value.tracks.map { it.item }, 0)
    }

    fun playAt(index: Int) {
        playerHolder.playQueue(_uiState.value.tracks.map { it.item }, index)
    }

    fun addSelectedToPlaylist(targetPlaylistId: Long) {
        val items = selectedTracks().map { it.item }
        if (items.isEmpty()) return
        viewModelScope.launch {
            favoriteRepository.addFavoritesToPlaylist(targetPlaylistId, items)
            clearSelection()
        }
    }

    fun removeSelected() {
        val ids = _uiState.value.selection.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { favoriteRepository.removeFromPlaylist(playlistId, it) }
            clearSelection()
        }
    }

    fun renamePlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { favoriteRepository.renamePlaylist(playlistId, name) }
    }

    fun deletePlaylist() {
        viewModelScope.launch { favoriteRepository.deletePlaylist(playlistId) }
    }

    private fun com.r3zound.music.data.local.FavoriteEntity.toSong(): FavoriteSong =
        toFavoriteSong(favoriteRepository::entityToItem)
}

/**
 * 歌单详情页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    navController: NavHostController,
    playlistId: Long,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    // 长按命中标记：用来挡掉长按抬手时 TrackRow 触发的那一次点击
    val longPressFlag = remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) { viewModel.attachPlaylistId(playlistId) }

    // 「正在播放」定位：当前播放项在歌单里的下标
    val searchNavState = rememberSearchNavState()
    val listState = rememberLazyListState()
    val locatedIndex = remember(state.tracks, state.currentSongKey) {
        val key = state.currentSongKey
        if (key == null) -1 else state.tracks.indexOfFirst { "${it.item.provider}:${it.item.id}" == key }
    }


    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.playlist?.name ?: "歌单详情",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "更多",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("重命名") },
                                onClick = {
                                    menuOpen = false
                                    showRename = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除歌单") },
                                onClick = {
                                    menuOpen = false
                                    showDelete = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            if (state.selectionMode) {
                SelectionBar(
                    count = state.selection.size,
                    onExit = viewModel::clearSelection,
                    onDownload = viewModel::downloadSelected,
                    onAddToPlaylist = { showAddToPlaylist = true },
                    onRemove = viewModel::removeSelected,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PlaylistHeader(state = state)
            ActionRow(
                onDownloadAll = viewModel::downloadAll,
                onPlayAll = {
                    viewModel.playAll()
                    navController.navigate(Routes.PLAYER)
                },
                onSortSelected = viewModel::setSortMode,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (state.tracks.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.MusicNote,
                    title = "歌单还是空的",
                    message = "去搜索页点收藏，把歌加进来",
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    itemsIndexed(
                        items = state.tracks,
                        key = { _, song -> song.favoriteId },
                    ) { index, song ->
                        val selected = song.favoriteId in state.selection
                        TrackRow(
                            item = song.item,
                            modifier = Modifier.trackRowGestures(
                                longPressFlag = longPressFlag,
                                onLongPress = { viewModel.startSelection(song.favoriteId) },
                            ),
                            isFavorite = true,
                            providerName = song.item.provider,
                            qualityLabel = song.item.qualityOptions.firstOrNull()?.label,
                            onClick = {
                                // 长按后抬手时 TrackRow 仍会触发一次点击，这里挡掉
                                if (longPressFlag.value) {
                                    longPressFlag.value = false
                                } else if (state.selectionMode) {
                                    viewModel.toggleSelection(song.favoriteId)
                                } else {
                                    viewModel.playAt(index)
                                    navController.navigate(Routes.PLAYER)
                                }
                            },
                            onArtistClick = { artist ->
                                searchNavState.request(artist)
                                navController.navigate(Routes.HOME)
                            },
                            isPlaying = state.currentSongKey == "${song.item.provider}:${song.item.id}",
                            trailing = {
                                IconAction(
                                    icon = Icons.Outlined.Download,
                                    tint = MuPalette.ink3,
                                    onClick = { viewModel.download(listOf(song.item)) },
                                )
                            },
                            leading = {
                                if (state.selectionMode) {
                                    CheckBox(
                                        checked = selected,
                                        onToggle = { viewModel.toggleSelection(song.favoriteId) },
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                } else {
                                    CoverImage(song.item.cover, 42.dp, title = song.item.title)
                                }
                            },
                        )
                    }
                }

                    PlayingLocator(
                        locatable = locatedIndex >= 0,
                        listState = listState,
                        onLocate = { listState.scrollToItem(locatedIndex) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }

    // ── 弹窗 ─────────────────────────────────────────────────
    if (showRename) {
        var name by remember(state.playlist?.name) { mutableStateOf(state.playlist?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("重命名歌单") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("歌单名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.renamePlaylist(name)
                        showRename = false
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("取消") } },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除歌单") },
            text = {
                Text(
                    text = "删除后歌单内的曲目会回到「全部歌曲」，歌单本身不可恢复。",
                    fontSize = 13.sp,
                    color = MuPalette.ink2,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist()
                        showDelete = false
                        navController.popBackStack()
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } },
        )
    }

    if (showAddToPlaylist) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylist = false },
            title = { Text("加入歌单") },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (state.playlists.isEmpty()) {
                        Text(
                            text = "还没有歌单，先去收藏页创建一个吧",
                            fontSize = 12.5.sp,
                            color = MuPalette.ink3,
                        )
                    }
                    state.playlists.forEach { playlist ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.addSelectedToPlaylist(playlist.id)
                                    showAddToPlaylist = false
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverImage(
                                cover = null,
                                size = 34.dp,
                                radius = 9.dp,
                                variant = playlist.coverVariant,
                                title = playlist.name,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = playlist.name,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToPlaylist = false }) { Text("关闭") }
            },
        )
    }
}

// ── 头部与操作行 ──────────────────────────────────────────────

@Composable
private fun PlaylistHeader(state: PlaylistDetailUiState) {
    val playlist = state.playlist
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            cover = null,
            size = 66.dp,
            radius = 13.dp,
            variant = playlist?.coverVariant ?: 0,
            title = playlist?.name,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist?.name ?: "歌单",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${state.tracks.size} 首 · 创建于 ${formatDate(playlist?.createdAt ?: 0L)}",
                fontSize = 11.sp,
                color = MuPalette.ink3,
            )
            if (state.songTags.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    state.songTags.take(4).forEach { tag ->
                        TagChip(tag.tag.name, tag.tag.colorIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    onDownloadAll: () -> Unit,
    onPlayAll: () -> Unit,
    onSortSelected: (PlaylistSort) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryButton(
            text = "下载全部",
            onClick = onDownloadAll,
            icon = Icons.Outlined.Download,
        )
        Spacer(Modifier.width(8.dp))
        SecondaryButton(
            text = "播放全部",
            onClick = onPlayAll,
            icon = Icons.Filled.PlayArrow,
        )
        Spacer(Modifier.width(8.dp))
        Box {
            SecondaryButton(
                text = "排序",
                onClick = { sortMenuOpen = true },
                icon = Icons.Filled.Sort,
            )
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
            ) {
                PlaylistSort.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = {
                            sortMenuOpen = false
                            onSortSelected(mode)
                        },
                    )
                }
            }
        }
    }
}

/** 多选模式下的批量操作条。 */
@Composable
private fun SelectionBar(
    count: Int,
    onExit: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "退出多选",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "已选 $count 首",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        BarAction("下载", onDownload)
        BarAction("加入歌单", onAddToPlaylist)
        BarAction("移除", onRemove)
    }
}

@Composable
private fun BarAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

// ── 手势 ──────────────────────────────────────────────────────

/**
 * 曲目行的手势：只负责识别长按，普通点击仍由 TrackRow 自带的 clickable 处理。
 *
 * TrackRow 内部的 clickable 会消费手势事件，所以这里自己挂一套 pointerInput 计时；
 * 长按命中时把 [longPressFlag] 置位，用来挡掉抬手时 TrackRow 触发的那一次点击。
 */
@Composable
private fun Modifier.trackRowGestures(
    longPressFlag: MutableState<Boolean>,
    onLongPress: () -> Unit,
): Modifier {
    val longPress by rememberUpdatedState(onLongPress)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            // 新的一次手势开始，清掉上一次的标记
            longPressFlag.value = false
            val down = awaitFirstDown(requireUnconsumed = false)
            val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForRelease(down.id)
            }
            if (released == null) {
                longPressFlag.value = true
                longPress.invoke()
                waitForRelease(down.id)
            }
        }
    }
}

/** 等待指定指针抬起；忽略消费状态。 */
private suspend fun AwaitPointerEventScope.waitForRelease(pointerId: PointerId): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return null
        if (!change.pressed) return change
    }
}

/** 格式化创建日期。 */
private fun formatDate(millis: Long): String {
    if (millis <= 0L) return "未知"
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
}
