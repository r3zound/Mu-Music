package com.r3zound.music.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = BrandRedDark,
    onPrimaryContainer = Color.White,
    secondary = QualityAmber,
    onSecondary = Color.Black,
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = DarkInk2,
    surfaceContainerLowest = DarkSurfaceContainerLow,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurface2,
    surfaceContainerHighest = DarkSurface2,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = WarningRed,
    onError = Color.White,
    scrim = Color.Black,
)

private val LightScheme = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3E4),
    onPrimaryContainer = Color(0xFF7A171B),
    secondary = Color(0xFFB07000),
    onSecondary = Color.White,
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = LightInk2,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurface2,
    surfaceContainerHighest = LightSurface2,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = WarningRed,
    onError = Color.White,
)

/** 语义辅助色不属于 Material color scheme，单独提供便于按语义取用。 */
object MuPalette {
    val qualityAmber: Color
        @Composable get() = MaterialTheme.colorScheme.secondary
    val success: Color
        @Composable get() = MaterialTheme.colorScheme.tertiary
    val ink2: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val ink3: Color
        @Composable get() = if (isDark()) DarkInk3 else LightInk3
    val surface2: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val outlineSoft: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant
}

@Composable
private fun isDark(): Boolean = MaterialTheme.colorScheme.background == DarkBackground

@Composable
fun MuMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            // 导航栏图标也跟着 APP 主题走，避免系统是浅色、APP 是深色时图标看不清
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = MuTypography,
        content = content,
    )
}
