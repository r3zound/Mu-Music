// R8 版本覆盖：AGP 8.5.2 自带的 R8 8.5.35 在 tree-shaking 阶段会抛
// java.util.ConcurrentModificationException（已复现，开启/关闭 shrinkResources 均复现）。
// 这里显式声明更新的 R8 来绕开该 bug。
buildscript {
    dependencies {
        classpath("com.android.tools:r8:8.7.18")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
