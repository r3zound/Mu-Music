package com.r3zound.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r3zound.music.ui.theme.MuPalette
import com.r3zound.music.ui.theme.tagColor

/**
 * 「加入歌单 / 打标签」的通用弹窗。
 *
 * 这里刻意用 [PlaylistOption] / [TagOption] 这种轻量 UI 模型，而不是数据库实体，
 * 让 UI 组件层不依赖 data 层，feature 包各自把自己的实体映射进来即可。
 */

/** 歌单选择项。 */
data class PlaylistOption(
    val id: Long,
    val name: String,
    val songCount: Int = 0,
)

/** 标签选择项。 */
data class TagOption(
    val id: Long,
    val name: String,
    val colorIndex: Int = 0,
)

// ── 歌曲操作面板 ────────────────────────────────────────────────

/**
 * 长按歌曲后弹出的操作列表。
 *
 * 每个 feature 包自己决定「收藏」按钮的文案与行为（收藏页是「取消收藏」，搜索页是「收藏」）。
 */
@Composable
fun SongActionsDialog(
    title: String,
    subtitle: String? = null,
    favoriteActionLabel: String,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTags: () -> Unit,
    onDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        color = MuPalette.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        text = {
            Column {
                ActionLine("播放", onPlay)
                ActionLine("加入歌单", onAddToPlaylist)
                ActionLine("标签", onEditTags)
                ActionLine("下载", onDownload)
                ActionLine(favoriteActionLabel, onToggleFavorite)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun ActionLine(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
    )
}

// ── 歌单选择 ────────────────────────────────────────────────────

/**
 * 选择一个歌单；列表为空时可以直接新建。
 *
 * [onCreate] 收到的是歌单名称，由调用方负责创建后把歌曲加进去。
 */
@Composable
fun PlaylistPickerDialog(
    playlists: List<PlaylistOption>,
    onDismiss: () -> Unit,
    onPick: (PlaylistOption) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入歌单", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (playlists.isEmpty()) {
                    Text("还没有歌单，先新建一个吧", fontSize = 12.5.sp, color = MuPalette.ink3)
                    Spacer(Modifier.height(8.dp))
                }
                playlists.forEach { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(playlist) }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(MuPalette.surface2),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QueueMusic,
                                contentDescription = null,
                                tint = MuPalette.ink3,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = playlist.name,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (playlist.songCount > 0) {
                            Text(
                                text = "${playlist.songCount} 首",
                                fontSize = 10.5.sp,
                                color = MuPalette.ink3,
                            )
                        }
                    }
                }
                if (creating) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        placeholder = { Text("歌单名称", fontSize = 13.sp, color = MuPalette.ink3) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { creating = true }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(MuPalette.surface2),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "新建歌单",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    onClick = {
                        val name = newName.trim()
                        if (name.isNotEmpty()) {
                            onCreate(name)
                            newName = ""
                            creating = false
                        }
                    },
                    enabled = newName.isNotBlank(),
                ) { Text("创建并加入") }
            } else {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        dismissButton = {
            if (creating) {
                TextButton(
                    onClick = {
                        creating = false
                        newName = ""
                    },
                ) { Text("取消") }
            }
        },
    )
}

// ── 标签选择 ────────────────────────────────────────────────────

/**
 * 多选标签；勾选即生效（由调用方负责落库）。
 *
 * 勾选是**即时的**，不需要再点一次「确定」——和主流音乐 App 的标签交互一致。
 */
@Composable
fun TagPickerDialog(
    tags: List<TagOption>,
    selectedTagIds: Set<Long>,
    onDismiss: () -> Unit,
    onToggle: (TagOption) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标签", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                if (tags.isEmpty()) {
                    Text(
                        text = "还没有标签，先建一个",
                        fontSize = 12.5.sp,
                        color = MuPalette.ink3,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(tags, key = { it.id }) { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(tag) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(tagColor(tag.colorIndex))
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = tag.name,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Checkbox(
                                    checked = selectedTagIds.contains(tag.id),
                                    onCheckedChange = { onToggle(tag) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (creating) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        placeholder = { Text("标签名称", fontSize = 13.sp, color = MuPalette.ink3) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { creating = true }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "新建标签",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    onClick = {
                        val name = newName.trim()
                        if (name.isNotEmpty()) {
                            onCreate(name)
                            newName = ""
                            creating = false
                        }
                    },
                    enabled = newName.isNotBlank(),
                ) { Text("创建并勾选") }
            } else {
                TextButton(onClick = onDismiss) { Text("完成") }
            }
        },
        dismissButton = {
            if (creating) {
                TextButton(
                    onClick = {
                        creating = false
                        newName = ""
                    },
                ) { Text("取消") }
            }
        },
    )
}
