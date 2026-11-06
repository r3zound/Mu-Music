package com.r3zound.music.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.r3zound.music.core.model.AudioFormat
import com.r3zound.music.core.util.SafeUrl
import com.r3zound.music.data.local.DownloadDao
import com.r3zound.music.data.local.DownloadSource
import com.r3zound.music.data.local.DownloadStatus
import com.r3zound.music.data.prefs.SettingsStore
import com.r3zound.music.data.remote.MusicApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * 单个下载任务。
 *
 * 下载策略（依据线上实测确定）：
 * 1. **直链优先**：`/api/url` 拿 CDN 直链直接下载
 *    - 直链支持 Range（206），可断点续传；实测直链 7.6–64 MB/s
 *    - 直链约 30 分钟过期，403 时重新取直链并带 Range 续传，不整包重来
 * 2. **代理兜底**：`/api/download` 由服务端代理转发
 *    - 不支持 Range（恒 200），失败必须整包重来；速度明显慢于直链
 * 3. 真实格式一律按 **body 魔数** 判定——服务端 Content-Type 恒为 audio/mpeg
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val api: MusicApi,
    private val dao: DownloadDao,
    private val settings: SettingsStore,
    private val fileStore: FileStore,
    private val client: OkHttpClient,
    private val notifier: DownloadNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()
        val task = dao.get(taskId) ?: return@withContext Result.failure()
        if (task.status == DownloadStatus.COMPLETED) return@withContext Result.success()

        dao.setStatus(taskId, DownloadStatus.RUNNING, null)
        promoteToForeground(task)
        try {
            download(taskId)
            Result.success(workDataOf(KEY_TASK_ID to taskId))
        } catch (e: Exception) {
            val message = e.message?.take(160) ?: "下载失败"
            dao.setStatus(taskId, DownloadStatus.FAILED, message)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun download(taskId: String) {
        val task = dao.get(taskId) ?: throw IOException("任务不存在")
        val baseUrl = settings.baseUrl.first()
        val extra = task.extraJson?.let {
            runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it) as? JsonObject }.getOrNull()
        }

        val temp = fileStore.tempFile(taskId)
        var usedProxy = false

        try {
            // ── 主路径：直链 ────────────────────────────────────
            val directUrl = resolveDirect(baseUrl, task.provider, task.songId, extra)
            downloadFromUrl(
                taskId = taskId,
                url = directUrl,
                temp = temp,
                refresh = { resolveDirect(baseUrl, task.provider, task.songId, extra) },
            )
        } catch (e: Exception) {
            // 直链失败：可能是过期、也可能是该渠道不支持直链 → 转代理兜底
            val directError = e.message ?: "直链下载失败"
            usedProxy = true
            runCatching { temp.delete() }
            try {
                downloadViaProxy(taskId, baseUrl, task.provider, task.songId, task.fileName, extra, temp)
            } catch (proxyError: Exception) {
                throw IOException("直链失败：$directError；代理失败：${proxyError.message ?: "未知"}")
            }
        }

        // ── 判定真实格式并入库 ────────────────────────────────
        val head = readHead(temp, 16)
        val format = AudioFormat.detect(head).takeIf { it != AudioFormat.UNKNOWN } ?: AudioFormat.MP3
        // 注意：publish 之后临时文件已被删除/移动，体积必须提前取
        val size = temp.length()

        val finalName = fileStore.buildFileName(task.artist, task.title, format)
        val stored = fileStore.publish(temp, finalName, format.mime)

        dao.upsert(
            task.copy(
                fileName = finalName,
                filePath = stored.path,
                fileSize = size,
                downloadedBytes = size,
                status = DownloadStatus.COMPLETED,
                error = null,
                source = if (usedProxy) DownloadSource.PROXY else DownloadSource.DIRECT,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** 把 Worker 提升为前台任务，进度通知在切后台后依然可见。 */
    private suspend fun promoteToForeground(task: com.r3zound.music.data.local.DownloadTaskEntity) {
        val notification = notifier.buildProgress(
            taskId = task.taskId,
            title = task.title,
            artist = task.artist,
            downloaded = task.downloadedBytes,
            total = task.totalBytes,
            percent = if (task.totalBytes > 0) {
                ((task.downloadedBytes * 100) / task.totalBytes).toInt().coerceIn(0, 100)
            } else {
                0
            },
            ongoing = true,
        )
        val type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        setForeground(androidx.work.ForegroundInfo(notifier.notificationId(task.taskId), notification, type))
    }

    /** 取直链并按安全策略校验。 */
    private suspend fun resolveDirect(
        baseUrl: String,
        provider: String,
        songId: String,
        extra: JsonObject?,
    ): String {
        val info = api.playInfo(baseUrl, songId, provider, extra)
        return SafeUrl.normalize(info.url) ?: throw IOException("直链不安全或无效")
    }

    /**
     * 从直链下载，支持 Range 续传。
     * 直链过期（403/410）时通过 [refresh] 重新取一次直链，并继续从断点续传。
     */
    private suspend fun downloadFromUrl(
        taskId: String,
        url: String,
        temp: File,
        refresh: suspend () -> String,
    ) {
        var currentUrl = url
        var refreshed = false
        while (true) {
            val startAt = if (temp.exists()) temp.length() else 0L
            val request = Request.Builder().url(currentUrl).apply {
                if (startAt > 0) header("Range", "bytes=$startAt-")
            }.build()

            val expired = client.newCall(request).execute().use {
                    when {
                        it.code == 403 || it.code == 410 -> true
                        it.code == 206 -> {
                            val body = it.body ?: throw IOException("空响应体")
                            val total = it.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                            temp.appendStreamTo(body.byteStream()) { read, _ ->
                                reportProgress(taskId, startAt + read, total)
                            }
                            false
                        }

                        it.code == 200 -> {
                            // 服务端忽略了 Range → 从头重下
                            temp.delete()
                            val body = it.body ?: throw IOException("空响应体")
                            val total = it.header("Content-Length")?.toLongOrNull()
                            temp.writeStreamTo(body.byteStream()) { read, _ ->
                                reportProgress(taskId, read, total)
                            }
                            false
                        }

                        else -> throw IOException("HTTP ${it.code}")
                    }
                }

            if (!expired) return
            if (refreshed) throw IOException("直链已失效")
            refreshed = true
            currentUrl = refresh()
        }
    }

    /** 服务端代理下载（不支持 Range，失败需整包重来）。 */
    private suspend fun downloadViaProxy(
        taskId: String,
        baseUrl: String,
        provider: String,
        songId: String,
        fileName: String,
        extra: JsonObject?,
        temp: File,
    ) {
        val request = api.downloadRequest(baseUrl, songId, provider, fileName, extra)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("代理返回 HTTP ${response.code}")
            val body = response.body ?: throw IOException("空响应体")
            val total = response.header("Content-Length")?.toLongOrNull()
            temp.writeStreamTo(body.byteStream()) { read, _ ->
                reportProgress(taskId, read, total)
            }
        }
    }

    private suspend fun reportProgress(taskId: String, downloaded: Long, total: Long?) {
        if (total != null && total > 0) {
            dao.setProgress(taskId, downloaded, total)
            setProgress(workDataOf(KEY_PROGRESS to downloaded, KEY_TOTAL to total))
        }
    }

    private fun readHead(file: File, size: Int): ByteArray {
        if (!file.exists() || file.length() == 0L) return ByteArray(0)
        return runCatching {
            file.inputStream().use { input ->
                val buffer = ByteArray(size)
                val read = input.read(buffer)
                if (read <= 0) ByteArray(0) else buffer.copyOf(read)
            }
        }.getOrDefault(ByteArray(0))
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL = "total"

        /** 写入字节流的回调。 */
        private suspend fun File.writeStreamTo(
            stream: java.io.InputStream,
            onProgress: suspend (downloaded: Long, total: Long?) -> Unit,
        ) {
            stream.use { input ->
                outputStream().use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastReport = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReport > 256 * 1024) {
                            lastReport = downloaded
                            onProgress(downloaded, null)
                        }
                    }
                    out.flush()
                }
            }
        }

        /** 追加写入（续传场景）。 */
        private suspend fun File.appendStreamTo(
            stream: java.io.InputStream,
            onProgress: suspend (downloaded: Long, total: Long?) -> Unit,
        ) {
            stream.use { input ->
                java.io.FileOutputStream(this, true).use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastReport = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReport > 256 * 1024) {
                            lastReport = downloaded
                            onProgress(downloaded, null)
                        }
                    }
                    out.flush()
                }
            }
        }
    }
}
