package com.r3zound.music.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 自建歌单。 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    /** 封面占位色索引 0..3，对应设计稿的四种拼贴色。 */
    val coverVariant: Int = 0,
    /**
     * 是否为「默认收藏」。默认收藏是内置歌单：首次启动自动创建，
     * 搜索结果播放时自动入库，用户不可重命名/删除。
     */
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 收藏的曲目。`extraJson` 保存服务端原样返回的 extra，
 * 之后下载/取流必须原样回传，否则音质降级或取流失败。
 */
@Entity(tableName = "favorites", indices = [Index("songKey"), Index("provider")])
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** provider:id，跨渠道唯一标识。 */
    val songKey: String,
    val provider: String,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val cover: String? = null,
    val duration: String? = null,
    val extraJson: String? = null,
    val qualityOptionsJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 歌单 ↔ 曲目 多对多，带顺序。 */
@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "favoriteId"],
    indices = [Index("favoriteId")],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val favoriteId: Long,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "favorite_tags", primaryKeys = ["favoriteId", "tagId"])
data class FavoriteTagEntity(
    val favoriteId: Long,
    val tagId: Long,
)

/**
 * 下载任务。
 *
 * [source] 记录实际走的是直链还是代理，便于排查「为什么这台机器特别慢」。
 * 直链支持 Range（可断点续传），代理不支持（实测恒 200，失败必须整包重来）。
 */
@Entity(tableName = "download_tasks", indices = [Index("status"), Index("songKey")])
data class DownloadTaskEntity(
    @PrimaryKey val taskId: String,
    val songKey: String,
    val provider: String,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val cover: String? = null,
    val duration: String? = null,
    val extraJson: String? = null,
    val qualityValue: String? = null,
    val qualityLabel: String? = null,
    val fileName: String,
    val filePath: String? = null,
    val fileSize: Long = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val status: String = DownloadStatus.PENDING,
    val error: String? = null,
    val source: String = DownloadSource.DIRECT,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

object DownloadStatus {
    const val PENDING = "PENDING"
    const val RUNNING = "RUNNING"
    const val PAUSED = "PAUSED"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
}

object DownloadSource {
    const val DIRECT = "DIRECT"
    const val PROXY = "PROXY"
}

/** 播放历史。 */
@Entity(tableName = "play_history", indices = [Index("playedAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songKey: String,
    val provider: String,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val cover: String? = null,
    val duration: String? = null,
    val extraJson: String? = null,
    val qualityOptionsJson: String? = null,
    val playedAt: Long = System.currentTimeMillis(),
)

/** 搜索历史。 */
@Entity(tableName = "search_history", indices = [Index("keyword")])
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val searchedAt: Long = System.currentTimeMillis(),
)
