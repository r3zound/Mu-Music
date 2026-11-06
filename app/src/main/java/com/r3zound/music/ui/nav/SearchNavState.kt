package com.r3zound.music.ui.nav

import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 一次「去搜索」的请求。 */
data class SearchRequest(
    val keyword: String,
    /** 限定参与检索的渠道；空表示用体检自动勾选的结果。 */
    val providers: Set<String> = emptySet(),
    /** 用于区分连续两次相同关键词的请求。 */
    val stamp: Long = System.nanoTime(),
)

/**
 * 跨页面的搜索请求通道。
 *
 * 搜索现在是**首页的一个子版块**，不再有独立的搜索路由。
 * 但「点歌手名」「点分类」这些入口散布在收藏页、歌单详情页、播放列表页，
 * 它们需要跨页面触发首页里的搜索——用路由参数传会生成新的 back stack entry，
 * ViewModel 作用域就断了（结果会显示在一个没有上下文的孤立页面里）。
 *
 * 所以用这个单例做中转：任何页面 `request(keyword)`，首页观察到后切到搜索子版块并执行。
 */
@Singleton
class SearchNavState @Inject constructor() {

    private val _request = kotlinx.coroutines.flow.MutableStateFlow<SearchRequest?>(null)
    val request = _request.asStateFlow()

    /** 请求搜索某个关键词。 */
    fun request(keyword: String, providers: Set<String> = emptySet()) {
        if (keyword.isBlank()) return
        _request.value = SearchRequest(keyword, providers)
    }

    /** 消费掉当前请求（首页处理完调用，避免重复触发）。 */
    fun consume() {
        _request.value = null
    }
}
