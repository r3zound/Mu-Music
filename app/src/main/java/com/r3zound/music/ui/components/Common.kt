package com.r3zound.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.r3zound.music.core.model.ChannelHealth
import com.r3zound.music.core.model.ChannelInfo
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.core.util.SafeUrl
import com.r3zound.music.ui.theme.MuPalette

/** 封面。加载中/失败时用色块渐变兜底，避免列表塌陷。 */
@Composable
fun CoverImage(
    cover: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    radius: Dp = 8.dp,
    variant: Int = 0,
    title: String? = null,
    /** 点击封面。为 null 时不可点。 */
    onClick: (() -> Unit)? = null,
) {
    val gradients = listOf(
        listOf(Color(0xFF3D4457), Color(0xFF1D222D)),
        listOf(Color(0xFF4A3F2F), Color(0xFF241F19)),
        listOf(Color(0xFF2F4638), Color(0xFF16221C)),
        listOf(Color(0xFF4A3540), Color(0xFF241A1F)),
    )
    val g = gradients[variant.coerceIn(0, gradients.lastIndex)]
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(g))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (cover.isNullOrBlank()) {
            Text(
                text = title?.take(1) ?: "♪",
                color = Color.White.copy(alpha = 0.78f),
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.34).sp,
            )
        } else {
            AsyncImage(
                // 服务端封面地址是 http://，明文流量被系统拦掉，这里统一升到 https
                model = SafeUrl.upgradeToHttps(cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 音质徽标：只显示真实格式，不标「超清母带」这类名不副实的标签。 */
@Composable
fun QualityBadge(text: String?, highlight: Boolean = false, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    val bg = if (highlight) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = if (highlight) {
        MaterialTheme.colorScheme.secondary
    } else {
        MuPalette.ink3
    }
    Text(
        text = text,
        color = fg,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = modifier
            .background(bg, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** 来源渠道徽标。 */
@Composable
fun ProviderBadge(name: String?, modifier: Modifier = Modifier) {
    if (name.isNullOrBlank()) return
    Text(
        text = name,
        color = MuPalette.ink3,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** 回落提示：服务端把未知渠道静默换成了别的渠道。 */
@Composable
fun FallbackBadge(modifier: Modifier = Modifier) {
    Text(
        text = "已回落",
        color = MaterialTheme.colorScheme.secondary,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** 列表行。右侧固定两个动作：收藏、下载。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    item: MusicItem,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    providerName: String? = null,
    qualityLabel: String? = null,
    qualityHighlight: Boolean = false,
    onClick: () -> Unit = {},
    onPlay: () -> Unit = {},
    onFavorite: () -> Unit = {},
    onDownload: () -> Unit = {},
    /** 长按：用来唤出「加入歌单 / 打标签」这类操作面板。为 null 时退化成普通点击。 */
    onLongClick: (() -> Unit)? = null,
    /** 点击歌手名：触发按歌手搜索。为 null 时歌手名不可点。 */
    onArtistClick: ((String) -> Unit)? = null,
    /** 是否是当前正在播放的曲目。为 true 时标题与歌手用主色高亮。 */
    isPlaying: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        if (leading == null) {
            CoverImage(item.cover, 42.dp, title = item.title)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                // 正在播放的曲目用主色，跟其他条目一眼区分开
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    // 歌手可点时提亮，暗示可以点进去搜这个歌手
                    color = when {
                        isPlaying -> MaterialTheme.colorScheme.primary
                        onArtistClick != null -> MuPalette.ink2
                        else -> MuPalette.ink3
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(
                            if (onArtistClick != null) {
                                Modifier.clickable { onArtistClick.invoke(item.primaryArtist) }
                            } else {
                                Modifier
                            }
                        ),
                )
                providerName?.let { ProviderBadge(it) }
                QualityBadge(qualityLabel, qualityHighlight)
                item.hasFallenBack.takeIf { it }?.let { FallbackBadge() }
                item.duration?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MuPalette.ink3,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        if (trailing != null) {
            trailing()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(
                    icon = if (isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MuPalette.ink3,
                    onClick = onFavorite,
                )
                IconAction(icon = Icons.Outlined.Download, tint = MuPalette.ink3, onClick = onDownload)
            }
        }
    }
}

@Composable
fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** 区块标题。 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MuPalette.ink3,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MuPalette.ink3,
            )
        }
    }
}

/** 空态。 */
@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MuPalette.ink3, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        message?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MuPalette.ink3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        action?.let {
            Spacer(Modifier.height(16.dp))
            it()
        }
    }
}

/** 渠道选择 chip，带健康指示点。 */
@Composable
fun ChannelChip(
    channel: ChannelInfo,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        label = "chipBg",
    )
    val fg by animateColorAsState(
        if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chipFg",
    )
    val dotColor = when (channel.health) {
        ChannelHealth.OK -> Color(0xFF12A150)
        ChannelHealth.PARTIAL -> Color(0xFFE8930C)
        ChannelHealth.DOWN -> Color(0xFF6B7488)
        ChannelHealth.UNKNOWN -> Color(0xFF6B7488)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel.health != ChannelHealth.UNKNOWN) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(dotColor)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(text = channel.name, fontSize = 12.sp, color = fg, maxLines = 1)
    }
}

/** 多选勾选框。 */
@Composable
fun CheckBox(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(
                width = if (checked) 0.dp else 1.5.dp,
                color = if (checked) Color.Transparent else MuPalette.ink3,
                shape = RoundedCornerShape(5.dp),
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
        }
    }
}

/** 主色按钮。 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = if (enabled) Color.White else MuPalette.ink3, modifier = Modifier.size(13.dp))
        }
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color.White else MuPalette.ink3,
        )
    }
}

/** 次级按钮。 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = MuPalette.ink2, modifier = Modifier.size(13.dp))
        }
        Text(text = text, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = MuPalette.ink2)
    }
}

/** 搜索输入框。 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, fontSize = 13.sp, color = MuPalette.ink3) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MuPalette.ink3,
                modifier = Modifier.size(17.dp),
            )
        },
        trailingIcon = trailing,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSearch?.invoke() },
        ),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/** 占位图。 */
@Composable
fun MusicNoteIcon(size: Dp = 18.dp, tint: Color = MuPalette.ink3) {
    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
fun FavoriteIcon(filled: Boolean) {
    Icon(
        imageVector = if (filled) Icons.Filled.Favorite else Icons.Outlined.BookmarkBorder,
        contentDescription = null,
        tint = if (filled) MaterialTheme.colorScheme.primary else MuPalette.ink3,
        modifier = Modifier.size(16.dp),
    )
}

@Composable
fun PlayIcon(tint: Color = MaterialTheme.colorScheme.onSurface, size: Dp = 16.dp) {
    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

/**
 * 通用 tab 项（带下划线指示）。
 *
 * 收藏页、播放列表页、首页都用它，保证几个页面切换的视觉一致。
 */
@Composable
fun FavTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(end = 18.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MuPalette.ink3,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(1.dp),
                )
        )
    }
}
