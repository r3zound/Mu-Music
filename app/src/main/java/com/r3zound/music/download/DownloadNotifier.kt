package com.r3zound.music.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.r3zound.music.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 下载进度通知。 */
@Singleton
class DownloadNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "下载进度",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示歌曲下载进度与完成通知"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildProgress(
        taskId: String,
        title: String,
        artist: String,
        downloaded: Long,
        total: Long,
        percent: Int,
        ongoing: Boolean,
    ): Notification {
        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (total > 0) {
            "$artist · $percent% · ${formatSize(downloaded)} / ${formatSize(total)}"
        } else {
            "$artist · $percent%"
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (ongoing) "正在下载 · $title" else "下载完成 · $title")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, total <= 0L)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    fun notify(id: Int, notification: Notification) {
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    /** 任务对应的通知 id：同一任务复用同一个通知，避免进度通知刷屏。 */
    fun notificationId(taskId: String): Int = taskId.hashCode()

    fun cancel(id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }

    companion object {
        const val CHANNEL_ID = "mu_download_channel"
        fun formatSize(bytes: Long): String = when {
            bytes <= 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024)
            else -> String.format("%.2f GB", bytes / 1024.0 / 1024 / 1024)
        }
    }
}
