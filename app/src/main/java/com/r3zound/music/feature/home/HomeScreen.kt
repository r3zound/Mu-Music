package com.r3zound.music.feature.home

import com.r3zound.music.ui.nav.SearchRequest
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.r3zound.music.ui.components.IconAction
import com.r3zound.music.ui.components.FavTab
import com.r3zound.music.feature.search.SearchContent
import com.r3zound.music.feature.search.SearchViewModel
import com.r3zound.music.ui.components.SectionHeader
import com.r3zound.music.ui.nav.Routes
import com.r3zound.music.ui.nav.SearchNavState
import com.r3zound.music.ui.nav.rememberSearchNavState
import com.r3zound.music.ui.theme.MuPalette

/**
 * 首页（类似网易云的「推荐」页）。
 *
 * 结构：顶部标题 → 快捷入口 → 分类宫格 → 最近搜索。
 * 点分类会带着「该分类的关键词 + 渠道组合」跳到搜索页，
 * 直接复用搜索页已有的渠道筛选、分组、质量选择与批量操作。
 */
/** 首页的子版块。 */
enum class HomeTab {
    /** 发现：快捷入口 + 分类 + 最近搜索 */
    DISCOVER,

    /** 搜索：搜索框 + 历史 + 结果 */
    SEARCH,
}

@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchNavState = rememberSearchNavState()
    var tab by remember { mutableStateOf(HomeTab.DISCOVER) }

    // 其他页面（收藏页、歌单详情页…）点歌手名/分类时发起的跨页面搜索请求。
    // 搜索在首页的子版块里，所以这里负责切 tab 并执行搜索。
    LaunchedEffect(Unit) {
        searchNavState.request.collect { req: SearchRequest? ->
            if (req != null) {
                tab = HomeTab.SEARCH
                searchViewModel.searchWithProviders(req.keyword, req.providers)
                searchNavState.consume()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HomeTabRow(
            tab = tab,
            onSelect = { tab = it },
        )

        when (tab) {
            HomeTab.DISCOVER -> DiscoverContent(
                navController = navController,
                state = state,
                viewModel = viewModel,
                searchNavState = searchNavState,
            )

            HomeTab.SEARCH -> SearchContent(
                navController = navController,
                viewModel = searchViewModel,
                showTitle = false,
            )
        }
    }
}

/** 首页的 tab 行：「发现 / 搜索」。 */
@Composable
private fun HomeTabRow(
    tab: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FavTab(
            label = "发现",
            selected = tab == HomeTab.DISCOVER,
            onClick = { onSelect(HomeTab.DISCOVER) },
        )
        FavTab(
            label = "搜索",
            selected = tab == HomeTab.SEARCH,
            onClick = { onSelect(HomeTab.SEARCH) },
        )
    }
}

/** 「发现」子版块的内容。 */
@Composable
private fun DiscoverContent(
    navController: NavHostController,
    state: HomeUiState,
    viewModel: HomeViewModel,
    searchNavState: SearchNavState,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 18.dp),
        ) {
            // 未配置音源站地址：先给引导，否则用户点了分类只会看到一堆报错
            if (state.baseUrl.isBlank()) {
                item(key = "setup") {
                    SetupBanner(onClick = { navController.navigate(Routes.SETTINGS) })
                }
            }

            item(key = "shortcuts") {
                ShortcutRow(
                    onDefaultPlaylist = {
                        val id = state.defaultPlaylistId
                        if (id > 0L) navController.navigate(Routes.playlistDetail(id))
                    },
                    onFavorites = { navController.navigate(Routes.FAVORITES) },
                    onPlaylists = { navController.navigate(Routes.PLAYLISTS) },
                )
            }

            item(key = "category-header") {
                SectionHeader(
                    title = "分类",
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }

            item(key = "categories") {
                // 宫格用固定列数，避免在 LazyColumn 里嵌套同向滚动容器
                Column(
                    Modifier.padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeCategories.all.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { category ->
                                CategoryTile(
                                    category = category,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        // 搜索是首页的子版块，直接切过去搜，不做路由跳转
                                        searchNavState.request(
                                            keyword = viewModel.keywordFor(category),
                                            providers = category.providers.toSet(),
                                        )
                                    },
                                )
                            }
                            // 补齐最后一行缺失的格子
                            repeat(3 - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (state.recentKeywords.isNotEmpty()) {
                item(key = "recent-header") {
                    SectionHeader(
                        title = "最近搜索",
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
                item(key = "recent") {
                    RecentKeywordRow(
                        keywords = state.recentKeywords,
                        onPick = { searchNavState.request(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    onDefaultPlaylist: () -> Unit,
    onFavorites: () -> Unit,
    onPlaylists: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        ShortcutTile(
            icon = Icons.Filled.Bookmark,
            label = "默认收藏",
            onClick = onDefaultPlaylist,
            modifier = Modifier.weight(1f),
        )
        ShortcutTile(
            icon = Icons.Outlined.BookmarkBorder,
            label = "我的收藏",
            onClick = onFavorites,
            modifier = Modifier.weight(1f),
        )
        ShortcutTile(
            icon = Icons.Filled.QueueMusic,
            label = "播放列表",
            onClick = onPlaylists,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ShortcutTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MuPalette.surface2)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 分类格子。 */
@Composable
private fun CategoryTile(
    category: HomeCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(MuPalette.surface2)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = category.title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = category.desc,
            fontSize = 9.5.sp,
            color = MuPalette.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 最近搜索关键词。 */
@Composable
private fun RecentKeywordRow(
    keywords: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        keywords.take(3).forEach { keyword ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(MuPalette.surface2)
                    .clickable { onPick(keyword) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = keyword,
                    fontSize = 11.5.sp,
                    color = MuPalette.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 未配置音源站地址时的引导卡片。 */
@Composable
private fun SetupBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Dns,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "还没有配置音源站地址",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "点这里填写服务器地址后才能开始搜索",
                fontSize = 11.sp,
                color = MuPalette.ink3,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MuPalette.ink3,
            modifier = Modifier.size(18.dp),
        )
    }
}
