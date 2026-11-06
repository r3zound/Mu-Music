# ── 通用：保留注解与内部类信息（Room / kotlinx.serialization / Hilt 都依赖）──
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault

# ── kotlinx.serialization ──
-dontnote kotlinx.serialization.**
-keepclassmembers class com.r3zound.music.** { *** Companion; }
-keepclasseswithmembers class com.r3zound.music.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.r3zound.music.**$$serializer { *; }
# 序列化字段名可能被混淆，改名后 JSON 键对不上，强制保留
-keepclassmembers class com.r3zound.music.** {
    @kotlinx.serialization.SerialName <fields>;
}

# ── Hilt / Dagger ──
# 聚合任务会移除「没有直接引用」的 @AndroidEntryPoint 组件，
# 而 Hilt 是在运行时按类名反射创建的，必须显式保留，否则 release 包运行即崩。
-keepclasseswithmembers,includedescriptorclasses class * {
    @dagger.hilt.android.AndroidEntryPoint <init>(...);
}
-keepclasseswithmembers,includedescriptorclasses class * {
    @dagger.hilt.android.HiltAndroidApp <init>(...);
}
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class * extends dagger.hilt.internal.GeneratedComponentManager

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ── WorkManager / Hilt Worker ──
# 注意：`-keep class X { <init>(...); }` 会保留**类名**（R8 不重命名被 keep 的类），
# HiltWorkerFactory 正是按 Worker 的类名反射查表的，所以类名不能被混淆。
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class com.r3zound.music.download.DownloadWorker { *; }

# ── Media3 ──
# MediaSessionService 会被系统按类名反射实例化
-keep class androidx.media3.session.** { *; }
-keep class com.r3zound.music.media.PlaybackService { *; }
