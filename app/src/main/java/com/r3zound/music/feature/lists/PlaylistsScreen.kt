package com.r3zound.music.feature.lists

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.runtime.LaunchedEffect
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
import com.r3zound.music.feature.favorites.FavoritesTab
import com.r3zound.music.feature.favorites.FavoritesUiState
import com.r3zound.music.feature.favorites.FavoritesViewModel
import com.r3zound.music.feature.lists.ConfirmDeleteDialog
import com.r3zound.music.ui.actions.SongActionsViewModel
import com.r3zound.music.ui.components.CoverImage
import com.r3zound.music.ui.components.EmptyState
import com.r3zound.music.ui.components.FavTab
import com.r3zound.music.ui.components.PlayingLocator
import com.r3zound.music.ui.components.PlaylistPickerDialog
import com.r3zound.music.ui.components.PrimaryButton
import com.r3zound.music.ui.components.SearchField
import com.r3zound.music.ui.components.SecondaryButton
import com.r3zound.music.ui.components.SongActionsDialog
import com.r3zound.music.ui.components.TagPickerDialog
import com.r3zound.music.ui.components.TrackRow
import com.r3zound.music.ui.nav.Routes
import com.r3zound.music.ui.nav.rememberSearchNavState
import com.r3zound.music.ui.theme.MuPalette
import com.r3zound.music.ui.theme.tagColor

/**
 * 播放列表页：我的歌单 + 最近播放。
 *
 * 底栏的「播放列表」入口。歌单用来把歌收在一起随时播，
 * 最近播放是听过的歌（按时间倒序）——两个都归在「按内容组织」这一维度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    navController: NavHostController,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchNavState = rememberSearchNavState()

    // 播放列表页默认停在「我的歌单」
    LaunchedEffect(Unit) { viewModel.selectTab(FavoritesTab.PLAYLISTS) }

    var showCreatePlaylist by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PlaylistEntity?>(null) }
    var descTarget by remember { mutableStateOf<PlaylistEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<PlaylistEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "播放列表",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            if (state.tab == FavoritesTab.PLAYLISTS) {
                NewPlaylistFab(onClick = { showCreatePlaylist = true })
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ListsTabRow(
                tab = state.tab,
                playlistCount = state.playlists.size,
                historyCount = state.history.size,
                onSelect = viewModel::selectTab,
            )
            when (state.tab) {
                FavoritesTab.PLAYLISTS -> PlaylistsTab(
                    state = state,
                    onOpen = { navController.navigate(Routes.playlistDetail(it.id)) },
                    onCreate = { showCreatePlaylist = true },
                    onRename = { renameTarget = it },
                    onEditDescription = { descTarget = it },
                    onDelete = { deleteTarget = it },
                )

                FavoritesTab.HISTORY -> HistoryTab(
                    state = state,
                    onPlay = { item ->
                        viewModel.play(item)
                        navController.navigate(Routes.PLAYER)
                    },
                    onDownload = { viewModel.download(listOf(it)) },
                    onArtistClick = { artist ->
                        searchNavState.request(artist)
                        navController.navigate(Routes.HOME)
                    },
                )

                else -> Unit
            }
        }
    }

    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylist = false },
            onConfirm = { name, description, coverVariant ->
                viewModel.createPlaylist(name, description, coverVariant)
                showCreatePlaylist = false
            },
        )
    }

    renameTarget?.let { playlist ->
        RenamePlaylistDialog(
            initialName = playlist.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.renamePlaylist(playlist.id, name)
                renameTarget = null
            },
        )
    }

    descTarget?.let { playlist ->
        EditDescriptionDialog(
            initialDescription = playlist.description.orEmpty(),
            onDismiss = { descTarget = null },
            onConfirm = { description ->
                viewModel.updatePlaylistDescription(playlist.id, description)
                descTarget = null
            },
        )
    }

    deleteTarget?.let { playlist ->
        ConfirmDeleteDialog(
            title = "删除歌单",
            message = "删除后歌单内的曲目会回到「全部歌曲」，歌单本身不可恢复。",
            confirmText = "删除",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.deletePlaylist(playlist.id)
                deleteTarget = null
            },
        )
    }
}

/** 播放列表页的 tab 行。 */
@Composable
private fun ListsTabRow(
    tab: FavoritesTab,
    playlistCount: Int,
    historyCount: Int,
    onSelect: (FavoritesTab) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        FavTab(
            label = "我的歌单 $playlistCount",
            selected = tab == FavoritesTab.PLAYLISTS,
            onClick = { onSelect(FavoritesTab.PLAYLISTS) },
        )
        FavTab(
            label = "最近播放 $historyCount",
            selected = tab == FavoritesTab.HISTORY,
            onClick = { onSelect(FavoritesTab.HISTORY) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistsTab(
    state: FavoritesUiState,
    onOpen: (PlaylistEntity) -> Unit,
    onCreate: () -> Unit,
    onRename: (PlaylistEntity) -> Unit,
    onEditDescription: (PlaylistEntity) -> Unit,
    onDelete: (PlaylistEntity) -> Unit,
) {
    if (state.playlists.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.MusicNote,
            title = "还没有歌单",
            message = "新建一个歌单，把喜欢的歌收在一起",
            action = { PrimaryButton("新建歌单", onClick = onCreate) },
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 86.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.playlists, key = { it.id }) { playlist ->
            PlaylistCard(
                playlist = playlist,
                songCount = state.playlistCounts[playlist.id] ?: 0,
                onOpen = { onOpen(playlist) },
                onRename = { onRename(playlist) },
                onEditDescription = { onEditDescription(playlist) },
                onDelete = { onDelete(playlist) },
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistCard(
    playlist: PlaylistEntity,
    songCount: Int,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onEditDescription: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember(playlist.id) { mutableStateOf(false) }
    // 内置「默认收藏」不可改名/删除，因此不提供长按菜单
    val menuEnabled = !playlist.isDefault
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { if (menuEnabled) menuOpen = true },
            )
    ) {
        Box {
            // 方形封面：占满网格单元宽度
            BoxWithConstraints {
                CoverImage(
                    cover = null,
                    size = maxWidth,
                    radius = 13.dp,
                    variant = playlist.coverVariant,
                    title = playlist.name,
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
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("修改描述") },
                    onClick = {
                        menuOpen = false
                        onEditDescription()
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = playlist.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (playlist.isDefault) {
                Spacer(Modifier.width(5.dp))
                // 内置歌单标记
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "默认",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$songCount 首 · ${playlistSizeLabel(songCount)}",
            fontSize = 10.5.sp,
            color = MuPalette.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/** 歌单规模（小/中/大），用于卡片副标题。 */
internal fun playlistSizeLabel(songCount: Int): String = when {
    songCount <= 4 -> "小"
    songCount <= 12 -> "中"
    else -> "大"
}


@Composable
internal fun HistoryTab(
    state: FavoritesUiState,
    onPlay: (MusicItem) -> Unit,
    onDownload: (MusicItem) -> Unit,
    onArtistClick: (String) -> Unit,
) {
    if (state.history.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.History,
            title = "还没有播放记录",
            message = "听过的歌会按时间倒序出现在这里",
        )
        return
    }

    val listState = rememberLazyListState()
    val currentKey = state.currentSongKey
    val locatedIndex = remember(state.history, currentKey) {
        state.history.indexOfFirst { "${it.item.provider}:${it.item.id}" == currentKey }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        ) {
            items(state.history, key = { "${it.item.provider}:${it.item.id}" }) { song ->
                TrackRow(
                    item = song.item,
                    providerName = song.item.provider,
                    onClick = { onPlay(song.item) },
                    onDownload = { onDownload(song.item) },
                    onArtistClick = { onArtistClick(it) },
                    isPlaying = currentKey == "${song.item.provider}:${song.item.id}",
                    trailing = {
                        Text(
                            text = relativeTime(song.playedAt),
                            fontSize = 10.sp,
                            color = MuPalette.ink3,
                        )
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


/** 时间戳 → 「刚刚 / 5 分钟前 / 昨天 / 3-12」。 */
internal fun relativeTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        diff < 172_800_000L -> "昨天"
        else -> {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = ts
            "${cal.get(java.util.Calendar.MONTH) + 1}-${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
        }
    }
}

@Composable
internal fun NewPlaylistFab(onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "新建歌单",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}


@Composable
internal fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?, coverVariant: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var coverVariant by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建歌单") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("歌单名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "封面配色",
                    fontSize = 11.sp,
                    color = MuPalette.ink3,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (0..3).forEach { variant ->
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (variant == coverVariant) 2.dp else 1.dp,
                                    color = if (variant == coverVariant) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable { coverVariant = variant },
                            contentAlignment = Alignment.Center,
                        ) {
                            CoverImage(
                                cover = null,
                                size = 38.dp,
                                radius = 10.dp,
                                variant = variant,
                                title = "",
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, description, coverVariant) },
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}


@Composable
internal fun RenamePlaylistDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                onClick = { onConfirm(name) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}


@Composable
internal fun EditDescriptionDialog(
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改描述") },
        text = {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("描述") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(description) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}


@Composable
internal fun ConfirmDeleteDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, fontSize = 13.sp, color = MuPalette.ink2) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

