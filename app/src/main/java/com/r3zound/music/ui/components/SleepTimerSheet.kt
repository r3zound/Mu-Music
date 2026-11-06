package com.r3zound.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r3zound.music.ui.theme.MuPalette

/**
 * 睡眠定时器选项。
 *
 * @param minutes 时长；null 表示「关闭定时」。
 */
private data class SleepOption(val minutes: Int?, val label: String)

private val sleepOptions = listOf(
    SleepOption(10, "10 分钟"),
    SleepOption(20, "20 分钟"),
    SleepOption(30, "30 分钟"),
    SleepOption(45, "45 分钟"),
    SleepOption(60, "60 分钟"),
    SleepOption(null, "关闭定时"),
)

/**
 * 睡眠定时器底部面板。
 *
 * 睡前听歌必须能定时关闭，否则电量和流量都遭罪。
 * 交互参考 MusicFree 的 `components/panels/types/timingClose.tsx`。
 *
 * @param remainingMs 当前剩余毫秒；非 null 表示定时正在跑，面板标题显示倒计时。
 * @param onPick 选中某个时长；传 null 表示关闭定时。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    remainingMs: Long?,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (remainingMs != null) Icons.Filled.Bedtime else Icons.Filled.TimerOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (remainingMs != null) {
                        "睡眠定时 · 剩余 ${formatRemaining(remainingMs)}"
                    } else {
                        "睡眠定时"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "到点后自动停止播放",
                fontSize = 11.5.sp,
                color = MuPalette.ink3,
            )
            Spacer(Modifier.height(14.dp))

            sleepOptions.forEach { option ->
                val active = option.minutes != null && remainingMs != null
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable {
                            onPick(option.minutes)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = option.label,
                        fontSize = 14.sp,
                        color = if (option.minutes == null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

/** 毫秒 → 「12:34」或「1:02:03」。 */
fun formatRemaining(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}
