package com.r3zound.music.core.util

import kotlinx.coroutines.CancellationException

/**
 * 执行 [block] 并吞掉所有异常，返回 null 表示失败。
 *
 * 与 `runCatching` 的区别：**放行 CancellationException**。
 * `runCatching` 会把协程取消也当成失败吞掉，破坏结构化并发，
 * 在 `viewModelScope.launch` 里会导致 ViewModel 取消后协程仍继续跑。
 * 本工程里所有「失败也不能影响主流程」的地方都必须走这里。
 */
suspend inline fun <T> ignoreErrors(crossinline block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    null
}
