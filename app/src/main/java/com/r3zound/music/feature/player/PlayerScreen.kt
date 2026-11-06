package com.r3zound.music.feature.player

import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Share
import android.content.Intent
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.os.Build
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.r3zound.music.core.util.SafeUrl
import com.r3zound.music.data.prefs.SettingsStore
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.core.model.QualityOption
import com.r3zound.music.data.remote.ProviderRegistry
import com.r3zound.music.media.LyricEntry
import com.r3zound.music.ui.components.CoverImage
import com.r3zound.music.ui.components.SleepTimerSheet
import com.r3zound.music.ui.components.LyricSettingsSheet
import com.r3zound.music.ui.components.formatRemaining
import com.r3zound.music.ui.nav.Routes
import com.r3zound.music.ui.theme.MuPalette
import kotlinx.coroutines.delay

/**
 * 全屏播放页。
 *
 * 结构（自上而下）：顶栏 → 封面 → 标题/副标题 → 进度条 → 控制行 → 快捷键 → 歌词区。
 * 上半部分可纵向滚动，歌词区是独立的 [LazyColumn]，跟随播放位置自动滚动并保持当前行居中。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(navController: NavHostController) {
    val vm: PlayerViewModel = hiltViewModel()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val shuffleOn by vm.shuffle.collectAsStateWithLifecycle()
    val repeatMode by vm.repeatMode.collectAsStateWithLifecycle()

    val current = playback.current
    val context = LocalContext.current
    val lyricState = rememberLazyListState()

    var positionMs by remember { mutableLongStateOf(0L) }
    var lyricIndex by remember { mutableIntStateOf(-1) }
    var showQuality by remember { mutableStateOf(false) }
    /** 播放页分页：false = 封面页，true = 歌词页。单击封面切到歌词页。 */
    var showLyricPage by remember { mutableStateOf(false) }
    /** 睡眠定时器面板。 */
    var showSleepTimer by remember { mutableStateOf(false) }
    /** 歌词设置面板。 */
    var showLyricSettings by remember { mutableStateOf(false) }
    val sleepTimer by vm.sleepTimer.collectAsStateWithLifecycle()
    val lyricFontSize by vm.lyricFontSize.collectAsStateWithLifecycle()
    val lyricOffset by vm.lyricOffset.collectAsStateWithLifecycle()

    // 总长：优先用播放器上报的值，拿不到时退回搜索结果里的时长字符串
    val durationMs = remember(playback.durationMs, current?.id) {
        if (playback.durationMs > 0L) playback.durationMs else parseDuration(current?.duration)
    }

    // 播放位置轮询：驱动进度条与歌词高亮
    LaunchedEffect(Unit) {
        while (true) {
            positionMs = vm.currentPositionMs()
            lyricIndex = vm.lyricIndexAt(positionMs)
            delay(300)
        }
    }

    // 进入页面 / 切歌：同步收藏状态与音质，并重新拉歌词
    LaunchedEffect(current?.provider, current?.id) {
        vm.syncItem(current)
        current?.let { vm.loadLyrics(it) }
    }

    // 歌词跟随播放位置滚动，当前行保持居中
    LaunchedEffect(lyricIndex, playback.lyrics.size) {
        if (lyricIndex >= 0) lyricState.centerItem(lyricIndex)
    }

    // 轻提示自动消失
    LaunchedEffect(ui.message) {
        if (ui.message != null) {
            delay(2200)
            vm.consumeMessage()
        }
    }

    // 播放页固定用深色配色：背景是封面模糊图，永远是暗的，
    // 所以文字一律走浅色，跟 APP 的浅色/深色主题无关。
    val playerScheme = MaterialTheme.colorScheme.copy(
        background = Color.Transparent,
        onBackground = Color.White,
        surface = Color.Transparent,
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.72f),
        surfaceContainerHigh = Color.White.copy(alpha = 0.10f),
    )

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        // 封面模糊背景：当前歌曲的封面铺满全屏做模糊，底下垫纯黑。
        // 这是「高级感」的最大来源，同一首歌有背景和没背景是两个产品。
        BlurredCoverBackground(current?.cover)

        MaterialTheme(colorScheme = playerScheme) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "正在播放",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = { shareCurrent(context, current) }) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = "分享",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { navController.navigate(Routes.QUEUE) }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "播放队列",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // 内容区：封面页 / 歌词页 分页。播放控制固定在下方，两页都能操作。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (showLyricPage) {
                    Column(Modifier.fillMaxSize()) {
                        // 歌词页顶部：缩略封面 + 歌名，点它也能切回封面页
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clickable { showLyricPage = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverImage(
                                cover = current?.cover,
                                size = 44.dp,
                                radius = 8.dp,
                                title = current?.title,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = current?.title ?: "未在播放",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = subtitleOf(current),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { showLyricSettings = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = "歌词设置",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                        LyricsSection(
                            lyrics = playback.lyrics,
                            loading = playback.lyricsLoading,
                            lyricIndex = lyricIndex,
                            listState = lyricState,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showLyricPage = false },
                            // 点歌词跳到那一句继续播（叠加该歌的时间偏移）
                            onLineClick = { entry -> vm.seekTo(entry.timeMs + lyricOffset) },
                            fontSize = lyricFontSize,
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showLyricPage = true },
                    ) {
                        CoverImage(
                            cover = current?.cover,
                            size = 208.dp,
                            radius = 16.dp,
                            title = current?.title,
                            onClick = { showLyricPage = true },
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = current?.title ?: "未在播放",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subtitleOf(current),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "轻触封面查看歌词",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f).copy(alpha = 0.75f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .padding(horizontal = 22.dp)
                    .navigationBarsPadding(),
            ) {
                // 进度条 + 左右时间
                SeekBar(
                    progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                    onSeek = { fraction ->
                        if (durationMs > 0) vm.seekTo((durationMs * fraction).toLong())
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(positionMs),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (durationMs > 0) formatTime(durationMs) else "--:--",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                    )
                }

                Spacer(Modifier.height(6.dp))

                // 控制行：随机 / 上一首 / 播放暂停 / 下一首 / 循环
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlIcon(
                        icon = if (shuffleOn) Icons.Filled.Shuffle else Icons.Outlined.Shuffle,
                        tint = if (shuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { vm.toggleShuffle() },
                    )
                    ControlIcon(
                        icon = Icons.Filled.SkipPrevious,
                        tint = MaterialTheme.colorScheme.onSurface,
                        size = 26.dp,
                        onClick = { vm.previous() },
                    )
                    PlayPauseButton(
                        isPlaying = playback.isPlaying,
                        onClick = { vm.togglePlayPause() },
                    )
                    ControlIcon(
                        icon = Icons.Filled.SkipNext,
                        tint = MaterialTheme.colorScheme.onSurface,
                        size = 26.dp,
                        onClick = { vm.next() },
                    )
                    ControlIcon(
                        icon = repeatIcon(repeatMode),
                        tint = if (repeatMode == PlayerViewModel.REPEAT_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        onClick = { vm.cycleRepeat() },
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 快捷键：收藏 / 下载 / 队列 / 音质
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    QuickAction(
                        icon = if (ui.favorited) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        label = "收藏",
                        highlighted = ui.favorited,
                        onClick = { current?.let { vm.toggleFavorite(it) } },
                    )
                    QuickAction(
                        icon = Icons.Outlined.Download,
                        label = "下载",
                        onClick = {
                            current?.let {
                                vm.download(it, ui.selectedQuality ?: it.qualityOptions.firstOrNull())
                            }
                        },
                    )
                    QuickAction(
                        icon = Icons.Outlined.QueueMusic,
                        label = "队列",
                        onClick = { navController.navigate(Routes.QUEUE) },
                    )
                    QuickAction(
                        icon = Icons.Outlined.HighQuality,
                        label = "音质",
                        onClick = { showQuality = true },
                    )
                    QuickAction(
                        icon = if (sleepTimer.active) Icons.Filled.Bedtime else Icons.Outlined.Bedtime,
                        label = if (sleepTimer.active) formatRemaining(sleepTimer.remainingMs ?: 0L) else "定时",
                        highlighted = sleepTimer.active,
                        onClick = { showSleepTimer = true },
                    )
                }
            }
        }
        }

        // 歌词设置
        if (showLyricSettings) {
            LyricSettingsSheet(
                fontSize = lyricFontSize,
                offsetMs = lyricOffset,
                minFontSize = SettingsStore.MIN_LYRIC_FONT_SIZE,
                maxFontSize = SettingsStore.MAX_LYRIC_FONT_SIZE,
                maxOffsetMs = SettingsStore.MAX_LYRIC_OFFSET,
                onFontSizeChange = { vm.setLyricFontSize(it) },
                onOffsetChange = { vm.setLyricOffset(it) },
                onDismiss = { showLyricSettings = false },
            )
        }

        // 睡眠定时器
        if (showSleepTimer) {
            SleepTimerSheet(
                remainingMs = sleepTimer.remainingMs,
                onPick = { minutes ->
                    if (minutes == null) vm.cancelSleepTimer() else vm.startSleepTimer(minutes)
                },
                onDismiss = { showSleepTimer = false },
            )
        }

        // 轻提示
        AnimatedVisibility(
            visible = ui.message != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
        ) {
            Text(
                text = ui.message.orEmpty(),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }

    // 音质选择
    if (showQuality && current != null) {
        ModalBottomSheet(onDismissRequest = { showQuality = false }) {
            QualitySheet(
                options = current.qualityOptions,
                selected = ui.selectedQuality,
                onSelect = {
                    vm.selectQuality(current, it)
                    showQuality = false
                },
            )
        }
    }
}

/** 副标题：歌手 · 专辑 · 渠道名。 */
private fun subtitleOf(item: MusicItem?): String {
    if (item == null) return "—"
    return listOfNotNull(
        item.artist.takeIf { it.isNotBlank() },
        item.album?.takeIf { it.isNotBlank() },
        ProviderRegistry.name(item.provider),
    ).joinToString(" · ")
}

/** 循环模式对应的图标。 */
private fun repeatIcon(repeatMode: Int): ImageVector = when (repeatMode) {
    PlayerViewModel.REPEAT_ALL -> Icons.Filled.Repeat
    PlayerViewModel.REPEAT_ONE -> Icons.Filled.RepeatOne
    else -> Icons.Outlined.Repeat
}

/** 控制行图标按钮。 */
@Composable
private fun ControlIcon(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    size: Dp = 21.dp,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}

/** 播放 / 暂停主按钮。 */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** 快捷键：图标 + 9sp 文字。 */
@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            // 快捷键现在有 5 个（收藏/下载/队列/音质/定时），收窄一点免得挤
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
        )
    }
}

/** 细进度条（4dp），支持点击与拖动跳转。 */
@Composable
private fun SeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var trackWidth by remember { mutableIntStateOf(1) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .onSizeChanged { trackWidth = it.width }
            .pointerInput(trackWidth) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var latest = down.position.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        latest = change.position.x
                        change.consume()
                    }
                    onSeek((latest / trackWidth.coerceAtLeast(1)).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** 歌词区：当前行放大加粗，下一行主色，其余次级色。 */
@Composable
private fun LyricsSection(
    lyrics: List<LyricEntry>,
    loading: Boolean,
    lyricIndex: Int,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    /** 点击某一行歌词：跳到那一句播放。 */
    onLineClick: ((LyricEntry) -> Unit)? = null,
    /** 歌词字号（sp），来自设置。 */
    fontSize: Int = 16,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            lyrics.isEmpty() && loading -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )

            lyrics.isEmpty() -> Text(
                text = "暂无歌词",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(lyrics) { index, entry ->
                    LyricLine(
                        text = entry.text,
                        isCurrent = index == lyricIndex,
                        isNext = index == lyricIndex + 1,
                        onClick = onLineClick?.let { { it(entry) } },
                        fontSize = fontSize,
                    )
                }
            }
        }
    }
}

/** 单行歌词。 */
@Composable
private fun LyricLine(
    text: String,
    isCurrent: Boolean,
    isNext: Boolean,
    onClick: (() -> Unit)? = null,
    /** 基准字号（sp）；当前行和下一行会在这基础上放大。 */
    fontSize: Int = 16,
) {
    val (size, weight, color) = when {
        isCurrent -> Triple((fontSize + 2).sp, FontWeight.Bold, MaterialTheme.colorScheme.onSurface)
        isNext -> Triple((fontSize + 1).sp, FontWeight.SemiBold, MaterialTheme.colorScheme.primary)
        else -> Triple(fontSize.sp, FontWeight.Normal, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f))
    }
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick.invoke() }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 5.dp),
        textAlign = TextAlign.Center,
        fontSize = size,
        fontWeight = weight,
        color = color,
    )
}

/** 音质选择弹层内容。 */
@Composable
private fun QualitySheet(
    options: List<QualityOption>,
    selected: QualityOption?,
    onSelect: (QualityOption) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = "选择音质",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "切换后会重新取流播放",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
        )
        Spacer(Modifier.height(10.dp))
        if (options.isEmpty()) {
            Text(
                text = "该渠道未提供可选音质",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
            )
            return@Column
        }
        options.forEach { option ->
            val isSelected = selected?.value == option.value
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = option.label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (option.format.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = option.format.uppercase(),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                        )
                    }
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * 把指定行滚动到视口中部。
 *
 * 不在可见范围时先用 [LazyListState.animateScrollToItem] 定位，再按实际偏移微调，
 * 这样无论列表当前滚动到哪里，当前行最终都停在视口中部附近。
 */
private suspend fun LazyListState.centerItem(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) {
        animateScrollToItem(index)
    }
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
    val target = viewportCenter - item.size / 2
    val delta = (item.offset - target).toFloat()
    if (delta != 0f) {
        scroll { scrollBy(delta) }
    }
}

/** 毫秒 → mm:ss（超过一小时显示 h:mm:ss）。 */
private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/**
 * 解析搜索结果里的时长字符串。
 *
 * 服务端一般给 "mm:ss"，少数给纯秒数，都做兼容；解析不出来返回 0。
 */
private fun parseDuration(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    val parts = raw.trim().split(":")
    val numbers = parts.mapNotNull { it.trim().toLongOrNull() }
    if (numbers.isEmpty()) return 0L
    return when (numbers.size) {
        1 -> numbers[0] * 1000L
        2 -> (numbers[0] * 60 + numbers[1]) * 1000L
        else -> (numbers[0] * 3600 + numbers[1] * 60 + numbers[2]) * 1000L
    }
}

/**
 * 封面模糊背景。
 *
 * Android 12（API 31）起可以用 [Modifier.blur] 真模糊；
 * 更低的版本 Compose 的 blur 是空操作，所以改成「封面图 + 更重的黑遮罩」近似，
 * 视觉上不会突变。
 */
@Composable
private fun BlurredCoverBackground(cover: String?, modifier: Modifier = Modifier) {
    val url = remember(cover) { SafeUrl.upgradeToHttps(cover) }
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (canBlur) {
                            Modifier.blur(70.dp, BlurredEdgeTreatment.Unbounded)
                        } else {
                            Modifier
                        }
                    ),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(alpha = if (canBlur) 0.45f else 0.74f)
                    )
            )
        }
    }
}

/**
 * 分享当前歌曲。
 *
 * 分享纯文本：歌名 + 歌手 + 专辑 + 音源站地址，
 * 不带任何私有信息（收藏、播放历史都不外传）。
 */
private fun shareCurrent(context: Context, item: MusicItem?) {
    if (item == null) return
    val text = buildString {
        append("《").append(item.title).append("》")
        if (item.artist.isNotBlank()) {
            append(" - ").append(item.artist)
        }
        if (!item.album.isNullOrBlank()) {
            append("\n专辑：").append(item.album)
        }
        append("\n来自 Mu 音乐")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, item.title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享歌曲"))
    }
}
