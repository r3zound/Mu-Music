package com.r3zound.music.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.r3zound.music.feature.downloads.DownloadsScreen
import com.r3zound.music.feature.favorites.FavoritesScreen
import com.r3zound.music.feature.favorites.PlaylistDetailScreen
import com.r3zound.music.feature.home.HomeScreen
import com.r3zound.music.feature.lists.PlaylistsScreen
import com.r3zound.music.feature.player.PlayerScreen
import com.r3zound.music.feature.player.QueueScreen
import com.r3zound.music.feature.settings.ChannelHealthScreen
import com.r3zound.music.feature.settings.SettingsScreen

object Routes {
    /**
     * 首页。
     *
     * 搜索是首页的一个子版块（「发现 / 搜索」），所以没有独立的搜索路由。
     * 跨页面触发搜索走 [SearchNavState]，避免生成新的 back stack entry
     * 把搜索结果的上下文切断。
     */
    const val HOME = "home"

    /** 播放列表：我的歌单 + 最近播放。 */
    const val PLAYLISTS = "playlists"

    /** 收藏：全部歌曲 + 标签。 */
    const val FAVORITES = "favorites"

    /** 下载管理（从设置页进入，不是底栏入口）。 */
    const val DOWNLOADS = "downloads"

    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val CHANNEL_HEALTH = "channel_health"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    const val QUEUE = "queue"

    fun playlistDetail(id: Long) = "playlist/$id"
}

@Composable
fun MuNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(navController) }
        composable(Routes.PLAYLISTS) { PlaylistsScreen(navController) }
        composable(Routes.FAVORITES) { FavoritesScreen(navController) }
        composable(Routes.DOWNLOADS) { DownloadsScreen(navController) }
        composable(Routes.SETTINGS) { SettingsScreen(navController) }
        composable(Routes.PLAYER) { PlayerScreen(navController) }
        composable(Routes.CHANNEL_HEALTH) { ChannelHealthScreen(navController) }
        composable(Routes.QUEUE) { QueueScreen(navController) }
        composable(
            route = Routes.PLAYLIST_DETAIL,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("playlistId") ?: 0L
            PlaylistDetailScreen(navController, playlistId = id)
        }
    }
}
