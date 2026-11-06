package com.r3zound.music.feature.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.r3zound.music.data.local.DownloadStatus
import com.r3zound.music.data.local.DownloadTaskEntity
import com.r3zound.music.download.DownloadNotifier
import com.r3zound.music.ui.components.CoverImage
import com.r3zound.music.ui.components.EmptyState
import com.r3zound.music.ui.components.IconAction
import com.r3zound.music.ui.components.PrimaryButton
import com.r3zound.music.ui.components.QualityBadge
import com.r3zound.music.ui.components.SecondaryButton
import com.r3zound.music.ui.nav.Routes
import com.r3zound.music.ui.theme.MuPalette
import com.r3zound.music.ui.theme.DangerViolet
import kotlinx.coroutines.flow.collect
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 下载中心。
 *
 * 三个标签页：下载中 / 已完成 / 失败。
 *
 * 产品要点：直链约 30 分钟过期，下载中断后 APP 会自动重新取直链并**断点续传**，
 * 不是从头重下。因此「直链已失效 / 链接已过期」这类失败在界面上要显示成
 * 「链接已过期，正在恢复」，而不是普通失败。
 */
@Composable
fun DownloadsScreen(navController: NavHostController) {
    val viewModel: DownloadsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 本地文件播放：PlayerHolder 只认网络直链，这里单独用 MediaPlayer 播 content:// Uri
    val context = LocalContext.current
    val localPlayback = remember(context) { LocalPlayback(context) }
    val localState by localPlayback.state.collectAsStateWithLifecycle()

    var sortMenuOpen by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.localPlay.collect { request -> localPlayback.play(request.taskId, request.path) }
    }

    DisposableEffect(Unit) {
        onDispose { localPlayback.release() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DownloadsTopBar(
            sort = state.sort,
            menuOpen = sortMenuOpen,
            onMenuToggle = { sortMenuOpen = it },
            onSortChange = {
                viewModel.selectSort(it)
                sortMenuOpen = false
            },
        )
        DownloadTabRow(state = state, onSelect = { viewModel.selectTab(it) })

        Box(Modifier.weight(1f)) {
            val goSearch: () -> Unit = { navController.navigate(Routes.SEARCH) }
            when (state.tab) {
                DownloadTab.ACTIVE -> ActiveTaskList(state = state, viewModel = viewModel, onSearch = goSearch)
                DownloadTab.COMPLETED -> CompletedTaskList(
                    state = state,
                    viewModel = viewModel,
                    localState = localState,
                    onSearch = goSearch,
                    onClear = { showClearDialog = true },
                )
                DownloadTab.FAILED -> FailedTaskList(state = state, viewModel = viewModel, onSearch = goSearch)
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空已完成") },
            text = { Text("将移除全部已完成的下载记录，已下载的文件仍保留在 Music/MuMusic 目录。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearCompleted()
                    }
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

// ── 顶栏 ────────────────────────────────────────────────────

@Composable
private fun DownloadsTopBar(
    sort: DownloadSort,
    menuOpen: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onSortChange: (DownloadSort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "下载",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Box {
            IconAction(
                icon = Icons.Outlined.Tune,
                tint = MuPalette.ink2,
                onClick = { onMenuToggle(true) },
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onMenuToggle(false) },
            ) {
                DownloadSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label,
                                color = if (option == sort) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        onClick = { onSortChange(option) },
                    )
                }
            }
        }
    }
}

// ── 标签页行 ────────────────────────────────────────────────

@Composable
private fun DownloadTabRow(state: DownloadsUiState, onSelect: (DownloadTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DownloadTab.entries.forEach { tab ->
            val selected = state.tab == tab
            val count = when (tab) {
                DownloadTab.ACTIVE -> state.active.size
                DownloadTab.COMPLETED -> state.completed.size
                DownloadTab.FAILED -> state.failed.size
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${tab.label} $count",
                    fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MuPalette.ink3,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(26.dp)
                        .height(2.dp)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            }
                        )
                )
            }
        }
    }
}

// ── 下载中 ──────────────────────────────────────────────────

@Composable
private fun ActiveTaskList(state: DownloadsUiState, viewModel: DownloadsViewModel, onSearch: () -> Unit) {
    if (state.active.isEmpty()) {
        EmptyDownloads(onSearch = onSearch)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.active, key = { it.taskId }) { task ->
            ActiveTaskCard(
                task = task,
                speed = state.speeds[task.taskId] ?: 0L,
                onPause = { viewModel.pause(task) },
                onResume = { viewModel.resume(task) },
                onCancel = { viewModel.cancel(task) },
            )
        }
    }
}

@Composable
private fun ActiveTaskCard(
    task: DownloadTaskEntity,
    speed: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val paused = task.error == DOWNLOAD_ERROR_CANCELLED || task.status == DownloadStatus.PAUSED
    val total = task.totalBytes
    val downloaded = task.downloadedBytes
    val percent = if (total > 0) ((downloaded * 100L) / total).toInt() else 0
    val label = formatLabel(task)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverImage(task.cover, 38.dp, radius = 8.dp, title = task.title)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (total > 0) {
                        "$label · ${DownloadNotifier.formatSize(downloaded)} / ${DownloadNotifier.formatSize(total)}"
                    } else {
                        "$label · ${DownloadNotifier.formatSize(downloaded)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MuPalette.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                QualityBadge(label)
                Row {
                    IconAction(
                        icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        tint = if (paused) MaterialTheme.colorScheme.primary else MuPalette.ink2,
                        onClick = if (paused) onResume else onPause,
                    )
                    IconAction(icon = Icons.Outlined.Close, tint = MuPalette.ink3, onClick = onCancel)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        DownloadProgressBar(
            progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (paused) {
                    "$percent% · 已暂停"
                } else {
                    buildString {
                        append("$percent%")
                        append(" · ")
                        append(if (speed > 0) "${DownloadNotifier.formatSize(speed)}/s" else "下载中")
                        if (total > 0 && speed > 0) {
                            append(" · ")
                            append(formatRemaining(total - downloaded, speed))
                        }
                    }
                },
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MuPalette.ink2,
            )
            Spacer(Modifier.weight(1f))
            if (paused && downloaded > 0) {
                Text(
                    text = "继续后从 ${DownloadNotifier.formatSize(downloaded)} 续传",
                    fontSize = 10.sp,
                    color = MuPalette.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── 已完成 ──────────────────────────────────────────────────

@Composable
private fun CompletedTaskList(
    state: DownloadsUiState,
    viewModel: DownloadsViewModel,
    localState: LocalPlaybackState,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    if (state.completed.isEmpty()) {
        EmptyDownloads(onSearch = onSearch)
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            SecondaryButton(
                text = "清空已完成",
                icon = Icons.Outlined.Delete,
                onClick = onClear,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.completed, key = { it.taskId }) { task ->
                val playing = localState.taskId == task.taskId && localState.isPlaying
                CompletedTaskCard(
                    task = task,
                    playing = playing,
                    onPlay = { viewModel.playLocal(task) },
                    onDelete = { viewModel.remove(task) },
                )
            }
        }
    }
}

@Composable
private fun CompletedTaskCard(
    task: DownloadTaskEntity,
    playing: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    val size = if (task.fileSize > 0) task.fileSize else task.downloadedBytes
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverImage(task.cover, 38.dp, radius = 8.dp, title = task.title)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${formatLabel(task)} · ${DownloadNotifier.formatSize(size)} · ${formatTime(task.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MuPalette.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = displayPath(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = MuPalette.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconAction(
                icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                tint = if (playing) MaterialTheme.colorScheme.primary else MuPalette.ink2,
                onClick = onPlay,
            )
            IconAction(icon = Icons.Outlined.Delete, tint = MuPalette.ink3, onClick = onDelete)
        }

        Spacer(Modifier.height(10.dp))
        DownloadProgressBar(progress = 1f, color = MuPalette.success)
    }
}

// ── 失败 ────────────────────────────────────────────────────

@Composable
private fun FailedTaskList(state: DownloadsUiState, viewModel: DownloadsViewModel, onSearch: () -> Unit) {
    if (state.failed.isEmpty()) {
        EmptyDownloads(onSearch = onSearch)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.failed, key = { it.taskId }) { task ->
            FailedTaskCard(
                task = task,
                recovering = task.taskId in state.recovering,
                onRetry = { viewModel.retry(task) },
                onRemove = { viewModel.remove(task) },
            )
        }
    }
}

@Composable
private fun FailedTaskCard(
    task: DownloadTaskEntity,
    recovering: Boolean,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val expired = isExpiredLink(task.error)
    val total = task.totalBytes
    val downloaded = task.downloadedBytes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverImage(task.cover, 38.dp, radius = 8.dp, title = task.title)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        expired && recovering -> "链接已过期，正在恢复"
                        expired -> "链接已过期，可重试恢复"
                        else -> task.error?.takeIf { it.isNotBlank() } ?: "下载失败"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (expired) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (expired) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (expired) {
                    Text(
                        text = "APP 会自动重新获取直链并断点续传，不会从头重下",
                        style = MaterialTheme.typography.bodySmall,
                        color = MuPalette.ink3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                PrimaryButton(text = "重试", icon = Icons.Filled.Refresh, onClick = onRetry)
                Spacer(Modifier.height(6.dp))
                SecondaryButton(text = "移除", onClick = onRemove)
            }
        }

        Spacer(Modifier.height(10.dp))
        DownloadProgressBar(
            progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f,
            color = DangerViolet,
        )
    }
}

// ── 公共组件 ────────────────────────────────────────────────

/** 4dp 圆角进度条。 */
@Composable
private fun DownloadProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color)
        )
    }
}

/** 空态：任何标签页没有内容时都引导去搜索。 */
@Composable
private fun EmptyDownloads(onSearch: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        EmptyState(
            icon = Icons.Outlined.Download,
            title = "还没有下载任务",
            message = "去搜索喜欢的歌，加入下载队列",
            action = {
                PrimaryButton(
                    text = "去搜索",
                    icon = Icons.Outlined.Search,
                    onClick = onSearch,
                )
            },
        )
    }
}

// ── 文本工具 ────────────────────────────────────────────────

/** 已知音频格式。 */
private val KNOWN_FORMATS = setOf("MP3", "FLAC", "M4A", "OGG", "AAC", "WAV")

/** 格式标签：优先取文件名扩展名，其次用音质名。 */
private fun formatLabel(task: DownloadTaskEntity): String {
    val extension = task.fileName.substringAfterLast('.', "").uppercase(Locale.ROOT)
    if (extension in KNOWN_FORMATS) return extension
    return task.qualityLabel?.takeIf { it.isNotBlank() } ?: "音频"
}

/** 已下载文件的展示路径，如 Music/MuMusic/周杰伦 - 晴天.flac。 */
private fun displayPath(task: DownloadTaskEntity): String {
    val name = task.fileName.ifBlank { task.title }
    return "Music/MuMusic/$name"
}

/** 剩余时间。 */
private fun formatRemaining(remainingBytes: Long, bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "剩余未知"
    val seconds = (remainingBytes / bytesPerSecond).coerceAtLeast(0L)
    return when {
        seconds < 60 -> "剩余 ${seconds}秒"
        seconds < 3600 -> "剩余 ${seconds / 60}分${(seconds % 60).toString().padStart(2, '0')}秒"
        else -> "剩余 ${seconds / 3600}小时${(seconds % 3600) / 60}分"
    }
}

/** 完成时间：今天 / 昨天 / 具体日期。 */
private fun formatTime(timestamp: Long): String {
    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone)
    val then = Instant.ofEpochMilli(timestamp).atZone(zone)
    val time = then.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    val days = then.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()
    return when (days) {
        0L -> "今天 $time"
        1L -> "昨天 $time"
        else -> then.toLocalDate().format(DateTimeFormatter.ofPattern("MM-dd", Locale.getDefault())) + " $time"
    }
}

/** 是否是「直链过期」类错误。 */
private fun isExpiredLink(error: String?): Boolean {
    val text = error ?: return false
    return text.contains("过期") || text.contains("直链已失效")
}
