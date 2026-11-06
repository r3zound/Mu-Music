package com.r3zound.music.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.r3zound.music.core.model.AudioFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 下载文件的落盘位置。
 *
 * - Android 10+：写入 MediaStore，直接进系统音乐库，路径 `Music/MuMusic`
 * - Android 9 及以下：写入公共 Music 目录并触发媒体扫描
 *
 * 下载过程始终先落在应用私有目录的 `.part` 文件上，支持 Range 断点续传，
 * 完成后再移入最终位置。
 */
class FileStore @javax.inject.Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 下载中的临时文件（应用私有目录，不受系统清理影响）。 */
    fun tempFile(taskId: String): File {
        val dir = File(context.filesDir, "downloads").apply { mkdirs() }
        return File(dir, "$taskId.part")
    }

    /** 清理残留的临时文件。 */
    fun cleanupTemp(taskId: String) {
        runCatching { tempFile(taskId).delete() }
    }

    /** 目标文件名：歌手 - 标题.扩展名 */
    fun buildFileName(artist: String?, title: String, format: AudioFormat): String {
        val safe = listOf(artist, title)
            .filter { !it.isNullOrBlank() }
            .joinToString(" - ")
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
        return if (safe.isEmpty()) "未知歌曲.${format.extension}" else "$safe.${format.extension}"
    }

    /**
     * 把已下载完成的临时文件移入最终位置。
     * 返回可播放的 Uri 与展示用的相对路径。
     */
    suspend fun publish(temp: File, fileName: String, mimeType: String): StoredFile =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishToMediaStore(temp, fileName, mimeType)
            } else {
                publishToPublicDir(temp, fileName)
            }
        }

    private fun publishToMediaStore(temp: File, fileName: String, mimeType: String): StoredFile {
        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_MUSIC}/MuMusic"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: error("无法在媒体库中创建文件：$fileName")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                temp.inputStream().use { input -> input.copyTo(out, DEFAULT_BUFFER_SIZE) }
            } ?: error("无法写入媒体库文件：$fileName")
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            temp.delete()
            return StoredFile(uri, "$relativePath/$fileName", uri.toString())
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            temp.delete()
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun publishToPublicDir(temp: File, fileName: String): StoredFile {
        val base = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "MuMusic")
        if (!base.exists()) base.mkdirs()
        val target = File(base, fileName)
        // 同名文件加 (1) 后缀，避免覆盖
        var finalFile = target
        var index = 1
        while (finalFile.exists()) {
            val name = fileName.substringBeforeLast('.') + " ($index)." + fileName.substringAfterLast('.')
            finalFile = File(base, name)
            index++
        }
        if (temp.renameTo(finalFile)) {
            MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null, null)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", finalFile)
            return StoredFile(uri, "Music/MuMusic/${finalFile.name}", finalFile.absolutePath)
        }
        // 跨文件系统时退回复制
        temp.inputStream().use { input ->
            finalFile.outputStream().use { out -> input.copyTo(out, DEFAULT_BUFFER_SIZE) }
        }
        temp.delete()
        MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null, null)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", finalFile)
        return StoredFile(uri, "Music/MuMusic/${finalFile.name}", finalFile.absolutePath)
    }

    /** 删除已发布文件（通过 Uri）。 */
    suspend fun delete(uriString: String) = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(Uri.parse(uriString), null, null)
        }
        Unit
    }

    /** 判断文件是否真实存在（MediaStore 场景）。 */
    suspend fun exists(uriString: String?): Boolean = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext false
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { true } ?: false
        }.getOrDefault(false)
    }
}

data class StoredFile(
    val uri: Uri,
    /** 展示用相对路径，如 Music/MuMusic/周杰伦 - 晴天.flac */
    val displayPath: String,
    /** 持久化的路径字符串 */
    val path: String,
)
