package com.r3zound.music.ui.nav

import android.net.Uri
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
import com.r3zound.music.feature.player.PlayerScreen
import com.r3zound.music.feature.player.QueueScreen
import com.r3zound.music.feature.search.SearchScreen
import com.r3zound.music.feature.settings.ChannelHealthScreen
import com.r3zound.music.feature.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"

    /** 带初始关键词的搜索页。用于「点歌手」「点分类」「点历史」等跳转。 */
    const val SEARCH_KEYWORD = "search/keyword/{keyword}?providers={providers}"

    const val FAVORITES = "favorites"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val CHANNEL_HEALTH = "channel_health"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    const val QUEUE = "queue"

    fun playlistDetail(id: Long) = "playlist/$id"

    /** 跳到搜索页并自动用该关键词搜一次。 */
    fun searchWithKeyword(keyword: String): String =
        "search/keyword/${Uri.encode(keyword)}"

    /** 跳到搜索页，并限定参与检索的渠道（首页分类用）。 */
    fun searchWithKeyword(keyword: String, providers: List<String>): String =
        "search/keyword/${Uri.encode(keyword)}?providers=${providers.joinToString(",")}"
}

@Composable
fun MuNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(navController) }
        composable(Routes.SEARCH) { SearchScreen(navController) }
        composable(
            route = Routes.SEARCH_KEYWORD,
            arguments = listOf(
                navArgument("keyword") { type = NavType.StringType },
                navArgument("providers") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val keyword = entry.arguments?.getString("keyword").orEmpty()
            // 查询参数由 Uri.getQueryParameters() 取出，已是解码后的原值
            val providers = entry.arguments?.getString("providers")
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            SearchScreen(
                navController = navController,
                initialKeyword = keyword,
                initialProviders = providers,
            )
        }
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
