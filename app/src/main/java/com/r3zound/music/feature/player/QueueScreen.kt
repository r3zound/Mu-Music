package com.r3zound.music.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.ui.components.CoverImage
import com.r3zound.music.ui.components.EmptyState
import com.r3zound.music.ui.theme.MuPalette
import kotlinx.coroutines.launch

/**
 * 播放队列页。
 *
 * 展示 [com.r3zound.music.media.PlayerHolder] 里的当前队列，当前播放项高亮；
 * 点击某项直接跳到该曲播放，右侧可删除单项，顶部可清空。
 */
@Composable
fun QueueScreen(navController: NavHostController) {
    val vm: PlayerViewModel = hiltViewModel()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val queue = playback.queue
    val currentIndex = playback.index

    var showSaveSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    if (showSaveSheet) {
        SaveQueueDialog(
            count = queue.size,
            onDismiss = { showSaveSheet = false },
            onConfirm = { name ->
                showSaveSheet = false
                scope.launch {
                    val id = vm.saveQueueAsPlaylist(name)
                    if (id != null) {
                        snackbarHostState.showSnackbar("已存为歌单")
                    }
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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
                text = "播放队列",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(enabled = queue.isNotEmpty()) { showSaveSheet = true }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "存为歌单",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(enabled = queue.isNotEmpty()) { vm.clearQueue() }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "清空",
                    fontSize = 12.5.sp,
                    color = if (queue.isEmpty()) MuPalette.ink3 else MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (queue.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.QueueMusic,
                title = "队列是空的",
                message = "去搜索并播放一首歌，它就会出现在这里",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.navigationBarsPadding())
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        ) {
            itemsIndexed(
                items = queue,
                key = { index, item -> "${item.provider}:${item.id}:$index" },
            ) { index, item ->
                QueueRow(
                    item = item,
                    isCurrent = index == currentIndex,
                    onClick = { vm.playAt(index) },
                    onRemove = { vm.removeAt(index) },
                )
            }
        }
    }
}

/**
 * 队列中的一行。
 *
 * 当前播放项用主色竖条 + 主色标题高亮，右侧是删除按钮。
 */
/** 「把队列存成歌单」的对话框。 */
@Composable
private fun SaveQueueDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("存为歌单") },
        text = {
            Column {
                Text(
                    text = "把当前播放队列的 $count 首歌存成一个新歌单。",
                    fontSize = 13.sp,
                    color = MuPalette.ink2,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("歌单名称") },
                    placeholder = { Text("例如：通勤路上") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun QueueRow(
    item: MusicItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 当前项高亮竖条
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
        )
        Spacer(Modifier.width(10.dp))
        CoverImage(item.cover, 40.dp, radius = 7.dp, title = item.title)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.artist,
                fontSize = 11.sp,
                color = MuPalette.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "从队列移除",
                tint = MuPalette.ink3,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
