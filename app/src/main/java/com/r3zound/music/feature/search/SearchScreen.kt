package com.r3zound.music.feature.search

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.r3zound.music.core.model.ChannelHealth
import com.r3zound.music.core.model.ChannelInfo
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.data.local.PlaylistEntity
import com.r3zound.music.data.remote.ProviderRegistry
import com.r3zound.music.ui.actions.SongActionsViewModel
import com.r3zound.music.ui.components.CheckBox
import com.r3zound.music.ui.components.PlayingLocator
import com.r3zound.music.ui.components.CoverImage
import com.r3zound.music.ui.components.EmptyState
import com.r3zound.music.ui.components.IconAction
import com.r3zound.music.ui.components.PlaylistOption
import com.r3zound.music.ui.components.PlaylistPickerDialog
import com.r3zound.music.ui.components.PrimaryButton
import com.r3zound.music.ui.components.SearchField
import com.r3zound.music.ui.components.SectionHeader
import com.r3zound.music.ui.components.TagPickerDialog
import com.r3zound.music.ui.components.TrackRow
import com.r3zound.music.ui.nav.Routes
import com.r3zound.music.ui.theme.MuPalette
import kotlinx.coroutines.launch

/** 热门歌手快捷入口（空态使用）。 */
private val HOT_ARTISTS = listOf("周杰伦", "孙燕姿", "林俊杰", "陈奕迅")

/**
 * 搜索页。
 *
 * 布局自上而下：标题栏 → 搜索框 → 搜索历史 → 渠道 chip → 分段控件 → 结果区。
 * 结果区在「按渠道分组」与「合并去重」之间切换；
 * 搜索过程中每个渠道独立渲染骨架，哪个渠道先回来就先显示哪个渠道的结果。
 */
@Composable
fun SearchScreen(
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel(),
    songActions: SongActionsViewModel = hiltViewModel(),
) {
    SearchContent(
        navController = navController,
        viewModel = viewModel,
        songActions = songActions,
    )
}

/**
 * 搜索内容（可嵌入）。
 *
 * 搜索是**首页的一个子版块**，没有独立路由了，所以这里做成一个能塞进
 * 任意容器的 composable。
 */
@Composable
fun SearchContent(
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel(),
    songActions: SongActionsViewModel = hiltViewModel(),
    /** 首页搜索子版块里不需要标题（外层已经有 tab 栏了）。 */
    showTitle: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val tagOptions by songActions.tagOptions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showPlaylistPicker by remember { mutableStateOf(false) }
    /** 批量打标签的弹窗 */
    var showBatchTagPicker by remember { mutableStateOf(false) }
    /** 所有选中歌曲都带的标签（批量勾选态） */
    var batchCommonTags by remember { mutableStateOf<Set<Long>>(emptySet()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (showTitle) {
            Text(
                text = "搜索",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
            )
        }

        SearchField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = "搜索歌曲、歌手",
            modifier = Modifier.padding(horizontal = 14.dp),
            onSearch = { viewModel.submitSearch() },
            trailing = {
                if (state.query.isNotEmpty()) {
                    IconAction(
                        icon = Icons.Filled.Close,
                        tint = MuPalette.ink3,
                        onClick = { viewModel.onQueryChange("") },
                    )
                }
            },
        )

        // 搜索历史：输入框为空时展示最近 10 条，点选即搜
        if (state.query.isBlank() && state.history.isNotEmpty()) {
            SearchHistoryRow(
                history = state.history,
                onPick = { viewModel.searchKeyword(it) },
            )
        }

        SegmentedSwitch(
            mode = state.groupMode,
            onChange = viewModel::setGroupMode,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )

        if (state.networkUnavailable) {
            NetworkBanner(modifier = Modifier.padding(horizontal = 14.dp))
        }

        Box(Modifier.weight(1f)) {
            when {
                !state.hasSearched -> HotArtistEmptyState(onPick = { viewModel.searchKeyword(it) })

                state.allFailed -> ErrorEmptyState(
                    message = state.results.values.firstNotNullOfOrNull { it.error } ?: "所有渠道均无响应",
                    onRetry = viewModel::retry,
                )

                else -> SearchResultList(
                    state = state,
                    viewModel = viewModel,
                    onRowClick = { item ->
                        if (viewModel.onRowClick(item)) navController.navigate(Routes.PLAYER)
                    },
                    onPlay = { item ->
                        viewModel.play(item)
                        navController.navigate(Routes.PLAYER)
                    },
                )
            }
        }

        // 多选模式底部批量操作条
        if (state.selectionMode) {
            BatchActionBar(
                count = state.selectedItems.size,
                onSelectAll = viewModel::selectAll,
                onDownload = viewModel::downloadSelected,
                onAddToPlaylist = { showPlaylistPicker = true },
                onTag = {
                    val items = state.selectedItems
                    if (items.isNotEmpty()) {
                        scope.launch {
                            batchCommonTags = songActions.tagsCommonToAll(items)
                            showBatchTagPicker = true
                        }
                    }
                },
                onClose = viewModel::exitSelection,
            )
        }
    }


    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            playlists = playlists.map { PlaylistOption(id = it.id, name = it.name) },
            onPick = { option ->
                viewModel.addSelectedToPlaylist(option.id)
                showPlaylistPicker = false
            },
            onCreate = { name ->
                viewModel.createPlaylistAndAdd(name)
                showPlaylistPicker = false
            },
            onDismiss = { showPlaylistPicker = false },
        )
    }

    if (showBatchTagPicker) {
        val items = state.selectedItems
        TagPickerDialog(
            tags = tagOptions,
            selectedTagIds = batchCommonTags,
            onDismiss = { showBatchTagPicker = false },
            onToggle = { tag ->
                songActions.toggleTagForItems(items, tag.id)
                batchCommonTags = if (tag.id in batchCommonTags) {
                    batchCommonTags - tag.id
                } else {
                    batchCommonTags + tag.id
                }
            },
            onCreate = { name -> songActions.createTagForItems(name, items) },
        )
    }
}

/** 分段控件：按渠道分组 / 合并去重。 */
@Composable
private fun SegmentedSwitch(
    mode: SearchGroupMode,
    onChange: (SearchGroupMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(MuPalette.surface2)
            .padding(3.dp),
    ) {
        SegmentCell("按渠道分组", mode == SearchGroupMode.BY_PROVIDER) {
            onChange(SearchGroupMode.BY_PROVIDER)
        }
        SegmentCell("合并去重", mode == SearchGroupMode.MERGED) {
            onChange(SearchGroupMode.MERGED)
        }
    }
}

@Composable
private fun RowScope.SegmentCell(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else MuPalette.ink2,
        )
    }
}

/** 网络异常横幅。 */
@Composable
private fun NetworkBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = "网络不可用",
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 搜索历史横排。 */
@Composable
private fun SearchHistoryRow(
    history: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = MuPalette.ink3,
            modifier = Modifier.size(13.dp),
        )
        history.forEach { keyword ->
            SmallChip(text = keyword, onClick = { onPick(keyword) })
        }
    }
}

/**
 * 结果区：分组 / 合并两种模式。
 *
 * 右下角浮着「正在播放」定位按钮（准星），把当前播的那首歌定位到屏幕里。
 */
@Composable
private fun SearchResultList(
    state: SearchUiState,
    viewModel: SearchViewModel,
    onRowClick: (MusicItem) -> Unit,
    onPlay: (MusicItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentKey = state.currentSongKey

    // 当前播放项在列表里的位置（分组/合并两种结构的下标算法不同）
    val locatedIndex = remember(state.groupMode, state.activeProviders, state.results, currentKey) {
        locateIndex(state, currentKey)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
        when (state.groupMode) {
            SearchGroupMode.BY_PROVIDER -> {
                for (provider in state.activeProviders) {
                    val result = state.results[provider]
                    item(key = "header-$provider") {
                        SectionHeader(
                            title = ProviderRegistry.name(provider),
                            trailing = result?.let { "${it.items.size} 条" } ?: "搜索中…",
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }
                    when {
                        // 该渠道还没回来：单独占位，不阻塞其它渠道
                        result == null -> item(key = "skeleton-$provider") {
                            ChannelSkeleton(modifier = Modifier.padding(horizontal = 14.dp))
                        }

                        // 无结果：保留分组，用灰字说明
                        result.items.isEmpty() -> item(key = "empty-$provider") {
                            Text(
                                text = result.error ?: "该渠道无结果",
                                color = MuPalette.ink3,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                            )
                        }

                        else -> items(
                            result.items,
                            key = { "${it.provider}:${it.id}" },
                        ) { item ->
                            SearchTrackRow(
                                item = item,
                                providerName = ProviderRegistry.name(provider),
                                state = state,
                                viewModel = viewModel,
                                onRowClick = { onRowClick(item) },
                                onPlay = { onPlay(item) },
                                onArtistClick = { viewModel.searchKeyword(it) },
                                isPlaying = state.currentSongKey == "${item.provider}:${item.id}",
                            )
                        }
                    }
                }
            }

            SearchGroupMode.MERGED -> {
                item(key = "merged-header") {
                    SectionHeader(
                        title = "合并结果",
                        trailing = "${state.mergedItems.size} 条",
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
                if (state.mergedItems.isEmpty() && state.searching) {
                    items(3, key = { "merged-skeleton-$it" }) {
                        ChannelSkeleton(modifier = Modifier.padding(horizontal = 14.dp))
                    }
                }
                items(state.mergedItems, key = { "${it.provider}:${it.id}" }) { item ->
                    SearchTrackRow(
                        item = item,
                        providerName = ProviderRegistry.name(item.provider),
                        state = state,
                        viewModel = viewModel,
                        onRowClick = { onRowClick(item) },
                        onPlay = { onPlay(item) },
                        onArtistClick = { viewModel.searchKeyword(it) },
                        isPlaying = state.currentSongKey == "${item.provider}:${item.id}",
                    )
                }
            }
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

/**
 * 算出当前播放项在结果列表里的下标；不在列表里返回 -1。
 *
 * 分组模式下每组前面还有一个标题项，加载中的渠道还会多一个占位项，
 * 所以下标必须按实际渲染结构累加，不能直接用曲目的序号。
 */
private fun locateIndex(state: SearchUiState, currentKey: String?): Int {
    if (currentKey.isNullOrEmpty()) return -1
    return when (state.groupMode) {
        SearchGroupMode.MERGED -> {
            val i = state.mergedItems.indexOfFirst { "${it.provider}:${it.id}" == currentKey }
            // 合并模式顶部有一个「合并结果」标题项
            if (i >= 0) i + 1 else -1
        }

        SearchGroupMode.BY_PROVIDER -> {
            var index = 0
            for (provider in state.activeProviders) {
                val result = state.results[provider]
                index += 1 // 分组标题
                when {
                    result == null -> index += 1 // 骨架占位
                    result.items.isEmpty() -> index += 1 // 空结果提示
                    else -> {
                        val i = result.items.indexOfFirst { "${it.provider}:${it.id}" == currentKey }
                        if (i >= 0) return index + i
                        index += result.items.size
                    }
                }
            }
            -1
        }
    }
}

/**
 * 单行结果。
 *
 * 长按进入多选模式（多选模式下左侧出现勾选框）；
 * 右侧固定播放 / 收藏 / 下载三个动作，长按下载图标进入多选模式。
 */
@Composable
private fun SearchTrackRow(
    item: MusicItem,
    providerName: String,
    state: SearchUiState,
    viewModel: SearchViewModel,
    onRowClick: () -> Unit,
    onPlay: () -> Unit,
    /** 点击歌手名：直接按该歌手重新搜索。 */
    onArtistClick: ((String) -> Unit)? = null,
    /** 是否是当前正在播放的曲目。 */
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val selected = state.selectedItems.any { it.provider == item.provider && it.id == item.id }
    val isFavorite = viewModel.isFavorite(item)
    val option = item.qualityOptions.firstOrNull { it.value == state.selectedLevel }
        ?: item.qualityOptions.firstOrNull()
    val format = option?.format?.takeIf { it.isNotBlank() }?.uppercase()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(item.provider, item.id) {
                detectTapGestures(onLongPress = { viewModel.enterSelection(item) })
            },
    ) {
        TrackRow(
            item = item,
            modifier = Modifier.padding(horizontal = 14.dp),
            isFavorite = isFavorite,
            providerName = providerName,
            qualityLabel = format,
            qualityHighlight = format == "FLAC",
            onClick = onRowClick,
            onFavorite = { viewModel.toggleFavorite(item) },
            onDownload = { viewModel.onDownloadIcon(item) },
            onArtistClick = onArtistClick,
            isPlaying = isPlaying,
            leading = if (state.selectionMode) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CheckBox(checked = selected, onToggle = onRowClick)
                        Spacer(Modifier.width(8.dp))
                        CoverImage(item.cover, 42.dp, title = item.title)
                    }
                }
            } else {
                null
            },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconAction(
                        icon = Icons.Filled.PlayArrow,
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = onPlay,
                    )
                    IconAction(
                        icon = if (isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MuPalette.ink3,
                        onClick = { viewModel.toggleFavorite(item) },
                    )
                    Box(
                        Modifier.pointerInput(item.provider, item.id) {
                            detectTapGestures(onLongPress = { viewModel.enterSelection(item) })
                        },
                    ) {
                        IconAction(
                            icon = Icons.Outlined.Download,
                            tint = MuPalette.ink3,
                            onClick = { viewModel.onDownloadIcon(item) },
                        )
                    }
                }
            },
        )
    }
}

/** 渠道骨架屏：只占该渠道自己的位置。 */
@Composable
private fun ChannelSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    )
    val block = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        repeat(3) {
            Row(
                modifier = Modifier.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(block),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Box(
                        Modifier
                            .width(140.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(block),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .width(92.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(block),
                    )
                }
            }
        }
    }
}

/** 空态：热门歌手快捷入口。 */
@Composable
private fun HotArtistEmptyState(onPick: (String) -> Unit) {
    EmptyState(
        icon = Icons.Outlined.Search,
        title = "搜索歌曲、歌手",
        message = "输入关键字，或直接点下面的热门歌手",
        action = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("热门歌手", fontSize = 11.sp, color = MuPalette.ink3)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HOT_ARTISTS.forEach { name ->
                        SmallChip(text = name, onClick = { onPick(name) })
                    }
                }
            }
        },
    )
}

/** 错误空态：全部渠道失败。 */
@Composable
private fun ErrorEmptyState(message: String, onRetry: () -> Unit) {
    EmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = "搜索失败",
        message = message,
        action = {
            PrimaryButton(text = "重试", onClick = onRetry, icon = Icons.Filled.Refresh)
        },
    )
}

/** 多选模式底部批量操作条。 */
@Composable
private fun BatchActionBar(
    count: Int,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onTag: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(icon = Icons.Filled.Close, tint = Color.White, onClick = onClose)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "已选 $count 首",
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        BarAction(text = "全选", onClick = onSelectAll)
        BarAction(text = "标签", onClick = onTag)
        BarAction(text = "加入歌单", onClick = onAddToPlaylist)
        BarAction(text = "下载", onClick = onDownload)
    }
}

@Composable
private fun BarAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

/** 小圆角 chip（历史 / 热门歌手）。 */
@Composable
private fun SmallChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 11.5.sp,
        color = MuPalette.ink2,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MuPalette.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** 渠道筛选对话框：勾选参与搜索的渠道。 */
@Composable
private fun FilterDialog(
    channels: List<ChannelInfo>,
    selected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var local by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择渠道", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { local = channels.map { it.id }.toSet() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckBox(
                        checked = local.size == channels.size && channels.isNotEmpty(),
                        onToggle = { local = channels.map { it.id }.toSet() },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("全选", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                channels.forEach { channel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                local = if (channel.id in local) local - channel.id else local + channel.id
                            }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckBox(
                            checked = channel.id in local,
                            onToggle = {
                                local = if (channel.id in local) local - channel.id else local + channel.id
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(channel.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        HealthDot(channel.health)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(local) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 渠道健康指示点。 */
@Composable
private fun HealthDot(health: ChannelHealth) {
    if (health == ChannelHealth.UNKNOWN) return
    val color = when (health) {
        ChannelHealth.OK -> MuPalette.success
        ChannelHealth.PARTIAL -> MuPalette.qualityAmber
        else -> MuPalette.ink3
    }
    Box(
        Modifier
            .size(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

/** 新建歌单对话框。 */
@Composable
private fun NewPlaylistDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建歌单", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("歌单名称", fontSize = 13.sp, color = MuPalette.ink3) },
                shape = RoundedCornerShape(12.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}