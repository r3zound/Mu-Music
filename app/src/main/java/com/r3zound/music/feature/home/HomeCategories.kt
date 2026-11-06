package com.r3zound.music.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.r3zound.music.data.remote.ProviderRegistry

/**
 * 首页分类。
 *
 * **重要前提**：音源站只提供 `/api/search` 一个检索入口，
 * 没有榜单 / 歌单 / 分类接口（上游本身就是纯搜索下载器）。
 * 因此这里的每个分类本质上是「关键词 × 渠道组合」的搜索预设，
 * 点进去看到的是按该关键词聚合出来的曲目，而不是网易云那样的真榜单。
 *
 * @param keywords 主关键词在前；同一次会话里重复点同一个分类会自动轮换到下一个，
 *                 避免每次都是同一批结果。
 * @param providers 参与检索的渠道，只挑该品类上实际有资源的渠道，减少无效请求。
 */
data class HomeCategory(
    val id: String,
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val keywords: List<String>,
    val providers: List<String>,
) {
    val primaryKeyword: String get() = keywords.first()
}

/** 首页的分类预设表。 */
object HomeCategories {

    private val music = listOf(
        ProviderRegistry.netease,
        ProviderRegistry.qq,
        ProviderRegistry.kugou,
    )

    private val broad = music + ProviderRegistry.migu

    val all: List<HomeCategory> = listOf(
        HomeCategory(
            id = "ranking",
            title = "排行榜",
            desc = "热歌 · 飙升 · 经典",
            icon = Icons.Outlined.Star,
            keywords = listOf("热歌榜", "飙升榜", "热歌推荐"),
            providers = broad,
        ),
        HomeCategory(
            id = "playlist",
            title = "歌单",
            desc = "合集 · 精选",
            icon = Icons.Outlined.Album,
            keywords = listOf("精选歌单", "歌单合集", "好听的歌单"),
            providers = music,
        ),
        HomeCategory(
            id = "chinese",
            title = "华语",
            desc = "华语 · 粤语",
            icon = Icons.Outlined.Language,
            keywords = listOf("华语热歌", "华语经典", "粤语歌"),
            providers = broad,
        ),
        HomeCategory(
            id = "love",
            title = "情歌",
            desc = "告白 · 想念",
            icon = Icons.Filled.Favorite,
            keywords = listOf("情歌", "情歌推荐", "想你的歌"),
            providers = broad,
        ),
        HomeCategory(
            id = "western",
            title = "欧美",
            desc = "英文 · 流行",
            icon = Icons.Filled.Public,
            keywords = listOf("欧美音乐", "英文歌", "欧美流行"),
            providers = music,
        ),
        HomeCategory(
            id = "kids",
            title = "儿歌",
            desc = "儿童 · 卡通",
            icon = Icons.Filled.ChildCare,
            keywords = listOf("儿歌", "儿童歌曲", "卡通儿歌"),
            providers = broad,
        ),
        HomeCategory(
            id = "classic",
            title = "经典",
            desc = "老歌 · 怀旧",
            icon = Icons.Outlined.History,
            keywords = listOf("经典老歌", "怀旧金曲", "经典歌曲"),
            providers = broad,
        ),
        HomeCategory(
            id = "edm",
            title = "电音",
            desc = "EDM · Remix",
            icon = Icons.Outlined.Bolt,
            keywords = listOf("电音", "EDM", "电子音乐"),
            providers = music,
        ),
        HomeCategory(
            id = "rap",
            title = "说唱",
            desc = "Hip-Hop · 嘻哈",
            icon = Icons.Filled.Mic,
            keywords = listOf("说唱", "Rap", "嘻哈"),
            providers = broad,
        ),
    )

    fun byId(id: String): HomeCategory? = all.firstOrNull { it.id == id }
}
