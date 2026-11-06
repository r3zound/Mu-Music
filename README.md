# Mu 音乐 — Android 客户端

面向**自建音源站**的原生 Android 音乐搜索与下载客户端。

> **不内置任何服务器地址。** 首次启动请到「设置 → 服务器地址」填入你自己的音源站，
> 否则搜索、播放、下载都无法进行。本项目开源在 <https://github.com/r3zound/Mu-Music>。

Kotlin 2.0 + Jetpack Compose + Material 3 + Media3 + Room + WorkManager。

**底栏结构**：首页（发现 / 搜索）· 播放列表（我的歌单 / 最近播放）· 收藏（全部歌曲 / 标签）· 设置

---

## 一、它能做什么

| 模块 | 能力 |
|---|---|
| **首页** | 两个子版块：**发现**（9 个分类入口 / 快捷入口 / 最近搜索）+ **搜索**（多渠道并发聚合、按渠道分组或合并去重、动态音质选择器、多选批量下载与打标签、**点歌手名直接搜该歌手**） |
| **播放列表** | **我的歌单**（新建/重命名/改描述/删除）+ **最近播放**（播放历史）、**把当前播放队列存成歌单** |
| **播放** | Media3 后台播放、锁屏/通知栏控制、滚动歌词（毫秒级对齐）、播放队列、倍速与循环、**封面/歌词分页（单击封面切歌词）**、**歌词点击跳转 + 字号/偏移校正**、**睡眠定时器**、**Mini Player 滑动切歌**、**分享歌曲** |
| **收藏夹** | 自建歌单（无限个 + 自定义封面 + 拖拽排序）、标签体系、长按单曲加入歌单/打标签、曲目元数据完整落库、一键下载整个歌单、**内置「默认收藏」——搜索结果播放时自动入库**、**最近播放历史** |
| **下载** | 后台队列、**直链优先 + Range 断点续传**、代理兜底、进度通知、仅 WiFi 下载、已下载管理 |
| **设置** | 音源地址可切换、默认音质、并发数与重试次数、主题、**一键渠道体检**（体检结果自动勾选参与搜索的渠道）、**下载管理入口**、**收藏夹 JSON 导出 / 导入** |

---

## 二、下载策略（这是本项目的核心设计）

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
- **服务端会对未知渠道静默回落 netease**。APP 收到结果后必须比对 `items[].provider`
  与请求的 provider，不一致就在 UI 上打「已回落」徽标——否则用户搜 QQ 音乐
  拿到的是网易结果却毫无察觉。

---

## 三、渠道现状

线上共 17 个渠道，实测（2026-11）真正可用的只有 5 个：

| 渠道 | 名称 | 搜索 | 取流 | 续传 |
|---|---|---|---|---|
| `netease` | 云音乐 | ✅ | ✅ flac | ✅ |
| `bodian` | 波点 | ✅ | ✅ flac | ✅ |
| `qqmp3` | QQMP3 | ✅ | ✅ mp3 | ✅ |
| `mitu` | 米兔 | ✅ | ✅ mp3 | ✅ |
| `migu` | 咪咕 | ✅ | ✅ flac | ❌ |
| `qq` / `kugou` / `livepoo` | XCVTS / 海棠 / 力音 | ✅ | ❌ 500 | - |
| `gequbao` | 歌曲宝 | ⚠️ 部分 | ❌ | - |
| `gequhai` `bugu` `mitu` `joox` `aiting` `jianbin-*` | 其它 | ❌ 空/超时 | - | - |

渠道可用性随时会变，所以设置页内置了**一键渠道体检**：并发探测每个渠道的
「搜索 + 取流」，把结果缓存下来，搜索页的渠道 chip 上直接显示健康指示点。

---

## 四、构建

```bash
# 需要 JDK 17 + Android SDK 34
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

### 构建期踩过的两个坑

1. **R8 版本**：AGP 8.5.2 自带的 R8 8.5.35 在 tree-shaking 阶段会抛
   `java.util.ConcurrentModificationException`（开/关 `shrinkResources` 都复现）。
   根目录 `build.gradle.kts` 里显式覆盖成 `com.android.tools:r8:8.7.18` 绕过。
2. **资源压缩已关闭**：`isShrinkResources = false`。开启时在部分 AGP 版本上会连带触发
   资源相关的合并异常，且省下的体积有限（release APK 本身只有 4.1 MB）。

另外 `gradle.properties` 里关掉了 Gradle 构建缓存（`org.gradle.caching=false`），
避免增量产物与磁盘状态不一致。

---

## 五、开源相关

- **不内置任何服务器地址**：首次启动请到「设置 → 服务器地址」填自己的音源站，
  没填之前搜索/播放/下载都会提示「未设置音源站地址」
- **关于页只有一个对外链接**：<https://github.com/r3zound/Mu-Music>
- `scripts/` 目录：
  - `gen_icon.py` —— 重新生成启动图标（改配色/形状后 `python3 scripts/gen_icon.py`）
  - `moresound.js` —— 同一套源的 MusicFree 插件
  - `test_plugin.mjs` —— 插件离线自测（`node scripts/test_plugin.mjs`，会真打线上接口）

## 六、目录结构

```
app/src/main/java/com/r3zound/music/
├── core/
│   ├── model/MusicItem.kt        领域模型 + 音频格式魔数判定
│   └── util/SafeUrl.kt           直链安全策略（强制 https、拒内网）
├── data/
│   ├── remote/                   API 客户端 + 渠道注册表
│   ├── local/                    Room：歌单/收藏/标签/下载任务/历史
│   ├── prefs/SettingsStore.kt    DataStore 全局设置
│   └── repo/                     Music / Favorite / Download 仓库
├── media/                        Media3 播放服务、PlayerHolder、解析型 DataSource
├── download/                     WorkManager 下载、FileStore、通知
├── feature/                      search / player / favorites / downloads / settings
└── ui/                           theme / components / nav
```

---

## 七、几个实现上的坑（改代码前先读）

1. **直链会过期**。`/api/url` 返回的网易云直链约 30 分钟后 403。
   播放用 `ResolvingDataSource` 在**真正读取数据时**才解析地址，
   而不是入队时就固定——否则队列排得靠后的歌会播放失败。

2. **代理下载不支持续传**。走 `/api/download` 时失败必须整包重来，
   所以直链永远是第一选择。

3. **Content-Type 不可信**。代理返回的 `Content-Type` 恒为 `audio/mpeg`，
   但实测内容是 flac（body 魔数 `fLaC`）。格式必须靠魔数判定。

4. **音质档位不硬编码**。netease 的 `qualityOptions` 有 7 档，
   但实测 hires / jyeffect / sky / jymaster 四档返回的都是同一个 2000k flac。
   UI 按服务端返回的真实值展示，不标「超清母带」。

5. **未知渠道静默回落**。见上文「核心设计」。

6. **未知 `provider` 不报错**。服务端对任意 provider 都会返回 netease 的结果，
   而不是 400。所以必须比对返回项的 `provider` 字段。

---

## 八、许可

本客户端（Mu 音乐）以 **MIT** 协议开源，见仓库根目录的 `LICENSE`。
服务端参考实现见 `markcxx/coco-downloader`（MIT）。

> 本项目不内置、也不推荐任何特定的音源站。请使用你有权访问的服务。
