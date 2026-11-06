package com.r3zound.music.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 取得全局的 [SearchNavState]。
 *
 * 它是 @Singleton，而 `hiltViewModel()` 只能取 @HiltViewModel，
 * 所以用 Hilt 的 EntryPoint 方式从 Application 拿。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SearchNavEntryPoint {
    fun searchNavState(): SearchNavState
}

@Composable
fun rememberSearchNavState(): SearchNavState {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SearchNavEntryPoint::class.java,
        ).searchNavState()
    }
}
