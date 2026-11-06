package com.r3zound.music.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import android.net.Uri
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSourceFactory
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException

/**
 * 解析型 DataSource：遇到 `mu://` 协议的 URI 时，先同步解析成真实 CDN 直链再下载。
 *
 * 直链约 30 分钟过期，所以在**真正开始读取数据时**才解析，而不是在加入队列时——
 * 这样队列里排得很后面的歌曲也不会因为链接过期而播放失败。
 *
 * 解析发生在播放线程（不是主线程），阻塞等待是预期行为。
 */
class ResolvingDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val resolve: (String) -> String?,
) : DataSource {

    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val real = if (uri.scheme == MU_SCHEME) resolve(uri.toString()) else null
        val target = if (real != null) dataSpec.withUri(Uri.parse(real)) else dataSpec
        val source = upstreamFactory.createDataSource()
        delegate = source
        return source.open(target)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: -1

    override fun getUri(): Uri = delegate?.uri ?: Uri.EMPTY

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

    override fun close() {
        delegate?.close()
        delegate = null
    }

    companion object {
        const val MU_SCHEME = "mu"
    }
}

/** 带解析能力的 MediaSource 工厂。 */
class ResolvingMediaSourceFactory(
    context: Context,
    private val upstreamFactory: DataSource.Factory,
    private val resolve: (String) -> String?,
) : MediaSourceFactory {

    private val delegate = DefaultMediaSourceFactory(context).setDataSourceFactory(
        object : DataSource.Factory {
            override fun createDataSource(): DataSource = ResolvingDataSource(upstreamFactory, resolve)
        }
    )

    override fun createMediaSource(mediaItem: MediaItem): MediaSource = delegate.createMediaSource(mediaItem)

    // Media3 1.4 起这几个方法是抽象的，必须显式转发给内部工厂。
    // 本 APP 只播普通音频流，不涉及 DRM，但接口照样要求实现。
    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory {
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory {
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes
}

/** 供 UI 显示的当前媒体项。 */
fun MediaItem.coverUri(): Uri? = mediaMetadata.artworkUri
