package com.r3zound.music.feature.home

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
import androidx.compose.material.icons.outlined.Settings
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
import com.r3zound.music.ui.components.SectionHeader
import com.r3zound.music.ui.nav.Routes
import com.r3zound.music.ui.theme.MuPalette

/**
 * 首页（类似网易云的「推荐」页）。
 *
 * 结构：顶部标题 → 快捷入口 → 分类宫格 → 最近搜索。
 * 点分类会带着「该分类的关键词 + 渠道组合」跳到搜索页，
 * 直接复用搜索页已有的渠道筛选、分组、质量选择与批量操作。
 */
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HomeTopBar(onSettings = { navController.navigate(Routes.SETTINGS) })

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
                    onDownloads = { navController.navigate(Routes.DOWNLOADS) },
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
                                        navController.navigate(
                                            Routes.searchWithKeyword(
                                                keyword = viewModel.keywordFor(category),
                                                providers = category.providers,
                                            )
                                        ) {
                                            // 同一个分类连点两次不要压出两层同样的页面
                                            launchSingleTop = true
                                        }
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
                        onPick = { navController.navigate(Routes.searchWithKeyword(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "发现",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconAction(icon = Icons.Outlined.Settings, tint = MuPalette.ink2, onClick = onSettings)
    }
}

/** 快捷入口：最近播放 / 我的收藏 / 下载管理。 */
@Composable
private fun ShortcutRow(
    onDefaultPlaylist: () -> Unit,
    onFavorites: () -> Unit,
    onDownloads: () -> Unit,
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
            label = "下载管理",
            onClick = onDownloads,
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
