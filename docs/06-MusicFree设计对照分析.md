# MusicFree 设计亮点对照分析 —— 20 个值得借鉴的点

> 对比对象：[maotoumao/MusicFree](https://github.com/maotoumao/MusicFree)（AGPL-3.0）
> 我方：Mu 音乐 `com.r3zound.music` v1.2.0
> 分析方式：通读 MusicFree 源码（`src/pages` / `src/core` / `src/components`），逐个模块对照我方实现
> 结论按「性价比」排序，**不需要全抄**。

---

## 一、总览

| # | 亮点 | 价值 | 成本 | 我的建议 |
|---|---|---|---|---|
| 1 | 列表内「正在播放」高亮 | 高 | 极低 | ✅ 立刻做 |
| 2 | 「正在播放」定位徽标（准星按钮） | 高 | 低 | ✅ 立刻做 |
| 3 | 歌词点击跳转播放位置 | 高 | 低 | ✅ 立刻做 |
| 4 | 播放页模糊封面背景 | 高 | 低 | ✅ 立刻做 |
| 5 | Mini Player 横向滑动切歌 | 高 | 中 | ✅ 建议做 |
| 6 | 睡眠定时器 | 中高 | 低 | ✅ 建议做 |
| 7 | 歌词字号 + 偏移手动调整 | 中 | 低 | ✅ 建议做 |
| 8 | 播放历史页 | 中高 | 低 | ✅ 建议做 |
| 9 | 主题编辑器（背景图/模糊/逐色键） | 中高 | 中 | 🔶 看你喜好 |
| 10 | 搜索结果「类型 × 音源」两层 Tab | 中 | 中 | 🔶 可选 |
| 11 | 本地音乐扫描（MediaStore） | 中 | 中 | 🔶 可选 |
| 12 | 分享当前歌曲 | 中 | 低 | 🔶 可选 |
| 13 | 歌单链接导入 | 中 | 中 | 🔶 可选 |
| 14 | 插件系统（可插拔音源） | 很高 | 高 | ⚠️ 见专章 |
| 15 | 评论面板 | 低 | 高 | ❌ 不建议 |
| 16 | 横屏播放页布局 | 低 | 中 | ❌ 不建议 |
| 17 | i18n 多语言 | 低 | 中 | ❌ 不建议 |
| 18 | 大图查看器 | 低 | 低 | ❌ 不建议 |
| 19 | 备份含插件列表 + 版本比较 | 中 | 中 | ⚠️ 取决于 14 |
| 20 | 抽屉式侧边导航 | 低 | 中 | ❌ 不建议 |

---

## 二、高性价比：建议优先做的 8 个

### 1. 列表内「正在播放」高亮 ⭐

**MusicFree 怎么做**
`src/components/mediaItem/musicItem.tsx`：列表项接收 `highlight` 属性，
命中当前播放歌曲时，**标题和序号都换成主色**（`fontColor={highlight ? "primary" : "text"}`）。

**我方现状**
只有播放队列页（`QueueScreen.kt`）有 `isCurrent` 高亮，**搜索结果、收藏列表、歌单详情都没有**。

**为什么好**
用户在 100 首的搜索结果里点了一首第 47 首，往下滚之后完全不知道"现在在放哪首"。
高亮是零成本的信息反馈。

**实现成本**：约 20 行。`TrackRow` 加一个 `isPlaying: Boolean` 参数，
各列表页从 `PlayerHolder.state.current` 拿当前歌曲比对 `provider + id`。

---

### 2. 「正在播放」定位徽标（准星按钮）⭐⭐

**MusicFree 怎么做**
`src/components/musicList/index.tsx`：右下角浮一个 **crosshair 圆形按钮**。
- 列表滚动开始时如果当前播放项在列表里 → 显示徽标
- 点徽标 → `scrollToIndex` 滚到当前播放项（无动画，立刻定位）
- 滚动停止 5 秒后徽标自动隐藏

```tsx
{showBadge && (
  <View style={styles.badge}>
    <Pressable onPress={scrollToHighlight}>
      <Icon name="crosshair" color={colors.notification} />
    </Pressable>
  </View>
)}
```

**我方现状**：完全没有。

**为什么好**
这是 MusicFree 最"懂用户"的一个细节。长列表里找当前播放的歌，一键定位，
而且**不用一直显示**（滚完就藏），不挡内容。

**实现成本**：约 60 行。Compose 里可以用
`LazyListState.animateScrollToItem(index)` + `LaunchedEffect` 监听滚动状态控制显隐。

---

### 3. 歌词点击跳转播放位置 ⭐

**MusicFree 怎么做**
`src/pages/musicDetail/components/content/lyric/index.tsx`：
点任意一行歌词 → `TrackPlayer.seekTo(time)`，立刻跳到那句的位置继续播。

```tsx
const time = lyrics[draggingIndex].time + (meta?.offset ?? 0);
await TrackPlayer.seekTo(time);
```

**我方现状**
`PlayerScreen.kt` 的 `LyricsSection` **只负责显示**，点歌词没有任何反应。

**为什么好**
这是歌词页的基本交互期望。用户听到"副歌开始"就想点过去反复听。

**实现成本**：约 15 行。`LyricLine` 加 `onClick`，传 `entry.time`（已经是秒）给 `vm.seekTo()`。

> ⚠️ 注意我方的时间单位：歌词 `time` 是**秒**，进度条是**毫秒**，这里容易踩坑。

---

### 4. 播放页模糊封面背景 ⭐⭐

**MusicFree 怎么做**
`src/pages/musicDetail/components/background.tsx`：把当前封面铺满全屏，
`blurRadius={50}` + `opacity={0.5}`，底下垫一层纯黑。

```tsx
<View style={style.background} />          {/* backgroundColor: "#000" */}
<Image style={style.blur} blurRadius={50} source={artworkSource} />
```

**我方现状**
播放页是纯 `MaterialTheme.colorScheme.background` 纯色背景，**完全没有封面视觉**。

**为什么好**
这是「高级感」的最大来源。同一首歌，模糊背景和纯色背景是两个产品。
而且封面是现成的，不增加任何网络请求。

**实现成本**：约 30 行。
```kotlin
// 用 Modifier.blur（API 31+）或自己实现
// 兼容性建议：低版本用「封面图 + 半透明黑色遮罩」近似
```
> ⚠️ Compose 的 `Modifier.blur` 需要 API 31（Android 12）。minSdk 24 需要降级方案：
> 低版本用 `graphicsLayer { renderEffect }` 不行（同样 API 31），
> 实用做法是低版本退回「封面图 + 60% 黑色遮罩」。

---

### 5. Mini Player 横向滑动切歌 ⭐

**MusicFree 怎么做**
`src/components/musicBar/musicInfo.tsx`：Mini Player 上用 Pan 手势，
**带惯性判定**：
- 横向位移超过歌曲卡宽度的 **30%** → 切上一首/下一首
- 或者速度超过 **1500 px/s**（fling）→ 切歌
- 都不满足 → 带弹性动画回到原位

```tsx
if (Math.abs(rate) > 0.3) { skip = vX > 0 ? 1 : -1; }
else if (Math.abs(vX) > 1500) { skip = vX > 0 ? 1 : -1; }
```

**我方现状**
`MiniPlayer` 只有「点击展开」「播放/暂停」「下一首」三个操作，**不能滑**。

**为什么好**
单手操作时，"滑一下换歌" 比 "点下一首按钮" 顺手得多。而且做对了很解压。

**实现成本**：约 80 行（`Modifier.pointerInput` + `detectHorizontalDragGestures` +
`Animatable` 做回弹）。是我 APP 里技术含量最高的一项 UI 改造。

---

### 6. 睡眠定时器 ⭐

**MusicFree 怎么做**
`src/components/panels/types/timingClose.tsx`：
底部弹窗，快捷选项 **10 / 20 / 30 / 45 / 60 分钟**，面板标题变成**实时倒计时**
（`useScheduleCloseCountDown`），还有「播完本首后关闭」的选项。

**我方现状**：完全没有。

**为什么好**
睡前听歌必须能定时关闭，否则电池和流量都遭罪。这是刚需型小功能。

**实现成本**：约 70 行。`PlayerHolder` 加一个倒计时 Job，
到点 `player.stop()`。设置页或播放页加一个入口。

---

### 7. 歌词字号 + 偏移手动调整

**MusicFree 怎么做**
两个独立面板：
- `setFontSize.tsx` —— 歌词字号调节
- `setLyricOffset.tsx` —— **歌词时间偏移**（正负毫秒），用于修正"歌词比歌慢半拍"

`src/core/lyricManager.ts` 统一管理：`updateLyricOffset(item, offset)`。

**我方现状**
歌词字号固定，**没有偏移校正**。

**为什么好**
- 偏移：很多源（尤其 QQ/咪咕）的歌词时间轴本身有偏差，用户手动校一次就永久生效
- 字号：大屏/小屏用户差异明显

**实现成本**：约 60 行。偏移存 DataStore，按 `provider:id` 存。

---

### 8. 播放历史页 ⭐

**MusicFree 怎么做**
`src/core/musicHistory.ts` + `src/pages/history/`：
独立的播放历史页，记录每首歌的播放次数和最后播放时间。

**我方现状**
**Room 里已经建了 `play_history` 表和 `HistoryDao`，但 UI 一次都没用**——
`AppModule` 里提供了 `provideHistoryDao`，然后就没有然后了。搜索历史用得好好的，
播放历史是空的。

**为什么好**
1. 翻车了要知道刚才是哪首
2. 重复听某首歌的快捷入口
3. 顺便把「播放历史」数据用起来，不用白不用

**实现成本**：约 100 行（写入点 + 一个列表页）。表已经建好了，主要是 UI。

---

## 三、中等价值：看你个人喜好

### 9. 主题编辑器

**MusicFree 怎么做**
`src/pages/setCustomTheme/body.tsx` + `src/core/theme.ts`：
- **背景图**：自定义图片 + 模糊半径（0~100）+ 不透明度（0~1）
- **逐个颜色键自定义**：`primary` / `appBar` / `musicBar` / `tabBar` / `divider` /
  `card` / `placeholder` / `backdrop` … 每个都能单独换
- **取色器**（`colorPicker.tsx` 面板）
- 还会根据主色的**灰度自动调整**对比度（`grayRate` 计算），保证文字可读

**我方现状**：只有「跟随系统 / 浅色 / 深色」三档，主色是写死的品牌红。

**值得抄的点**
- **逐颜色键自定义**：这个设计很好，主色和顶栏色、底栏色可以分开调
- **灰度自适应**：主色太亮/太暗时自动加深，保证文字对比度 —— 这条我可以只抄逻辑，不抄 UI

**成本**：约 300 行（要引入取色器 + 颜色键体系）。是个大工程。

---

### 10. 搜索结果「类型 × 音源」两层 Tab

**MusicFree 怎么做**
`src/pages/searchPage/components/resultPanel/`：
- **外层 Tab**：歌曲 / 歌单 / 专辑 / 歌手 / 歌词（`react-native-tab-view`）
- **内层 Tab**：**每个音源插件一个 Tab**，每个 Tab 独立显示自己的加载状态和结果
- 每个插件单独调 `search(query, 1, tab, pluginHash)`

**我方现状**
有「按渠道分组」和「合并去重」两种模式切换 ✓（功能等价），但用的是分组列表而不是嵌套 Tab。

**差异**
MusicFree 让你能同时看到「云音乐的歌曲」和「QQ 的歌曲」两个 Tab，
并且每个源可以**单独重试**。我的合并模式在某个源失败时会显示错误徽标，但没有"点单个源重试"。

**值得抄的点**：**单源重试**。我的渠道体检也有类似需求。

**成本**：约 200 行。要改搜索页结构。

---

### 11. 本地音乐扫描（MediaStore）

**MusicFree 怎么做**
`src/pages/localMusic/`：扫描设备上的音乐文件，
自动识别本地歌曲（列表里显示一个 ✓ 徽标，`LocalMusicSheet.isLocalMusic()`），
本地歌曲和在线歌曲**混在同一个列表里**。

**我方现状**
只有「下载管理」（管的是 APP 自己下的文件），**没有扫描设备上的其它音乐**。

**值得抄的点**
如果用户手机里有一堆本地歌，扫描进来统一管理是刚需。
而且我已经有 MediaStore 写入能力（下载会进媒体库），读取是同一套 API，成本比想象低。

**成本**：约 150 行。

---

### 12. 分享当前歌曲

**MusicFree 怎么做**
`src/pages/musicDetail/components/navBar.tsx`：播放页右上角一个分享图标，
调插件的 `share` 能力生成链接。

**我方现状**：没有。

**值得抄的点**：可以分享「歌名 + 歌手 + 你的音源站链接」纯文本，不需要插件支持，
30 行就能搞定。

**成本**：极低。

---

### 13. 歌单链接导入

**MusicFree 怎么做**
`src/components/panels/types/importMusicSheet.tsx`：粘贴一个链接，自动解析成歌单。

**我方现状**：收藏夹有 JSON 导入导出，但没有"链接导入歌单"。

**注意**：这一条**依赖源支持**。我的源只有 4 个接口，没有歌单 ID 体系，
所以只能做"用链接里的关键词去搜索"这种伪导入。**优先级低。**

---

### 14. 插件系统（可插拔音源）⚠️ 见专章

**MusicFree 怎么做**
整个 App 的架构核心：一个 JS 插件系统，每个插件是一个「平台」，
自带 UI 配置（`userVariables`）、版本号、主页、作者、搜索/取流/歌词/歌单/榜单能力。

**我方现状**：源是写死在 `ProviderRegistry` 里的 17 个渠道 + 硬编码的 4 个接口。

**评估**：这个是 MusicFree 最大的架构优势，也是最大的改造成本。
但对你的场景来说——**你只有自己的一个源站**——插件系统的价值是
"以后想加第二个源时不用改 APP"。详见最后一章。

---

## 四、不建议抄的

### 15. 评论面板 ❌

`src/components/panels/types/musicComment/`。
依赖插件实现 `getMusicComments`，而你的源**根本没有评论接口**。
纯 UI 投入、零产出。

### 16. 横屏播放页布局 ❌

`src/pages/musicDetail/components/content/index.tsx` 用 `useOrientation` 在横屏时
同时显示封面和歌词。手机 APP 里横屏播放的场景极少，而且要做专门的横屏布局，
性价比低。

### 17. i18n 多语言 ❌

`src/core/i18n/languages/` 有简中/繁中/英文三份。
你自己的源站是中文生态，用户也是中文，加多语言是纯负担。

### 18. 大图查看器 ❌

`src/components/panels/types/imageViewer.tsx`。加这个不如把精力放在
「播放页模糊背景」上，那个收益大得多。

### 19. 备份含插件列表 + 版本比较 ⚠️

`src/core/backup.ts`：备份 JSON 里除了歌单，还存了**插件列表（含版本号）**，
恢复时按版本比较决定是否覆盖插件。

**这条很聪明**，但只在你采纳 14（插件系统）之后才有意义。
如果不做插件系统，这条可以直接跳过。

### 20. 抽屉式侧边导航 ❌

`src/pages/home/index.tsx` 用 `createDrawerNavigator`。
底部 Tab 已经够用了，再加抽屉是双重导航。

---

## 五、如果要做「插件系统」——一个务实的中间方案

MusicFree 的插件系统是给「要接入几十个平台」的场景设计的。
你的情况是**一个自建源站**，照抄整套插件架构（沙箱、JS 引擎、插件市场、
版本管理、在线导入）投入产出比很低。

但有一个**轻量版**值得考虑：

> **把「源」抽成接口，让你的源站可以是多个。**

具体做法：
1. 定义 `interface MusicSource { id; name; search(); url(); lyric(); download() }`
2. 把现在的 17 渠道实现改成 17 个 `MusicSource` 实例（或一个 `MultiChannelSource`）
3. 设置页允许添加自定义源（填 baseUrl + 选类型），源列表存 DataStore
4. 搜索时遍历所有启用的源，聚合结果

这样你以后加第二个站（比如换个源站、或者加个备用源）**不用改 APP**，
但不用背 JS 沙箱那套包袱。**成本约 200~300 行**，比真插件系统小一个数量级。

---

## 六、我的最终建议（一句话版）

**立刻做这 5 个，成本极低但体验提升明显：**

1. 列表内「正在播放」高亮
2. 「正在播放」定位准星按钮
3. 歌词点击跳转
4. 播放页模糊封面背景
5. 睡眠定时器

**第二批（有时间就做）：**

6. 播放历史页（表都建好了，别浪费）
7. 歌词字号 + 偏移校正
8. Mini Player 滑动切歌
9. 分享当前歌曲

**其余的先别动。** 特别是插件系统、评论、主题编辑器——
在"你自己的源站"这个前提下，收益远低于成本。

---

## 七、附：源码位置速查

| 亮点 | MusicFree 源码 |
|---|---|
| 正在播放高亮 | `src/components/mediaItem/musicItem.tsx` |
| 定位准星徽标 | `src/components/musicList/index.tsx` |
| 歌词点击跳转 | `src/pages/musicDetail/components/content/lyric/index.tsx` |
| 模糊封面背景 | `src/pages/musicDetail/components/background.tsx` |
| Mini Player 滑动 | `src/components/musicBar/musicInfo.tsx` |
| 睡眠定时器 | `src/components/panels/types/timingClose.tsx` |
| 歌词字号 / 偏移 | `src/components/panels/types/setFontSize.tsx` / `setLyricOffset.tsx` |
| 播放历史 | `src/core/musicHistory.ts`、`src/pages/history/` |
| 主题编辑器 | `src/pages/setCustomTheme/`、`src/core/theme.ts` |
| 两层 Tab 搜索 | `src/pages/searchPage/components/resultPanel/` |
| 本地音乐 | `src/pages/localMusic/` |
| 分享 | `src/pages/musicDetail/components/navBar.tsx` |
| 插件系统 | `src/core/pluginManager/`、`src/types/plugin.d.ts` |
| 备份 | `src/core/backup.ts` |
