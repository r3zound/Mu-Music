@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.r3zound.music.feature.favorites

import androidx.compose.runtime.LaunchedEffect
import com.r3zound.music.ui.nav.rememberSearchNavState
import com.r3zound.music.feature.lists.ConfirmDeleteDialog
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.data.local.PlaylistEntity
import com.r3zound.music.data.local.TagWithCount
import com.r3zound.music.ui.actions.SongActionsViewModel
import com.r3zound.music.ui.components.CoverImage
import com.r3zound.music.ui.components.PlayingLocator
import com.r3zound.music.ui.components.EmptyState
import com.r3zound.music.ui.components.FavTab
import com.r3zound.music.ui.components.PlaylistPickerDialog
import com.r3zound.music.ui.components.PrimaryButton
import com.r3zound.music.ui.components.SearchField
import com.r3zound.music.ui.components.SecondaryButton
import com.r3zound.music.ui.components.SongActionsDialog
import com.r3zound.music.ui.components.TagPickerDialog
import com.r3zound.music.ui.components.TrackRow
import com.r3zound.music.ui.nav.Routes
import com.r3zound.music.ui.theme.MuPalette
import com.r3zound.music.ui.theme.tagColor

/**
 * 收藏页：全部歌曲 / 标签 两个分页（歌单与最近播放在「播放列表」页）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    navController: NavHostController,
    viewModel: FavoritesViewModel = hiltViewModel(),
    songActions: SongActionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actionState by songActions.uiState.collectAsStateWithLifecycle()

    // 收藏页只有「全部歌曲 / 标签」两个子版块
    LaunchedEffect(Unit) { viewModel.selectTab(FavoritesTab.SONGS) }
    val searchNavState = rememberSearchNavState()
    val playlistOptions by songActions.playlistOptions.collectAsStateWithLifecycle()
    val tagOptions by songActions.tagOptions.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCreateTag by remember { mutableStateOf(false) }
    var deleteTagTarget by remember { mutableStateOf<TagWithCount?>(null) }

    // 长按歌曲弹出的操作面板。
    // actionTarget 要跨「操作面板 → 歌单选择器」两级弹窗保持，所以不能在切换时清空。
    var actionTarget by remember { mutableStateOf<MusicItem?>(null) }
    var actionFavoriteId by remember { mutableStateOf<Long?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "收藏",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "导出 JSON 即将上线", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = "导出收藏",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            FavoritesTabRow(
                tab = state.tab,
                songCount = state.songs.size,
                tagCount = state.tags.size,
                onSelect = viewModel::selectTab,
            )
            when (state.tab) {
                FavoritesTab.SONGS -> SongsTab(
                    state = state,
                    onQueryChange = viewModel::setQuery,
                    onDownload = { viewModel.download(listOf(it)) },
                    onRemove = viewModel::removeFavorite,
                    onLongPress = { song ->
                        actionTarget = song.item
                        actionFavoriteId = song.favoriteId
                        showActionSheet = true
                    },
                    onArtistClick = { artist ->
                        searchNavState.request(artist)
                        navController.navigate(Routes.HOME)
                    },
                )

                FavoritesTab.TAGS -> TagsTab(
                    state = state,
                    onCreate = { showCreateTag = true },
                    onToggle = viewModel::toggleTag,
                    onLongPress = { deleteTagTarget = it },
                    onDownload = { viewModel.download(listOf(it)) },
                    onRemove = viewModel::removeFavorite,
                    onSongLongPress = { song ->
                        actionTarget = song.item
                        actionFavoriteId = song.favoriteId
                        showActionSheet = true
                    },
                    onArtistClick = { artist ->
                        searchNavState.request(artist)
                        navController.navigate(Routes.HOME)
                    },
                )

                // 歌单与历史在「播放列表」页，这里用不到
                else -> Unit
            }
        }
    }

    // ── 弹窗 ─────────────────────────────────────────────────
    if (showCreateTag) {
        CreateTagDialog(
            onDismiss = { showCreateTag = false },
            onConfirm = { name, colorIndex ->
                viewModel.createTag(name, colorIndex)
                showCreateTag = false
            },
        )
    }

    deleteTagTarget?.let { tag ->
        ConfirmDeleteDialog(
            title = "删除标签",
            message = "删除「${tag.tag.name}」后，标签与歌曲的关联会一并解除。",
            confirmText = "删除",
            onDismiss = { deleteTagTarget = null },
            onConfirm = {
                viewModel.deleteTag(tag.tag.id)
                deleteTagTarget = null
            },
        )
    }

    // ── 长按歌曲：操作面板 / 歌单选择 / 标签选择 ──────────────
    if (showActionSheet) {
        actionTarget?.let { target ->
            SongActionsDialog(
                title = target.title,
                subtitle = target.artist,
                favoriteActionLabel = "取消收藏",
                onDismiss = {
                    showActionSheet = false
                    actionTarget = null
                    actionFavoriteId = null
                },
                onPlay = {
                    showActionSheet = false
                    viewModel.play(target)
                    navController.navigate(Routes.PLAYER)
                },
                onAddToPlaylist = {
                    // 保留 actionTarget，歌单选择器还要用它
                    showActionSheet = false
                    showPlaylistPicker = true
                },
                onEditTags = {
                    showActionSheet = false
                    actionFavoriteId?.let { songActions.openTagPickerForFavorite(it) }
                },
                onDownload = {
                    showActionSheet = false
                    viewModel.download(listOf(target))
                },
                onToggleFavorite = {
                    showActionSheet = false
                    actionFavoriteId?.let { viewModel.removeFavorite(it) }
                    actionTarget = null
                    actionFavoriteId = null
                },
            )
        }
    }

    if (showPlaylistPicker) {
        val target = actionTarget
        PlaylistPickerDialog(
            playlists = playlistOptions,
            onDismiss = {
                showPlaylistPicker = false
                actionTarget = null
            },
            onPick = { playlist ->
                showPlaylistPicker = false
                if (target != null) songActions.addToPlaylist(playlist.id, target)
                actionTarget = null
            },
            onCreate = { name ->
                showPlaylistPicker = false
                if (target != null) songActions.createPlaylistAndAdd(name, target)
                actionTarget = null
            },
        )
    }

    if (actionState.tagPickerTarget != null) {
        TagPickerDialog(
            tags = tagOptions,
            selectedTagIds = actionState.tagPickerSelected,
            onDismiss = { songActions.closeTagPicker() },
            onToggle = { songActions.toggleTag(it.id) },
            onCreate = { songActions.createTagAndApply(it) },
        )
    }
}

// ── 顶部分页行 ────────────────────────────────────────────────

/** 自定义分页行：选中项加粗 + 底部主色指示条。 */
@Composable
private fun FavoritesTabRow(
    tab: FavoritesTab,
    songCount: Int,
    tagCount: Int,
    onSelect: (FavoritesTab) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        FavTab(
            label = "全部歌曲 $songCount",
            selected = tab == FavoritesTab.SONGS,
            onClick = { onSelect(FavoritesTab.SONGS) },
        )
        FavTab(
            label = "标签 $tagCount",
            selected = tab == FavoritesTab.TAGS,
            onClick = { onSelect(FavoritesTab.TAGS) },
        )
    }
}

// ── 全部歌曲 ──────────────────────────────────────────────────

@Composable
private fun SongsTab(
    state: FavoritesUiState,
    onQueryChange: (String) -> Unit,
    onDownload: (MusicItem) -> Unit,
    onRemove: (Long) -> Unit,
    onLongPress: (FavoriteSong) -> Unit,
    /** 点击歌手名：跳到搜索页搜这个歌手。 */
    onArtistClick: (String) -> Unit,
) {
    val query = state.query.trim()
    // 本地过滤：歌名或歌手命中即可
    val visible = remember(state.songs, query) {
        if (query.isEmpty()) {
            state.songs
        } else {
            state.songs.filter {
                it.item.title.contains(query, ignoreCase = true) ||
                    it.item.artist.contains(query, ignoreCase = true)
            }
        }
    }

    val listState = rememberLazyListState()
    val currentKey = state.currentSongKey
    val locatedIndex = remember(visible, currentKey) {
        visible.indexOfFirst { "${it.item.provider}:${it.item.id}" == currentKey }
    }

    Column(Modifier.fillMaxSize()) {
        SearchField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = "搜索歌名或歌手",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
        if (visible.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.BookmarkBorder,
                title = if (query.isEmpty()) "还没有收藏任何歌曲" else "没有匹配的歌曲",
                message = if (query.isEmpty()) "在搜索结果里点收藏，就会出现在这里" else "换个关键词试试",
            )
            return
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            ) {
                items(visible, key = { it.favoriteId }) { song ->
                    TrackRow(
                        item = song.item,
                        isFavorite = true,
                        providerName = song.item.provider,
                        qualityLabel = song.item.qualityOptions.firstOrNull()?.label,
                        onFavorite = { onRemove(song.favoriteId) },
                        onDownload = { onDownload(song.item) },
                        onLongClick = { onLongPress(song) },
                        onArtistClick = { onArtistClick(it) },
                        isPlaying = currentKey == "${song.item.provider}:${song.item.id}",
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

// ── 标签 ──────────────────────────────────────────────────────

@Composable
private fun TagsTab(
    state: FavoritesUiState,
    onCreate: () -> Unit,
    onToggle: (Long) -> Unit,
    onLongPress: (TagWithCount) -> Unit,
    onDownload: (MusicItem) -> Unit,
    onRemove: (Long) -> Unit,
    onSongLongPress: (FavoriteSong) -> Unit,
    /** 点击歌手名：跳到搜索页搜这个歌手。 */
    onArtistClick: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryButton("新建标签", onClick = onCreate, icon = Icons.Filled.Add)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "共 ${state.tags.size} 个标签",
                fontSize = 11.sp,
                color = MuPalette.ink3,
            )
        }
        if (state.tags.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.BookmarkBorder,
                title = "还没有标签",
                message = "用标签把收藏的歌曲分组，比如「通勤」「睡前」",
                action = { PrimaryButton("新建标签", onClick = onCreate) },
            )
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(state.tags, key = { it.tag.id }) { tag ->
                TagCard(
                    tag = tag,
                    expanded = state.expandedTagId == tag.tag.id,
                    onClick = { onToggle(tag.tag.id) },
                    onLongClick = { onLongPress(tag) },
                )
            }
        }

        // 展开的标签下的歌曲
        val expanded = state.tags.firstOrNull { it.tag.id == state.expandedTagId }
        if (expanded != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(tagColor(expanded.tag.colorIndex))
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = expanded.tag.name,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${state.taggedSongs.size} 首",
                        fontSize = 10.5.sp,
                        color = MuPalette.ink3,
                    )
                }
                if (state.taggedSongs.isEmpty()) {
                    Text(
                        text = "这个标签下还没有歌曲",
                        fontSize = 11.5.sp,
                        color = MuPalette.ink3,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                    ) {
                        items(state.taggedSongs, key = { it.favoriteId }) { song ->
                            TrackRow(
                                item = song.item,
                                isFavorite = true,
                                providerName = song.item.provider,
                                qualityLabel = song.item.qualityOptions.firstOrNull()?.label,
                                onFavorite = { onRemove(song.favoriteId) },
                                onDownload = { onDownload(song.item) },
                                onLongClick = { onSongLongPress(song) },
                                onArtistClick = { onArtistClick(it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagCard(
    tag: TagWithCount,
    expanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(tagColor(tag.tag.colorIndex))
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = tag.tag.name,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${tag.songCount} 首",
                fontSize = 10.sp,
                color = MuPalette.ink3,
            )
        }
        if (expanded) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// ── 通用小部件 ────────────────────────────────────────────────

/** 右下角新建歌单按钮。 */
/** 小圆角标签。 */
@Composable
internal fun TagChip(text: String, colorIndex: Int, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = tagColor(colorIndex),
        modifier = modifier
            .background(tagColor(colorIndex).copy(alpha = 0.16f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

// ── 弹窗 ──────────────────────────────────────────────────────

@Composable
private fun CreateTagDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorIndex: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("标签名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "标签颜色",
                    fontSize = 11.sp,
                    color = MuPalette.ink3,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (0..4).forEach { index ->
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(tagColor(index))
                                .border(
                                    width = if (index == colorIndex) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { colorIndex = index },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (index == colorIndex) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, colorIndex) },
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ── 最近播放 ──────────────────────────────────────────────────

/**
 * 最近播放列表。
 *
 * 播放历史由 [com.r3zound.music.media.PlayerHolder] 在切歌时写入，
 * 同一首歌重复播放只更新时间戳。
 */
