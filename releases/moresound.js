/**
 * m.resound.vip → MusicFree 自定义源插件
 * ============================================================
 *
 * 把 m.resound.vip（自建音源站）接进 MusicFree，让它变成一个「平台」。
 *
 * 【源的真实能力】
 *   站点只有 4 个接口，没有任何鉴权：
 *     GET /api/search?q=&provider=&limit=&offset=   → { items: [...] }
 *     GET /api/url?id=&provider=&extra=              → { url, type, bitrate, cover? }
 *     GET /api/lyric?id=&provider=&extra=            → { songid, provider, lines: [...] }
 *     GET /api/download?id=&provider=&filename=&extra= → 二进制音频流
 *
 * 【因此本插件的取舍】
 *   1. 源没有「榜单 / 歌单 / 专辑 / 歌手」接口，所以 MusicFree 里的
 *      排行榜、歌单广场、歌手页都是用「关键词搜索」伪装出来的。
 *      这不是 bug，是源的边界——界面能点开，内容是按分类关键词聚合的曲目。
 *   2. /api/url 返回的是约 30 分钟有效的直链。MusicFree 默认
 *      cacheControl='no-cache'（仅离线时用缓存），联网播放每次都会重新
 *      调 getMediaSource，直链过期问题天然不存在。**不要改成 'cache'**。
 *   3. extra 必须原样透传。服务端靠 extra.selectedLevel 决定音质档位，
 *      靠 extra 里的其它字段定位资源，所以本插件任何地方都不改写 extra
 *      的原有字段，只按用户选的音质覆盖 selectedLevel。
 *
 * 【安装】
 *   MusicFree → 我的 → 插件管理 → 右上角「+」→ 在线导入 → 填本文件 URL
 *   或把本文件保存为 moresound.js 后，用「导入本地插件」。
 *   安装后**必须**在插件详情里填「音源站地址」（默认留空，插件不内置任何服务器）。
 */

'use strict';

const PLATFORM = 'moresound';
/**
 * 默认留空：本插件会开源分发，不能内置任何人的服务器地址。
 * 使用者必须在插件设置里填「音源站地址」。
 */
const DEFAULT_API = '';

/**
 * HTTP 客户端。
 *
 * MusicFree 的插件沙箱只注入了 require / env / URL / process，
 * axios 不是全局变量，必须 require 出来；require 不到时退回内置 fetch。
 */
let httpClient = null;
try {
    httpClient = require('axios');
} catch (e) {
    httpClient = null;
}

const UA =
    'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36';

/** 每个渠道检索多少条。并发渠道数 × 这个值 = 一次搜索最多返回多少首。 */
const LIMIT_PER_CHANNEL = 15;

/**
 * 参与聚合检索的渠道。
 *
 * 线上实测（2026-11）：netease / bodian / qqmp3 / mitu / migu 是
 * 「搜索 + 取流」全链路可用的；qq 只能搜不能取流（返回 500），
 * 放在最后兜底。这里只保留 6 个，保证速度和成功率。
 */
const SEARCH_CHANNELS = [
    'netease',
    'bodian',
    'qqmp3',
    'mitu',
    'migu',
    'qq',
];

/** 渠道 id → 展示名。 */
const CHANNEL_NAMES = {
    netease: '云音乐',
    qq: 'QQ音乐',
    kugou: '酷狗',
    migu: '咪咕',
    mitu: '米兔',
    bodian: '波点',
    qqmp3: 'QQMP3',
    gequbao: '歌曲宝',
};

/**
 * MusicFree 的音质枚举 → 服务端 qualityOptions[].quality 的降级链。
 *
 * MusicFree 只有 low / standard / high / super 四档，
 * 服务端每个渠道能给的档位不同（有的只有 128k，有的有 7 档无损）。
 * 所以这里给的是「从高到低」的候选链，实际取该渠道 options 里第一个命中的。
 */
const QUALITY_CHAIN = {
    low: ['standard', '128'],
    standard: ['exhigh', '320', 'standard', '128'],
    high: ['lossless', 'flac', 'exhigh', '320', 'standard', '128'],
    super: ['hires', 'jymaster', 'sky', 'lossless', 'flac', 'exhigh', '320', 'standard', '128'],
};

/** 分类预设：源没有分类接口，用关键词 + 渠道组合模拟。 */
const CATEGORIES = [
    { id: 'ranking', title: '排行榜', desc: '热歌 · 飙升 · 经典', keywords: ['热歌榜', '飙升榜', '热歌推荐'] },
    { id: 'playlist', title: '歌单', desc: '合集 · 精选', keywords: ['精选歌单', '歌单合集', '好听的歌单'] },
    { id: 'chinese', title: '华语', desc: '华语 · 粤语', keywords: ['华语热歌', '华语经典', '粤语歌'] },
    { id: 'love', title: '情歌', desc: '告白 · 想念', keywords: ['情歌', '情歌推荐', '想你的歌'] },
    { id: 'western', title: '欧美', desc: '英文 · 流行', keywords: ['欧美音乐', '英文歌', '欧美流行'] },
    { id: 'kids', title: '儿歌', desc: '儿童 · 卡通', keywords: ['儿歌', '儿童歌曲', '卡通儿歌'] },
    { id: 'classic', title: '经典', desc: '老歌 · 怀旧', keywords: ['经典老歌', '怀旧金曲', '经典歌曲'] },
    { id: 'edm', title: '电音', desc: 'EDM · Remix', keywords: ['电音', 'EDM', '电子音乐'] },
    { id: 'rap', title: '说唱', desc: 'Hip-Hop · 嘻哈', keywords: ['说唱', 'Rap', '嘻哈'] },
];

/** 榜单预设（用关键词搜索伪装）。 */
const TOP_LISTS = [
    { id: 'hot', title: '热歌榜', keyword: '热歌榜' },
    { id: 'rising', title: '飙升榜', keyword: '飙升榜' },
    { id: 'new', title: '新歌榜', keyword: '新歌推荐' },
    { id: 'original', title: '原创榜', keyword: '原创歌曲' },
    { id: 'classic', title: '经典榜', keyword: '经典老歌' },
    { id: 'billboard', title: '排行榜', keyword: '歌曲排行榜' },
];

// ────────────────────────────────────────────────────────────
// 基础工具
// ────────────────────────────────────────────────────────────

/** 读取用户在插件设置里填的音源站地址。 */
function getApiBase() {
    let base = DEFAULT_API;
    try {
        const vars = env && typeof env.getUserVariables === 'function' ? env.getUserVariables() : null;
        if (vars && vars.apiUrl && String(vars.apiUrl).trim()) {
            base = String(vars.apiUrl).trim();
        }
    } catch (e) {
        // 取不到就用默认值
    }
    return base.replace(/\/+$/, '');
}

/**
 * 请求站点接口。
 *
 * @param {string} path 例如 '/api/search'
 * @param {object} params 查询参数
 */
async function apiGet(path, params) {
    const url = new URL(getApiBase() + path);
    if (params) {
        Object.keys(params).forEach(function (key) {
            const value = params[key];
            if (value !== undefined && value !== null && value !== '') {
                url.searchParams.append(key, String(value));
            }
        });
    }
    const headers = {
        'user-agent': UA,
        accept: 'application/json, text/plain, */*',
    };
    if (httpClient && typeof httpClient.get === 'function') {
        const resp = await httpClient.get(url.toString(), { timeout: 15000, headers: headers });
        return resp && resp.data;
    }
    const resp = await fetch(url.toString(), { headers: headers });
    if (!resp.ok) {
        throw new Error('HTTP ' + resp.status);
    }
    return await resp.json();
}

/** "03:58" → 238（秒）。解析不了就返回 0。 */
function parseDuration(text) {
    if (!text) return 0;
    // 捕获组：1 = 小时（可选，未参与时为 undefined），2 = 分，3 = 秒
    const m = String(text).match(/^(?:(\d+):)?(\d{1,2}):(\d{1,2})$/);
    if (!m) {
        const n = parseInt(String(text), 10);
        return isNaN(n) ? 0 : n;
    }
    if (m[1] !== undefined) {
        // hh:mm:ss
        return parseInt(m[1], 10) * 3600 + parseInt(m[2], 10) * 60 + parseInt(m[3], 10);
    }
    // mm:ss
    return parseInt(m[2], 10) * 60 + parseInt(m[3], 10);
}

/** 秒 → [mm:ss.xx]。 */
function secondsToLrcTime(seconds) {
    const total = Math.max(0, Number(seconds) || 0);
    const m = Math.floor(total / 60);
    const s = Math.floor(total % 60);
    const cs = Math.floor((total % 1) * 100);
    return (
        String(m).padStart(2, '0') +
        ':' +
        String(s).padStart(2, '0') +
        '.' +
        String(cs).padStart(2, '0')
    );
}

/** 统一的小工具：安全取对象字段。 */
function pick(obj, key, fallback) {
    if (!obj) return fallback;
    const v = obj[key];
    return v === undefined || v === null ? fallback : v;
}

/**
 * 把搜索结果转成 MusicFree 的 IMusicItem。
 *
 * 关键点：extra 原样挂在 item 上，MusicFree 会把整个 item 持久化，
 * 之后取流/取歌词时再原样回传给服务端。
 */
function toMusicItem(raw, requestedProvider) {
    if (!raw || !raw.id) return null;
    const provider = pick(raw, 'provider', requestedProvider || 'netease');
    const songId = String(raw.id);
    const extra = raw.extra && typeof raw.extra === 'object' ? raw.extra : null;

    const item = {
        id: provider + ':' + songId,
        platform: PLATFORM,
        title: pick(raw, 'title', '未知歌曲'),
        artist: pick(raw, 'artist', '未知歌手'),
        album: pick(raw, 'album', ''),
        artwork: pick(raw, 'cover', ''),
        duration: parseDuration(pick(raw, 'duration', '')),
        // ↓ 这几个字段是本插件自己的，MusicFree 会一起存下来
        songId: songId,
        provider: provider,
        requestedProvider: requestedProvider || provider,
        extra: extra,
        qualityOptions: (extra && extra.qualityOptions) || [],
    };
    // 服务端把请求的渠道换成了别的渠道（未注册渠道会回落 netease）
    item.fallenBack = !!(requestedProvider && requestedProvider !== provider);
    return item;
}

/** 查一个 item 的 extra（兼容从 mediaMeta 恢复出来的对象）。 */
function getExtra(musicItem) {
    if (!musicItem) return null;
    if (musicItem.extra && typeof musicItem.extra === 'object') return musicItem.extra;
    if (musicItem.$ && musicItem.$.extra) return musicItem.$.extra;
    return null;
}

/** 查一个 item 的渠道和歌曲 id。 */
function getSource(musicItem) {
    if (!musicItem) return null;
    let provider = musicItem.provider;
    let songId = musicItem.songId;
    if ((!provider || !songId) && typeof musicItem.id === 'string') {
        const idx = musicItem.id.indexOf(':');
        if (idx > 0) {
            provider = provider || musicItem.id.slice(0, idx);
            songId = songId || musicItem.id.slice(idx + 1);
        }
    }
    if (!provider) return null;
    return { provider: provider, songId: String(songId || '') };
}

/** 单渠道搜索。失败返回空数组，不抛异常。 */
async function searchOne(keyword, provider, offset, limit) {
    try {
        const data = await apiGet('/api/search', {
            q: keyword,
            provider: provider,
            limit: limit,
            offset: offset,
        });
        const items = (data && data.items) || [];
        return items
            .map(function (raw) {
                return toMusicItem(raw, provider);
            })
            .filter(Boolean);
    } catch (e) {
        return [];
    }
}

/** 并发搜索多个渠道并合并去重（按 provider:id 去重）。 */
async function searchAllChannels(keyword, offset, limit, channels) {
    const list = channels && channels.length ? channels : SEARCH_CHANNELS;
    const results = await Promise.all(
        list.map(function (provider) {
            return searchOne(keyword, provider, offset, limit);
        })
    );
    const merged = [];
    const seen = {};
    results.forEach(function (items) {
        items.forEach(function (item) {
            const key = item.provider + '|' + item.songId;
            if (seen[key]) return;
            seen[key] = true;
            merged.push(item);
        });
    });
    return merged;
}

// ────────────────────────────────────────────────────────────
// 插件实现
// ────────────────────────────────────────────────────────────

const plugin = {
    platform: PLATFORM,
    version: '1.0.0',
    author: 'm.resound.vip',
    description:
        '自建音源站接入：聚合检索多渠道、直链取流、歌词解析。' +
        '【使用前必须先在下方填写「音源站地址」】' +
        '注：源站没有榜单/歌单接口，排行榜与歌单广场由分类关键词聚合而成。',
    homepage: 'https://m.resound.vip',

    /**
     * 'no-cache' 是关键：MusicFree 只有在离线时才用缓存的直链，
     * 联网播放每次都会重新调用 getMediaSource，
     * 因此 /api/url 的 30 分钟直链过期问题不会暴露。
     * 改成 'cache' 会导致播放失败。
     */
    cacheControl: 'no-cache',

    /** 用 id 作为主键，配合 getMusicInfo 做按主键反查。 */
    primaryKey: ['id'],

    supportedSearchType: ['music'],
    defaultSearchType: 'music',

    /** 用户可在插件设置里改音源站地址。 */
    userVariables: [
        {
            key: 'apiUrl',
            name: '音源站地址',
            hint: '必填。填你自己的音源站，例如 https://your-music-server.com',
        },
    ],

    /**
     * 搜索。
     *
     * @param {string} query 关键词
     * @param {number} page 页码，从 1 开始
     * @param {string} type 媒体类型，本插件只支持 'music'
     */
    async search(query, page, type) {
        if (type !== 'music') {
            return { isEnd: true, data: [] };
        }
        const keyword = String(query || '').trim();
        if (!keyword) {
            return { isEnd: true, data: [] };
        }
        if (!getApiBase()) {
            throw new Error('请先在插件设置里填写「音源站地址」');
        }
        const currentPage = Math.max(1, page || 1);
        const offset = (currentPage - 1) * LIMIT_PER_CHANNEL;
        const data = await searchAllChannels(keyword, offset, LIMIT_PER_CHANNEL);
        return {
            isEnd: data.length === 0,
            data: data,
        };
    },

    /**
     * 取直链。
     *
     * @param {object} musicItem 歌曲
     * @param {string} quality low | standard | high | super
     */
    async getMediaSource(musicItem, quality) {
        const source = getSource(musicItem);
        if (!source || !source.songId) {
            throw new Error('歌曲信息不完整，无法取流');
        }
        const extra = getExtra(musicItem);
        const options = (musicItem && musicItem.qualityOptions) || (extra && extra.qualityOptions) || [];

        // 按用户选的音质，在该渠道实际支持的档位里挑一个最接近的
        const chain = QUALITY_CHAIN[quality] || QUALITY_CHAIN.standard;
        let picked = null;
        for (let i = 0; i < chain.length; i++) {
            picked = options.find(function (o) {
                return o && (o.quality === chain[i] || o.value === chain[i]);
            });
            if (picked) break;
        }
        if (!picked && options.length) picked = options[0];

        // extra 原样透传，只覆盖 selectedLevel
        let extraParam = null;
        if (extra) {
            extraParam = Object.assign({}, extra);
            if (picked && picked.value) {
                extraParam.selectedLevel = picked.value;
            }
        }

        const info = await apiGet('/api/url', {
            id: source.songId,
            provider: source.provider,
            extra: extraParam ? JSON.stringify(extraParam) : undefined,
        });
        if (!info || !info.url) {
            throw new Error('取流失败：服务端没有返回直链');
        }
        return {
            url: info.url,
            headers: {
                'user-agent': UA,
            },
            quality: quality,
        };
    },

    /**
     * 取歌词。
     *
     * 服务端返回 { lines: [{ time: 秒, text }] }，
     * 这里转成标准 LRC 文本。
     */
    async getLyric(musicItem) {
        const source = getSource(musicItem);
        if (!source || !source.songId) return null;
        const extra = getExtra(musicItem);
        try {
            const data = await apiGet('/api/lyric', {
                id: source.songId,
                provider: source.provider,
                extra: extra ? JSON.stringify(extra) : undefined,
            });
            const lines = (data && data.lines) || [];
            if (!lines.length) return null;
            const text = lines
                .map(function (line) {
                    const t = secondsToLrcTime(line && line.time);
                    const txt = (line && line.text) || '';
                    return '[' + t + ']' + txt;
                })
                .join('\n');
            return { rawLrc: text, lrc: text };
        } catch (e) {
            return null;
        }
    },

    /**
     * 按主键反查歌曲信息。
     *
     * 源站没有「按 id 查详情」的接口，所以只能在本次会话里缓存
     * 搜索结果来反查；查不到就返回 null，让 MusicFree 用原对象。
     */
    async getMusicInfo(mediaBase) {
        if (!mediaBase || !mediaBase.id) return null;
        const source = getSource(mediaBase);
        if (!source) return null;
        const key = source.provider + '|' + source.songId;
        const cached = itemCache[key];
        if (!cached) return null;
        return Object.assign({}, cached);
    },

    /**
     * 歌手页：用歌手名做搜索来伪造「该歌手的作品」。
     */
    async getArtistWorks(artistItem, page, type) {
        const name = (artistItem && (artistItem.name || artistItem.title)) || '';
        if (!name) return { isEnd: true, data: [] };
        const currentPage = Math.max(1, page || 1);
        const offset = (currentPage - 1) * LIMIT_PER_CHANNEL;
        const data = await searchAllChannels(name, offset, LIMIT_PER_CHANNEL, [
            'netease',
            'qq',
            'kugou',
            'migu',
        ]);
        const filtered = data.filter(function (item) {
            return String(item.artist || '').indexOf(name) >= 0;
        });
        return {
            isEnd: filtered.length === 0,
            data: filtered.length ? filtered : data,
        };
    },

    /** 榜单列表（关键词搜索伪装）。 */
    async getTopLists() {
        return [
            {
                title: 'm.resound.vip 榜单',
                data: TOP_LISTS.map(function (t) {
                    return {
                        id: PLATFORM + ':top:' + t.id,
                        platform: PLATFORM,
                        title: t.title,
                        description: t.keyword,
                        artist: '',
                        artwork: '',
                        worksNum: 0,
                        coverImg: '',
                    };
                }),
            },
        ];
    },

    /** 榜单详情（用榜单关键词搜索）。 */
    async getTopListDetail(topListItem, page) {
        const title = (topListItem && topListItem.title) || '';
        const preset = TOP_LISTS.find(function (t) {
            return t.title === title;
        });
        const keyword = (preset && preset.keyword) || title;
        const currentPage = Math.max(1, page || 1);
        const offset = (currentPage - 1) * LIMIT_PER_CHANNEL;
        const data = await searchAllChannels(keyword, offset, LIMIT_PER_CHANNEL);
        return {
            isEnd: data.length === 0,
            musicList: data,
        };
    },

    /** 歌单广场的分类标签。 */
    async getRecommendSheetTags() {
        return {
            pinned: [],
            data: [
                {
                    title: '分类',
                    data: CATEGORIES.map(function (c) {
                        return {
                            id: PLATFORM + ':cat:' + c.id,
                            platform: PLATFORM,
                            title: c.title,
                            description: c.desc,
                            artist: '',
                            artwork: '',
                            worksNum: 0,
                            coverImg: '',
                        };
                    }),
                },
            ],
        };
    },

    /**
     * 某个分类下的「歌单」。
     *
     * 源站没有歌单接口，这里把分类关键词搜到的曲目包装成若干个
     * 虚拟歌单，让 MusicFree 的歌单广场有东西可点。
     */
    async getRecommendSheetsByTag(tagItem, page) {
        const tagId = (tagItem && tagItem.id) || '';
        const preset = CATEGORIES.find(function (c) {
            return PLATFORM + ':cat:' + c.id === tagId;
        });
        const title = (preset && preset.title) || (tagItem && tagItem.title) || '';
        const keyword = (preset && preset.keywords[0]) || title;
        const currentPage = Math.max(1, page || 1);
        const offset = (currentPage - 1) * LIMIT_PER_CHANNEL;
        const data = await searchAllChannels(keyword, offset, LIMIT_PER_CHANNEL);
        return {
            isEnd: data.length === 0,
            data: data.map(function (item, index) {
                return {
                    id: PLATFORM + ':sheet:' + tagId + ':' + item.id,
                    platform: PLATFORM,
                    title: item.title + ' · ' + item.artist,
                    description: '由「' + title + '」分类聚合而来',
                    artist: item.artist,
                    artwork: item.artwork,
                    worksNum: 1,
                    coverImg: item.artwork,
                    musicItem: item,
                };
            }),
        };
    },

    /** 歌单详情：把聚合出来的曲目直接返回。 */
    async getMusicSheetInfo(sheetItem, page) {
        const keyword =
            (sheetItem && (sheetItem.description || sheetItem.title)) || '';
        const currentPage = Math.max(1, page || 1);
        const offset = (currentPage - 1) * LIMIT_PER_CHANNEL;
        const data = await searchAllChannels(keyword, offset, LIMIT_PER_CHANNEL);
        return {
            isEnd: data.length === 0,
            musicList: data,
        };
    },

    /** 专辑详情：源没有专辑接口，用专辑名搜索近似。 */
    async getAlbumInfo(albumItem, page) {
        const keyword =
            (albumItem && (albumItem.title || albumItem.description)) || '';
        if (!keyword) return { isEnd: true, musicList: [] };
        const currentPage = Math.max(1, page || 1);
        const offset = (currentPage - 1) * LIMIT_PER_CHANNEL;
        const data = await searchAllChannels(keyword, offset, LIMIT_PER_CHANNEL);
        return {
            isEnd: data.length === 0,
            musicList: data,
        };
    },

    /** 导入歌单链接。 */
    async importMusicSheet(urlLike) {
        const url = String(urlLike || '');
        const m = url.match(/[?&](?:id|ids)=([^&]+)/);
        if (!m) throw new Error('不支持的链接');
        const id = decodeURIComponent(m[1]);
        const providerMatch = url.match(/[?&]provider=([^&]+)/);
        const provider = providerMatch ? decodeURIComponent(providerMatch[1]) : 'netease';
        const items = await searchOne(id, provider, 0, 50);
        return items;
    },
};

/** 本次会话内的歌曲缓存，供 getMusicInfo 反查。 */
const itemCache = {};

// 每次搜索后把结果塞进缓存
const originalSearch = plugin.search.bind(plugin);
plugin.search = async function (query, page, type) {
    const result = await originalSearch(query, page, type);
    (result.data || []).forEach(function (item) {
        itemCache[item.provider + '|' + item.songId] = item;
    });
    return result;
};

module.exports = plugin;
