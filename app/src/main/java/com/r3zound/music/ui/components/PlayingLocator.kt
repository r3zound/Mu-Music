package com.r3zound.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

/**
 * 「正在播放」定位按钮（准星）。
 *
 * 长列表里播了一首靠前的歌，往下滚之后就不知道现在在放哪首了。
 * 这个按钮浮在列表右下角：
 *
 * - 开始滚动时出现（前提是当前播放的曲目就在这个列表里）
 * - 点一下直接滚回当前播放项
 * - 停止滚动 5 秒后自动隐藏，不长期占地方
 *
 * 交互参考 MusicFree 的 `components/musicList/index.tsx`。
 *
 * @param locatable 当前播放的曲目是否在这个列表里；不在就不显示按钮。
 * @param listState 列表的滚动状态，用来监听滚动起停。
 * @param onLocate 定位动作，由调用方负责算出目标下标并滚动。
 */
@Composable
fun PlayingLocator(
    locatable: Boolean,
    listState: LazyListState,
    onLocate: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!locatable) return

    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 滚动开始 → 显示；滚动停止 → 5 秒后隐藏。
    // 用 collectLatest：滚动重新开始时会取消上一次等待，不会误隐藏。
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { scrolling ->
                if (scrolling) {
                    visible = true
                } else {
                    delay(5000)
                    visible = false
                }
            }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable {
                    scope.launch { onLocate() }
                    visible = false
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "定位到正在播放的歌曲",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/** 曲目的唯一键（provider:id），跨渠道唯一。 */
fun songKeyOf(provider: String, id: String): String = "$provider:$id"
