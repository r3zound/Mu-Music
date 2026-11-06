# Mu 音乐

> 面向**自建音源站**的原生 Android 音乐搜索与下载客户端。
> Kotlin 2.0 + Jetpack Compose + Material 3 + Media3 + Room + WorkManager。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)

---

## ⚠️ 使用前必读

**本项目不内置、也不推荐任何特定的音源站。**
首次启动请到「设置 → 服务器地址」填入**你自己有权访问的**音源站，
否则搜索、播放、下载都无法进行（会明确提示「未设置音源站地址」）。

本项目只提供客户端。服务端接口协议见 [`docs/01-需求对齐与方案.md`](docs/01-需求对齐与方案.md)。

---

## 📦 下载

| 文件 | 说明 |
|---|---|
| [`releases/MuMusic-1.4.0.apk`](releases/MuMusic-1.4.0.apk) | 直接安装的 APK（4.0 MB） |
| [`releases/MuMusic-1.4.0-release.zip`](releases/MuMusic-1.4.0-release.zip) | APK + 插件 + 说明文档 |
| [`releases/MuMusic-source-1.4.0.tar.gz`](releases/MuMusic-source-1.4.0.tar.gz) | 源码快照 |
| [`releases/moresound.js`](releases/moresound.js) | MusicFree 音源插件（见下） |

**安装**：下载 APK → 允许「安装未知来源应用」→ 安装。
包名 `com.r3zound.music`，minSdk 24（Android 7.0），targetSdk 34。

### 附：MusicFree 插件

[`releases/moresound.js`](releases/moresound.js) 是把同音源站接进
[MusicFree](https://github.com/maotoumao/MusicFree) 的单文件插件——
如果你更喜欢 MusicFree 的界面，用这个更快。详见
[`docs/07-MusicFree插件使用说明.md`](docs/07-MusicFree插件使用说明.md)。

---

## ✨ 功能

| 模块 | 能力 |
|---|---|
| **首页** | 9 个分类入口（排行榜/歌单/华语/情歌/欧美/儿歌/经典/电音/说唱）、快捷入口、最近搜索 |
| **搜索** | 多渠道并发聚合搜索、按渠道分组或合并去重、动态音质选择器、多选批量下载 / 批量打标签、**点歌手名直接搜该歌手** |
| **播放** | Media3 后台播放、锁屏/通知栏控制、滚动歌词（毫秒级对齐）、播放队列、倍速与循环、**封面/歌词分页（单击封面切歌词）**、**歌词点击跳转 + 字号/偏移校正**、**睡眠定时器**、**Mini Player 滑动切歌**、**分享歌曲**、**模糊封面背景** |
| **收藏夹** | 自建歌单（无限个 + 自定义封面 + 拖拽排序）、标签体系、长按单曲加入歌单/打标签、曲目元数据完整落库、一键下载整个歌单、**内置「默认收藏」——搜索结果播放时自动入库**、**最近播放历史** |
| **下载** | 后台队列、**直链优先 + Range 断点续传**、代理兜底、进度通知、仅 WiFi 下载、已下载管理 |
| **设置** | 音源地址可切换、默认音质、并发数与重试次数、主题（跟随系统/浅色/深色）、**一键渠道体检**、**收藏夹 JSON 导出 / 导入** |

---

## 🔧 核心设计：下载策略

线上实测的结论直接决定了下载模块的实现：

| 通道 | 速度 | Range 断点续传 | 直链有效期 |
|---|---|---|---|
| `/api/url` → CDN 直链 | **7.6–64.8 MB/s** | ✅ 206 Partial Content | 约 30 分钟（超时后 403） |
| `/api/download` → 服务端代理 | 1–10 MB/s | ❌ 恒返回 200 | — |

因此下载流程是：

```
① /api/url 取直链 → 直接下载（支持 Range）
② 直链 403/410 → 重新取直链 → 带 Range 从断点续传（不整包重来）
③ 直链彻底失败 → 回退 /api/download 代理
④ 落盘前按 body 魔数判定真实格式（服务端 Content-Type 恒为 audio/mpeg，即使内容是 flac）
```

另外两个必须遵守的约束：

- **`extra` 必须原样透传**。搜索返回的 `extra` 里带着音质档位与解析器上下文，
  取流/下载/歌词都要把它 JSON 化后原样回传，否则音质降级或取流失败。
- **服务端会对未知渠道静默回落**。APP 收到结果后必须比对 `items[].provider`
  与请求的 provider，不一致就在 UI 上打「已回落」徽标。

---

## 🏗️ 构建

需要 **JDK 17 + Android SDK 34**：

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

签名配置在 `app/build.gradle.kts` 的 `signingConfigs.release`。

> **密钥库不要提交到 GitHub**（`.gitignore` 已经把 `*.jks` 排除了）。
> 自己生成一把，然后把 `app/build.gradle.kts` 里的口令改掉：
>
> ```bash
> keytool -genkeypair -v -keystore app/mu-release.jks -alias mu \
>         -keyalg RSA -keysize 2048 -validity 10950 \
>         -storepass <你的口令> -keypass <你的口令>
> ```

### 构建期踩过的坑（改代码前先读）

1. **R8 版本**：AGP 8.5.2 自带的 R8 8.5.35 在 tree-shaking 阶段会抛
   `ConcurrentModificationException`。根目录 `build.gradle.kts` 里显式覆盖成
   `com.android.tools:r8:8.7.18` 绕过。
2. **资源压缩已关闭**：`isShrinkResources = false`。
3. **Gradle 构建缓存关闭**：`org.gradle.caching=false`，避免增量产物与磁盘状态不一致。
4. **包名改了要清缓存**：Hilt 的聚合任务会缓存旧的 `android:name`，换包名后
   必须 `rm -rf app/build` 再构建，否则报 `Could not find class file for 'xxx'`。
5. **proguard 规则里的包名要跟着改**：`com.coco.music.**` 换成新包名后，
   序列化保留规则和 `PlaybackService` 保留规则会全部失效——**只会在 release 包炸**。

---

## 📂 目录结构

```
.
├── app/                          Android 工程
│   └── src/main/java/com/r3zound/music/
│       ├── core/
│       │   ├── model/MusicItem.kt      领域模型 + 音频格式魔数判定
│       │   └── util/                   直链安全策略 / 音质解析 / 协程工具
│       ├── data/
│       │   ├── remote/                 API 客户端 + 渠道注册表
│       │   ├── local/                  Room：歌单/收藏/标签/下载任务/历史
│       │   ├── prefs/SettingsStore.kt  DataStore 全局设置
│       │   └── repo/                   Music / Favorite / Download 仓库
│       ├── media/                      Media3 播放核心 + 占位 URI 解析
│       ├── download/                   WorkManager 下载队列
│       ├── feature/                    各页面（home/search/favorites/player/...）
│       └── ui/                         通用组件 / 主题 / 导航
├── docs/                          开发文档（需求 → 方案 → 逐版本演进）
├── design/UI设计稿.html            交互设计稿（单文件，可直接浏览器打开）
├── releases/                      APK / 插件 / 源码快照
└── scripts/
    ├── gen_icon.py                重新生成启动图标
    ├── moresound.js               MusicFree 插件源码
    └── test_plugin.mjs            插件离线自测（会真打线上接口）
```

---

## 📚 开发文档

按时间顺序读，能看清这个项目是怎么长出来的：

| 文档 | 内容 |
|---|---|
| [`01-需求对齐与方案.md`](docs/01-需求对齐与方案.md) | 需求对齐、线上实测、API 契约、架构设计 |
| [`02-v1.1-新增功能说明.md`](docs/02-v1.1-新增功能说明.md) | 首页分类 / 歌手可点 / 默认收藏 / 封面歌词分页 |
| [`03-v1.2-修复说明.md`](docs/03-v1.2-修复说明.md) | 换包名、地址留空、深色主题修复、状态栏适配等 |
| [`04-v1.3-第一批借鉴说明.md`](docs/04-v1.3-第一批借鉴说明.md) | 正在播放高亮 / 定位准星 / 歌词跳转 / 模糊背景 / 睡眠定时 |
| [`05-v1.4-第二批借鉴说明.md`](docs/05-v1.4-第二批借鉴说明.md) | 播放历史 / 歌词字号偏移 / 滑动切歌 / 分享 |
| [`06-MusicFree设计对照分析.md`](docs/06-MusicFree设计对照分析.md) | 与 MusicFree 的 20 项逐条对照（含取舍理由） |
| [`07-MusicFree插件使用说明.md`](docs/07-MusicFree插件使用说明.md) | MusicFree 插件安装与使用 |
| [`design/UI设计稿.html`](design/UI设计稿.html) | 交互设计稿 |

---

## 📄 许可

本项目以 **MIT** 协议开源，见 [`LICENSE`](LICENSE)。

服务端参考实现见 [`markcxx/coco-downloader`](https://github.com/markcxx/coco-downloader)（MIT）。
界面与部分交互设计参考了 [`maotoumao/MusicFree`](https://github.com/maotoumao/MusicFree)（AGPL-3.0）——
本项目为独立实现，未复制其代码。

> 请使用你有权访问的服务，并遵守所在地法律。
