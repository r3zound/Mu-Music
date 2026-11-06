/**
 * moresound.js 的离线验证脚本。
 *
 * 模拟 MusicFree 的插件沙箱（注入 require / env / URL / process），
 * 真实调用线上接口，验证 search / getMediaSource / getLyric / 榜单 / 歌单广场。
 *
 * 用法：node /workspace/test_plugin.mjs
 */
import { readFileSync } from 'node:fs';

const CODE = readFileSync(new URL('./moresound.js', import.meta.url), 'utf8');

// ── 模拟 MusicFree 的运行时 ──
const axiosLike = {
    async get(url, config) {
        const ctrl = new AbortController();
        const timer = setTimeout(() => ctrl.abort(), (config && config.timeout) || 15000);
        try {
            const resp = await fetch(url, { headers: (config && config.headers) || {}, signal: ctrl.signal });
            const text = await resp.text();
            let data;
            try {
                data = JSON.parse(text);
            } catch {
                data = text;
            }
            return { status: resp.status, data };
        } finally {
            clearTimeout(timer);
        }
    },
};

const env = {
    getUserVariables: () => ({ apiUrl: 'https://m.resound.vip' }),
    appVersion: '0.1.0',
    os: 'android',
    lang: 'zh-CN',
};

const mod = { exports: {} };
const fakeRequire = (name) => {
    if (name === 'axios') return axiosLike;
    throw new Error('module not found: ' + name);
};

new Function(
    'require', '__musicfree_require', 'module', 'exports', 'console', 'env', 'URL', 'process',
    CODE
)(fakeRequire, fakeRequire, mod, mod.exports, console, env, URL, { platform: 'android', version: '0.1.0', env });

const plugin = mod.exports;

let failures = 0;
function check(name, ok, detail) {
    const mark = ok ? '✅' : '❌';
    if (!ok) failures++;
    console.log(`${mark} ${name}${detail ? ' — ' + detail : ''}`);
}

// ── 1. 元信息 ──
check('platform', plugin.platform === 'moresound', plugin.platform);
check('cacheControl=no-cache（直链不过期的前提）', plugin.cacheControl === 'no-cache');
check('primaryKey', Array.isArray(plugin.primaryKey) && plugin.primaryKey[0] === 'id');
check('userVariables.apiUrl', !!plugin.userVariables?.find(v => v.key === 'apiUrl'));

// ── 2. 搜索 ──
console.log('\n--- search("周杰伦") ---');
const t0 = Date.now();
const res = await plugin.search('周杰伦', 1, 'music');
console.log(`   返回 ${res.data.length} 条，用时 ${Date.now() - t0}ms`);
check('搜索有结果', res.data.length > 0, `${res.data.length} 条`);
if (res.data.length) {
    const it = res.data[0];
    console.log('   首条:', JSON.stringify({
        id: it.id, title: it.title, artist: it.artist, album: it.album,
        duration: it.duration, provider: it.provider, artwork: it.artwork ? '有' : '无',
        qualityOptions: (it.qualityOptions || []).length,
    }));
    check('id 形如 provider:songId', it.id.includes(':'));
    check('时长是秒数', typeof it.duration === 'number' && it.duration > 0);
    check('extra 已保留', !!it.extra && typeof it.extra === 'object');
    check('artwork 非空', !!it.artwork);
    check('qualityOptions 非空', (it.qualityOptions || []).length > 0);

    // ── 3. 取流 ──
    console.log('\n--- getMediaSource ---');
    const ms = await plugin.getMediaSource(it, 'high');
    console.log('   url:', String(ms.url).slice(0, 110));
    check('拿到直链', !!ms.url && /^https?:\/\//.test(ms.url));
    const head = await fetch(ms.url, { method: 'HEAD' });
    check('直链可访问', head.ok, 'HTTP ' + head.status);

    // ── 4. 歌词 ──
    console.log('\n--- getLyric ---');
    const lrc = await plugin.getLyric(it);
    const lrcText = (lrc && (lrc.rawLrc || lrc.lrc)) || '';
    console.log('   ' + lrcText.split('\n').slice(0, 3).join('\n   '));
    check('歌词非空', lrcText.length > 0);
    check('是标准 LRC 格式', /\[\d{2}:\d{2}\.\d{2}\]/.test(lrcText));

    // ── 5. getMusicInfo 反查 ──
    const info = await plugin.getMusicInfo(it);
    check('getMusicInfo 能反查', !!info && info.id === it.id);
}

// ── 6. 榜单 ──
console.log('\n--- getTopLists / getTopListDetail ---');
const tops = await plugin.getTopLists();
check('榜单有分组', tops.length > 0 && tops[0].data.length > 0, `${tops[0]?.data.length} 个榜单`);
const detail = await plugin.getTopListDetail(tops[0].data[0], 1);
check('榜单详情有歌', (detail.musicList || []).length > 0, `${detail.musicList?.length} 首`);

// ── 7. 歌单广场 ──
console.log('\n--- getRecommendSheetTags / getRecommendSheetsByTag ---');
const tags = await plugin.getRecommendSheetTags();
check('分类标签有 9 个', tags.data[0].data.length === 9, `${tags.data[0].data.length} 个`);
const sheets = await plugin.getRecommendSheetsByTag(tags.data[0].data[2], 1);
check('分类下有歌单', (sheets.data || []).length > 0, `${sheets.data?.length} 个`);

// ── 8. 歌手页 ──
console.log('\n--- getArtistWorks ---');
const works = await plugin.getArtistWorks({ id: 'x', name: '周杰伦', platform: 'moresound' }, 1, 'music');
check('歌手作品有结果', (works.data || []).length > 0, `${works.data?.length} 首`);

// ── 9. 音质映射：super 档也要能取到流 ──
console.log('\n--- 音质档位取流 ---');
if (res.data.length) {
    const target = res.data.find(i => (i.qualityOptions || []).some(o => o.quality === 'hires'))
        || res.data[0];
    try {
        const ms2 = await plugin.getMediaSource(target, 'super');
        check('super 档取流成功', !!ms2.url);
    } catch (e) {
        check('super 档取流成功', false, e.message);
    }
}

// ── 10. 未填「音源站地址」时的行为 ──
console.log('\n--- 未配置音源站地址 ---');
{
    // 重新加载一份插件，把 env 换成「用户没填地址」
    const mod2 = { exports: {} };
    const env2 = { getUserVariables: () => ({}), appVersion: '0.1.0', os: 'android', lang: 'zh-CN' };
    new Function(
        'require', '__musicfree_require', 'module', 'exports', 'console', 'env', 'URL', 'process',
        CODE
    )(fakeRequire, fakeRequire, mod2, mod2.exports, console, env2, URL, { platform: 'android', version: '0.1.0', env });
    const plugin2 = mod2.exports;
    let msg = '';
    try {
        await plugin2.search('周杰伦', 1, 'music');
    } catch (e) {
        msg = e.message;
    }
    check('地址为空时给出明确提示', msg.includes('音源站地址'), msg || '(没有抛错)');
    check('插件不内置任何默认地址', plugin2.description.includes('音源站地址'));
}

console.log(`\n${failures === 0 ? '🎉 全部通过' : '⚠️ 有 ' + failures + ' 项失败'}`);
process.exit(failures === 0 ? 0 : 1);
