package com.r3zound.music.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.core.model.QualityOption
import com.r3zound.music.data.local.DownloadDao
import com.r3zound.music.data.local.DownloadSource
import com.r3zound.music.data.local.DownloadStatus
import com.r3zound.music.data.local.DownloadTaskEntity
import com.r3zound.music.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 下载队列管理：建任务、排任务、观察状态。 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val settings: SettingsStore,
) {

    fun observeAll(): Flow<List<DownloadTaskEntity>> = dao.observeAll()
    fun observeActive(): Flow<List<DownloadTaskEntity>> = dao.observeActive()
    fun observeCompleted(): Flow<List<DownloadTaskEntity>> = dao.observeCompleted()
    fun observeFailed(): Flow<List<DownloadTaskEntity>> = dao.observeFailed()
    fun observeCompletedCount(): Flow<Int> = dao.observeCompletedCount()

    /**
     * 加入下载队列。
     * 同一首歌已在队列中则不重复添加；已下载完成的直接跳过。
     */
    suspend fun enqueue(
        item: MusicItem,
        quality: QualityOption?,
        fileName: String,
    ): String {
        val songKey = "${item.provider}:${item.id}"
        dao.findCompletedBySongKey(songKey)?.let { return it.taskId }
        dao.findPendingBySongKey(songKey)?.let { return it.taskId }

        val taskId = "$songKey#${System.currentTimeMillis()}"
        dao.upsert(
            DownloadTaskEntity(
                taskId = taskId,
                songKey = songKey,
                provider = item.provider,
                songId = item.id,
                title = item.title,
                artist = item.artist,
                album = item.album,
                cover = item.cover,
                duration = item.duration,
                extraJson = item.extra?.toString(),
                qualityValue = quality?.value,
                qualityLabel = quality?.label,
                fileName = fileName,
                status = DownloadStatus.PENDING,
                source = DownloadSource.DIRECT,
            )
        )
        schedule(taskId)
        return taskId
    }

    /** 批量入队（歌单一键下载）。 */
    suspend fun enqueueAll(items: List<Pair<MusicItem, QualityOption?>>): Int {
        var count = 0
        items.forEach { (item, quality) ->
            enqueue(item, quality, "${item.artist} - ${item.title}")
            count++
        }
        return count
    }

    private suspend fun schedule(taskId: String) {
        val wifiOnly = settings.wifiOnly.first()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .setInputData(workDataOf(DownloadWorker.KEY_TASK_ID to taskId))
            .addTag(TAG_DOWNLOAD)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("dl-$taskId", ExistingWorkPolicy.REPLACE, request)
    }

    /** 重新下载（失败重试 / 用户手动重试）。 */
    suspend fun retry(taskId: String) {
        dao.setStatus(taskId, DownloadStatus.PENDING, null)
        schedule(taskId)
    }

    suspend fun cancel(taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("dl-$taskId")
        dao.setStatus(taskId, DownloadStatus.FAILED, "已取消")
    }

    suspend fun delete(taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("dl-$taskId")
        dao.get(taskId)?.filePath?.let { path -> runCatching { java.io.File(path).delete() } }
        dao.delete(taskId)
    }

    suspend fun clearCompleted() {
        dao.observeCompletedOnce().forEach { dao.delete(it.taskId) }
    }

    companion object {
        const val TAG_DOWNLOAD = "mu_download"
    }
}

/** 便捷：一次性读取已完成列表。 */
private suspend fun DownloadDao.observeCompletedOnce(): List<DownloadTaskEntity> =
    observeCompleted().first()
