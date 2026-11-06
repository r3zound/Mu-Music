package com.r3zound.music.data.remote

import com.r3zound.music.core.model.ChannelInfo

/**
 * 渠道注册表。
 *
 * 线上 m.resound.vip 实测共 17 个渠道。注意服务端对**未注册**渠道会静默回落到 netease，
 * 所以 APP 必须始终显式传 provider，并校验响应里的 provider 字段。
 *
 * 列表以线上实测为准；服务端升级后新增/下线渠道时，改这里即可。
 */
object ProviderRegistry {

    val netease = "netease"
    val qq = "qq"
    val kugou = "kugou"
    val gequbao = "gequbao"
    val gequhai = "gequhai"
    val bugu = "bugu"
    val bodian = "bodian"
    val qqmp3 = "qqmp3"
    val mitu = "mitu"
    val joox = "joox"
    val migu = "migu"
    val livepoo = "livepoo"
    val aiting = "aiting"
    val jianbinNetease = "jianbin-netease"
    val jianbinQq = "jianbin-qq"
    val jianbinKugou = "jianbin-kugou"
    val jianbinKuwo = "jianbin-kuwo"

    /** 线上实测的 17 个渠道，顺序与站点前端一致。 */
    val all: List<ChannelInfo> = listOf(
        ChannelInfo(netease, "云音乐"),
        ChannelInfo(qq, "XCVTS"),
        ChannelInfo(kugou, "海棠"),
        ChannelInfo(gequbao, "歌曲宝"),
        ChannelInfo(gequhai, "歌曲海"),
        ChannelInfo(bugu, "布谷"),
        ChannelInfo(bodian, "波点"),
        ChannelInfo(qqmp3, "QQMP3"),
        ChannelInfo(mitu, "米兔"),
        ChannelInfo(joox, "JOOX"),
        ChannelInfo(migu, "咪咕"),
        ChannelInfo(livepoo, "力音"),
        ChannelInfo(aiting, "爱听"),
        ChannelInfo(jianbinNetease, "煎饼-1"),
        ChannelInfo(jianbinQq, "煎饼-2"),
        ChannelInfo(jianbinKugou, "煎饼-3"),
        ChannelInfo(jianbinKuwo, "煎饼-4"),
    )

    private val byId = all.associateBy { it.id }

    fun name(provider: String): String = byId[provider]?.name ?: provider

    fun info(provider: String): ChannelInfo? = byId[provider]

    fun isKnown(provider: String): Boolean = byId.containsKey(provider)
}
