package com.r3zound.music.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 收藏夹备份文件的读写。
 *
 * - Android 10+ 走 MediaStore 写进「下载/MuMusic」，用户能直接找到；
 * - Android 9 及以下直接写公共下载目录（清单里已声明 WRITE_EXTERNAL_STORAGE，maxSdkVersion=28）。
 */
@Singleton
class BackupWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * 把备份内容写到下载目录，返回给用户看的相对路径。
     *
     * @throws IOException 写入失败时抛出
     */
    fun writeToDownloads(json: String, fileName: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(json, fileName)
        } else {
            writeViaFile(json, fileName)
        }
    }

    /** 从用户选中的 uri 读取文本内容。 */
    fun readText(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()

    private fun writeViaMediaStore(json: String, fileName: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER",
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法在下载目录创建文件")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("无法写入备份文件")
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return "下载/$FOLDER/$fileName"
    }

    private fun writeViaFile(json: String, fileName: String): String {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FOLDER,
        )
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("无法创建目录：${dir.absolutePath}")
        }
        val file = File(dir, fileName)
        file.writeText(json, Charsets.UTF_8)
        return file.absolutePath
    }

    companion object {
        const val FOLDER = "MuMusic"

        /** 备份文件名：MuMusic-favorites-20261104-153012.json */
        fun buildFileName(timestamp: Long = System.currentTimeMillis()): String {
            val date = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                .format(java.util.Date(timestamp))
            return "MuMusic-favorites-$date.json"
        }
    }
}
