# B1：自建音源站 → MusicFree 音源插件 使用说明

> 交付物：`moresound.js`（单文件插件，约 24 KB）
> 适用：MusicFree 移动版（Android / iOS），插件协议 v2
> 许可证：本插件为独立作品，随便用。MusicFree 本体是 AGPL-3.0，自用安装不受影响。

---

## 一、这是什么

把你的音源站接进 MusicFree，让它变成 MusicFree 里的一个「**平台**」。

> 插件**不内置任何服务器地址**，装好后必须先在插件设置里填「音源站地址」。

装上之后，MusicFree 的搜索、歌单广场、排行榜、歌手页、歌词全部走你的源站，
UI 是网易云级，音源完全自控，**不需要逆向、不需要改包名、不需要自己编译任何东西**。

```
MusicFree 官方 APK  +  moresound.js  =  一个能用的网易云替代客户端
```

---

## 二、安装

### 步骤 1：装 MusicFree

从 GitHub Releases 拿官方 APK：
<https://github.com/maotoumao/MusicFree/releases>

（AGPL-3.0 协议，官方签名，认准作者 `maotoumao`。）

### 步骤 2：装插件

**方式 A：在线导入（快）**

1. 打开 MusicFree → 我的 → 插件管理
2. 右上角 `+` → 「在线导入」
3. 粘贴插件 URL：
   ```
   https://matrix-internal-cn.oss-cn-shanghai.aliyuncs.com/user_file_upload_tmp%2F2026-11-05%2Fc81616a8-afb8-44d7-90ee-b88b61259f59.js?x-oss-credential=...&x-oss-expires=3600&x-oss-signature=...
   ```
   > ⚠️ 这个是**临时链接，1 小时后过期**。过期了就改用方式 B。

**方式 B：本地导入（推荐，永久）**

1. 把附件里的 `moresound.js` 存到手机上（微信文件传输助手 / QQ / 网盘都行）
2. MusicFree → 插件管理 → 右上角 `+` → 「导入本地插件」→ 选这个文件
3. 装完在插件列表里能看到 **moresound**，点进去启用

### 步骤 3：设置音源站地址（必填）

插件详情页 → 「音源站地址」→ **默认是空的，必须你自己填**。

插件不内置任何人的服务器（要开源分发），所以第一次用会提示：
「请先在插件设置里填写『音源站地址』」。填上你自己的源站地址即可，**不用改代码**。

---

## 三、怎么用

装完之后，MusicFree 里的操作和平时一样：

| 功能 | 说明 |
|---|---|
| **搜索** | 一次并发检索 6 个渠道（云音乐 / 波点 / QQMP3 / 米兔 / 咪咕 / QQ），结果自动去重合并 |
| **播放** | 点播放时实时向 `/api/url` 取直链，flac / mp3 自动按内容分发 |
| **音质** | MusicFree 的 low/standard/high/super 四档，会自动降级到该渠道实际支持的档位 |
| **歌词** | `/api/lyric` 返回的逐行时间戳，转成标准 LRC，当前行高亮 + 自动滚动 |
| **歌手页** | 点歌手名进入，用歌手名检索聚合出「该歌手的作品」 |
| **排行榜** | 6 个榜单预设（热歌榜 / 飙升榜 / 新歌榜 / 原创榜 / 经典榜 / 排行榜） |
| **歌单广场** | 9 个分类标签：排行榜、歌单、华语、情歌、欧美、儿歌、经典、电音、说唱 |

### 9 个分类的关键词

| 分类 | 关键词（按顺序轮换） |
|---|---|
| 排行榜 | 热歌榜 → 飙升榜 → 热歌推荐 |
| 歌单 | 精选歌单 → 歌单合集 → 好听的歌单 |
| 华语 | 华语热歌 → 华语经典 → 粤语歌 |
| 情歌 | 情歌 → 情歌推荐 → 想你的歌 |
| 欧美 | 欧美音乐 → 英文歌 → 欧美流行 |
| 儿歌 | 儿歌 → 儿童歌曲 → 卡通儿歌 |
| 经典 | 经典老歌 → 怀旧金曲 → 经典歌曲 |
| 电音 | 电音 → EDM → 电子音乐 |
| 说唱 | 说唱 → Rap → 嘻哈 |

---

## 四、必须知道的限制（源的能力边界）

这几条不是插件的 bug，是 `m.resound.vip` 本身就只有 4 个接口造成的。
**换成任何一个客户端都一样**，除非你换源站。

### 1. 源站没有榜单 / 歌单 / 专辑 / 歌手接口

实测 `m.resound.vip` 只有这 4 个：

```
GET /api/search?q=&provider=&limit=&offset=   → { items: [...] }
GET /api/url?id=&provider=&extra=              → { url, type, bitrate }
GET /api/lyric?id=&provider=&extra=            → { songid, provider, lines: [...] }
GET /api/download?id=&provider=&filename=&extra= → 音频流
```

所以插件里的**排行榜、歌单广场、歌手页、专辑页，全部是「用分类关键词去搜索」伪装出来的**。
内容是真的能播，但**它不是网易云那种官方榜单**，别当成真榜单看。

### 2. 直链约 30 分钟过期

`/api/url` 返回的直链带时间戳，约 30 分钟失效。

插件已经处理好了：`cacheControl` 设为 `no-cache`，MusicFree 联网播放时**每次都会重新调 `getMediaSource`**，
所以直链过期不会暴露。**如果你去改这个值成 `cache`，播放就会在半小时后开始失败。**

### 3. 渠道可用性随关键词变化

线上实测（2026-11）：

- **搜索 + 取流全通**：`netease`、`bodian`、`qqmp3`、`mitu`、`migu`
- **能搜、取流报 500**：`qq`、`kugou`、`livepoo`、`gequbao`
- **搜不到 / 超时**：`gequhai`、`bugu`、`joox`、`aiting`、`jianbin-*`

插件默认只并发检索前 6 个可用渠道。某些冷门渠道随时可能挂，这是上游解析服务的状态。

### 4. 未知渠道会静默回落

服务端遇到没注册的 `provider` 会静默回落到 `netease`。
插件每次都显式传 `provider`，并在 item 上标了 `fallenBack` 字段，界面上能看出来。

---

## 五、技术细节

### 插件协议

用的是 MusicFree 的 `IPluginDefine`（`src/types/plugin.d.ts`）：

| 插件方法 | 对应源站接口 | 实现方式 |
|---|---|---|
| `search(query, page, type)` | `/api/search` | 6 渠道并发 + 去重合并，15 条/渠道 |
| `getMediaSource(item, quality)` | `/api/url` | 按 quality 降级链选档位，覆盖 `extra.selectedLevel` |
| `getLyric(item)` | `/api/lyric` | `lines[{time,text}]` → LRC |
| `getMusicInfo(base)` | — | 会话内缓存反查 |
| `getArtistWorks(artist, page, type)` | `/api/search` | 按歌手名检索 |
| `getTopLists()` | — | 6 个静态榜单预设 |
| `getTopListDetail(item, page)` | `/api/search` | 按榜单关键词检索 |
| `getRecommendSheetTags()` | — | 9 个分类预设 |
| `getRecommendSheetsByTag(tag, page)` | `/api/search` | 按分类关键词检索 |
| `getMusicSheetInfo(item, page)` | `/api/search` | 按歌单关键词检索 |
| `getAlbumInfo(item, page)` | `/api/search` | 按专辑名检索 |
| `importMusicSheet(url)` | `/api/search` | 解析链接里的 id/provider |

### extra 的处理（最容易踩坑的地方）

服务端靠 `extra` 里的 `selectedLevel` 决定音质档位，靠 `extra` 里的其它字段定位资源。
所以插件**任何地方都不改写 extra 的原有字段**，只在取流时按用户选的音质覆盖 `selectedLevel`：

```js
let extraParam = Object.assign({}, extra);
if (picked && picked.value) extraParam.selectedLevel = picked.value;
```

### 音质降级链

MusicFree 只有 4 档，服务端每个渠道能给的档位不同：

```js
low:      ['standard', '128']
standard: ['exhigh', '320', 'standard', '128']
high:     ['lossless', 'flac', 'exhigh', '320', 'standard', '128']
super:    ['hires', 'jymaster', 'sky', 'lossless', 'flac', 'exhigh', '320', 'standard', '128']
```

取该渠道 `qualityOptions` 里第一个命中的档位；全都没有就取第一个。

### 主键

`primaryKey: ['id']`，`id` 格式是 `provider:songId`（如 `netease:509781655`），跨渠道唯一。

---

## 六、离线验证

插件带了一个自测脚本，不用装 MusicFree 就能验证源站链路：

```bash
node /workspace/test_plugin.mjs
```

它模拟 MusicFree 的插件沙箱（注入 `require` / `env` / `URL` / `process`），
真实打线上接口，逐项检查 20 个点：搜索、直链可达、歌词格式、榜单、歌单广场、歌手页、音质降级。

最近一次结果：

```
✅ platform — moresound
✅ cacheControl=no-cache
✅ primaryKey
✅ userVariables.apiUrl
--- search("周杰伦") ---  返回 60 条，用时 1285ms
✅ id 形如 provider:songId
✅ 时长是秒数
✅ extra 已保留
✅ artwork 非空
✅ qualityOptions 非空
--- getMediaSource ---   ✅ 拿到直链 / ✅ 直链可访问 HTTP 200
--- getLyric ---         ✅ 歌词非空 / ✅ 是标准 LRC 格式
✅ getMusicInfo 能反查
--- 榜单 ---            ✅ 6 个榜单 / ✅ 详情 118 首
--- 歌单广场 ---         ✅ 9 个分类 / ✅ 分类下 54 个歌单
--- 歌手页 ---           ✅ 65 首
--- super 档取流 ---     ✅ 成功
🎉 全部通过
```

---

## 七、常见问题

**Q：装了插件但搜不到歌？**
A：**先确认插件设置里的「音源站地址」填了**。插件默认留空（开源项目不内置别人的服务器），
不填的话搜索会直接报「请先在插件设置里填写『音源站地址』」。
填好后如果搜索整体超时，多半是上游服务在抖，过一会儿再试。

**Q：播放报「取流失败」？**
A：那个渠道的直链解析挂了。换个渠道的歌试试，或者用 `netease`（最稳）。

**Q：榜单里的歌和网易云对不上？**
A：正常。榜单是关键词搜索聚合的，不是官方榜单。见「四、必须知道的限制」。

**Q：能改成自己的源站吗？**
A：能，改插件设置里的地址就行。但如果你的源站接口和 `m.resound.vip` 不一样，得改 `apiGet` 那几个函数。

**Q：为什么不用 LX Music？**
A：LX Music 移动版（`lx-music-mobile`）的自定义源插件协议只有 `musicUrl` / `lyric` / `pic` 三个动作，
**没有 search**，搜索永远走它内置的网易云/QQ 接口，改不了。MusicFree 的插件协议才是完整的。
