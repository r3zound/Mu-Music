package com.r3zound.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── 品牌与语义色 ───────────────────────────────────────────────
val BrandRed = Color(0xFFE5484D)      // 强调：正在发生的事（播放中/下载中/选中）
val BrandRedDark = Color(0xFFC93A3F)
val BrandRedLight = Color(0xFFFF8B8F)
val QualityAmber = Color(0xFFE8930C)   // 音质档位
val QualityAmberLight = Color(0xFFF0A93A)
val SuccessGreen = Color(0xFF12A150)
val SuccessGreenLight = Color(0xFF3FC181)
val DangerViolet = Color(0xFF8B5CF6)
val WarningRed = Color(0xFFEF4444)

// ── 深色表面四层 ───────────────────────────────────────────────
val DarkBackground = Color(0xFF0C0E14)
val DarkSurface = Color(0xFF161A23)
val DarkSurface2 = Color(0xFF1E232E)
val DarkOutline = Color(0xFF2A303D)
val DarkSurfaceContainerLow = Color(0xFF11141B)

// ── 深色文字三级 ───────────────────────────────────────────────
val DarkInk = Color(0xFFF2F4F8)
val DarkInk2 = Color(0xFFA8B0C0)
val DarkInk3 = Color(0xFF6B7488)

// ── 浅色 ───────────────────────────────────────────────────────
val LightBackground = Color(0xFFF7F8FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurface2 = Color(0xFFF1F3F6)
val LightOutline = Color(0xFFE2E5EB)
val LightInk = Color(0xFF14161C)
val LightInk2 = Color(0xFF545A68)
val LightInk3 = Color(0xFF8B909D)

// ── 渠道健康度 ─────────────────────────────────────────────────
val HealthOk = SuccessGreen
val HealthPartial = QualityAmber
val HealthDown = DarkInk3

/**
 * 标签配色：全部取自 Material 配色，不写死颜色。
 *
 * 放在 theme 包里，收藏页和共享弹窗组件都能复用同一套配色。
 */
@Composable
fun tagColor(colorIndex: Int): Color {
    val scheme = MaterialTheme.colorScheme
    return when (((colorIndex % 5) + 5) % 5) {
        0 -> scheme.primary
        1 -> scheme.secondary
        2 -> scheme.tertiary
        3 -> scheme.error
        else -> scheme.onSurfaceVariant
    }
}
