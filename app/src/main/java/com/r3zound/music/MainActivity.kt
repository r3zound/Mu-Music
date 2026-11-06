package com.r3zound.music

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.r3zound.music.media.PlayerHolder
import com.r3zound.music.ui.components.MiniPlayer
import com.r3zound.music.ui.nav.MuNavHost
import com.r3zound.music.data.prefs.SettingsStore
import com.r3zound.music.data.prefs.ThemeMode
import com.r3zound.music.ui.nav.Routes
import com.r3zound.music.ui.theme.MuMusicTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
    }

    @Inject
    lateinit var playerHolder: PlayerHolder

    @Inject
    lateinit var settingsStore: SettingsStore

    /**
     * 下载通知点击后要求跳到下载页。
     *
     * 下载通知会带上 [EXTRA_OPEN_DOWNLOADS]，这里消费它并跳页；
     * 消费完立刻置回 false，避免旋转屏幕等重建时重复跳。
     */
    private val openDownloads = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openDownloads.value = intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) ?: false
        setContent {
            // 主题模式从设置里读：跟随系统 / 浅色 / 深色
            val themeMode by settingsStore.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            MuMusicTheme(darkTheme = dark) {
                MuApp(playerHolder, openDownloads)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openDownloads.value = intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)
    }
}

private data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem(Routes.HOME, "首页", Icons.Filled.Home, Icons.Outlined.Home),
    NavItem(Routes.SEARCH, "搜索", Icons.Filled.Search, Icons.Outlined.Search),
    NavItem(Routes.FAVORITES, "收藏", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
    NavItem(Routes.DOWNLOADS, "下载", Icons.Filled.Download, Icons.Outlined.Download),
    NavItem(Routes.SETTINGS, "设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun MuApp(playerHolder: PlayerHolder, openDownloads: MutableStateFlow<Boolean>) {
    val navController = rememberNavController()
    val playback by playerHolder.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 全屏播放页自带导航，隐藏底栏与 Mini Player
    val isFullScreen = currentRoute == Routes.PLAYER

    // 下载通知跳转：消费一次即可
    LaunchedEffect(Unit) {
        openDownloads.collect { need ->
            if (need) {
                navController.navigate(Routes.DOWNLOADS)
                openDownloads.value = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!isFullScreen) {
                Column {
                    MiniPlayer(
                        item = playback.current,
                        isPlaying = playback.isPlaying,
                        onTogglePlay = { playerHolder.togglePlayPause() },
                        onNext = { playerHolder.next() },
                        onOpen = { navController.navigate(Routes.PLAYER) },
                        // 横向滑动切歌
                        onSwipe = { direction ->
                            if (direction > 0) playerHolder.next() else playerHolder.previous()
                        },
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                    ) {
                        navItems.forEach { item ->
                            // 「搜索」有两个路由：search 与 search/keyword/{keyword}，
                            // 带关键词进来时底栏也要保持在「搜索」上
                            val selected = currentRoute == item.route ||
                                currentRoute?.startsWith(item.route + "/") == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.icon,
                                        contentDescription = item.label,
                                        modifier = Modifier.padding(bottom = 0.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                // 边到边显示时内容会顶进状态栏，这里统一避让
                .statusBarsPadding()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            MuNavHost(navController)
        }
    }
}
