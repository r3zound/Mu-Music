package com.r3zound.music.ui.components

import com.r3zound.music.ui.theme.MuPalette
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 歌词设置面板：字号 + 时间偏移。
 *
 * - **字号**全局生效（sp），所有歌曲共用。
 * - **时间偏移**按歌曲存：不同源的歌词时间轴准头不一样，
 *   偏移只对当前这首生效。正数 = 歌词提前，负数 = 歌词延后。
 *
 * 交互参考 MusicFree 的 `setFontSize.tsx` / `setLyricOffset.tsx`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricSettingsSheet(
    fontSize: Int,
    offsetMs: Long,
    minFontSize: Int,
    maxFontSize: Int,
    maxOffsetMs: Long,
    onFontSizeChange: (Int) -> Unit,
    onOffsetChange: (Long) -> Unit,
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
            Text(
                text = "歌词设置",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            // ── 字号 ──
            Text(
                text = "字号",
                fontSize = 12.sp,
                color = MuPalette.ink3,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepButton(
                    icon = Icons.Filled.Remove,
                    enabled = fontSize > minFontSize,
                    onClick = { onFontSizeChange(fontSize - 1) },
                )
                Text(
                    text = "$fontSize sp",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(56.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepButton(
                    icon = Icons.Filled.Add,
                    enabled = fontSize < maxFontSize,
                    onClick = { onFontSizeChange(fontSize + 1) },
                )
                Spacer(Modifier.weight(1f))
                // 实时预览
                Text(
                    text = "预览歌词",
                    fontSize = fontSize.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(22.dp))

            // ── 时间偏移 ──
            Text(
                text = "时间偏移",
                fontSize = 12.sp,
                color = MuPalette.ink3,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "歌词比歌慢时调正值，快了调负值。只对当前这首歌生效。",
                fontSize = 11.sp,
                color = MuPalette.ink3,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepButton(
                    icon = Icons.Filled.Remove,
                    enabled = offsetMs - 500L >= -maxOffsetMs,
                    onClick = { onOffsetChange(offsetMs - 500L) },
                )
                Text(
                    text = formatOffset(offsetMs),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(64.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepButton(
                    icon = Icons.Filled.Add,
                    enabled = offsetMs + 500L <= maxOffsetMs,
                    onClick = { onOffsetChange(offsetMs + 500L) },
                )
                Spacer(Modifier.weight(1f))
                if (offsetMs != 0L) {
                    Text(
                        text = "重置",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOffsetChange(0L) },
                    )
                }
            }
        }
    }
}

/** 加减按钮。 */
@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MuPalette.ink3
            },
            modifier = Modifier.size(17.dp),
        )
    }
}

/** 偏移毫秒 → 「+1.5 秒」。 */
fun formatOffset(offsetMs: Long): String {
    if (offsetMs == 0L) return "0 秒"
    val sign = if (offsetMs > 0) "+" else "-"
    val abs = kotlin.math.abs(offsetMs)
    return if (abs % 1000L == 0L) {
        "$sign${abs / 1000L} 秒"
    } else {
        String.format("%s%.1f 秒", sign, abs / 1000.0)
    }
}
