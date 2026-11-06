package com.r3zound.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.ui.theme.MuPalette
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 常驻 Mini Player。
 *
 * 播放是一个持续状态而非页面，因此底栏只有 5 项（首页/搜索/收藏/下载/设置），
 * Mini Player 常驻在导航栏之上，点击展开全屏播放页。
 *
 * **横向滑动可以切歌**（带惯性）：
 * - 位移超过卡片宽度的 30% → 切上一首 / 下一首
 * - 或者甩得快（速度 > 1.5 px/ms ≈ 1500 px/s）→ 也切
 * - 都不满足 → 带弹性动画回到原位
 *
 * 交互参考 MusicFree 的 `components/musicBar/musicInfo.tsx`。
 */
@Composable
fun MiniPlayer(
    item: MusicItem?,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** 滑动切歌：-1 上一首，1 下一首。 */
    onSwipe: (Int) -> Unit = {},
) {
    AnimatedVisibility(
        visible = item != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        // 横向拖动位移；用 Animatable 是为了松手时能做回弹动画
        val offsetX = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        // 最近一次拖动的速度（px/ms），用来判断是不是「甩」
        var dragSpeed by remember { mutableFloatStateOf(0f) }
        var lastDragTime by remember { mutableLongStateOf(0L) }

        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onOpen)
                // pointerInput 放在 clickable 之后 → 更内层，先收到拖动事件；
                // 拖动超过阈值会自动取消点击，不会误触发「打开播放页」
                .pointerInput(item?.provider, item?.id) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragSpeed = 0f
                            lastDragTime = 0L
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch { offsetX.snapTo(offsetX.value + amount) }
                            val now = change.uptimeMillis
                            val dt = (now - lastDragTime).coerceAtLeast(1L)
                            if (lastDragTime != 0L) {
                                dragSpeed = amount / dt.toFloat()
                            }
                            lastDragTime = now
                        },
                        onDragEnd = {
                            val direction = when {
                                // 位移超过卡片宽度的 30%
                                abs(offsetX.value) > size.width * 0.3f ->
                                    if (offsetX.value > 0) -1 else 1
                                // 或者甩得快
                                abs(dragSpeed) > 1.5f ->
                                    if (dragSpeed > 0) -1 else 1
                                else -> 0
                            }
                            scope.launch {
                                if (direction == 0) {
                                    // 没达到阈值：弹回原位
                                    offsetX.animateTo(0f, tween(220))
                                } else {
                                    // 切歌：先滑出屏幕
                                    val target = if (direction > 0) size.width.toFloat() else -size.width.toFloat()
                                    offsetX.animateTo(target, tween(160))
                                    onSwipe(direction)
                                    // 新歌从反方向滑入
                                    offsetX.snapTo(-target)
                                    offsetX.animateTo(0f, tween(200))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, tween(220)) }
                        },
                    )
                }
                .graphicsLayer { translationX = offsetX.value }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(item?.cover, 34.dp, radius = 7.dp, title = item?.title)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item?.title ?: "",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item?.artist ?: "",
                    fontSize = 10.5.sp,
                    color = MuPalette.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(17.dp),
                )
            }
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onNext),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "下一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}
