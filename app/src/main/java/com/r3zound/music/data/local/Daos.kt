package com.r3zound.music.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    val songCount: Int,
)

data class FavoriteWithPlaylist(
    @Embedded val favorite: FavoriteEntity,
    val playlistId: Long,
    val position: Int,
)

data class TagWithCount(
    @Embedded val tag: TagEntity,
    val songCount: Int,
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    /** 导出备份时一次性取全量。 */
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    suspend fun getAll(): List<PlaylistEntity>

    /** 导出备份时一次性取全量歌单关联。 */
    @Query("SELECT * FROM playlist_items")
    suspend fun getAllItems(): List<PlaylistItemEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE playlists SET updatedAt = :ts WHERE id = :id")
    suspend fun touch(id: Long, ts: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun countItems(playlistId: Long): Int

    /** 取「默认收藏」歌单。 */
    @Query("SELECT * FROM playlists WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): PlaylistEntity?

    /** 默认收藏歌单 id，不存在时返回 null。 */
    @Query("SELECT id FROM playlists WHERE isDefault = 1 LIMIT 1")
    fun observeDefaultId(): Flow<Long?>

    /** 已被收藏在默认收藏里的曲目 id（用于避免重复入库）。 */
    @Query(
        """
        SELECT favoriteId FROM playlist_items
        JOIN favorites f ON f.id = playlist_items.favoriteId
        WHERE playlist_items.playlistId = :playlistId AND f.songKey = :songKey
        LIMIT 1
        """
    )
    suspend fun favoriteIdInPlaylistByKey(playlistId: Long, songKey: String): Long?
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    /** 导出备份时一次性取全量。 */
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    suspend fun getAll(): List<FavoriteEntity>

    /** 导出备份时一次性取全量标签。 */
    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    suspend fun getAllTags(): List<TagEntity>

    /** 导出备份时一次性取全量「收藏↔标签」关联。 */
    @Query("SELECT * FROM favorite_tags")
    suspend fun getAllTagLinks(): List<FavoriteTagEntity>

    @Query("SELECT * FROM favorites WHERE songKey = :songKey")
    suspend fun findByKey(songKey: String): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE id = :id")
    suspend fun getById(id: Long): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity): Long

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM favorites")
    fun observeCount(): Flow<Int>

    // ── 歌单关联 ──────────────────────────────────────────────
    @Query(
        """
        SELECT f.* , pi.playlistId AS playlistId, pi.position AS position
        FROM favorites f
        JOIN playlist_items pi ON pi.favoriteId = f.id
        WHERE pi.playlistId = :playlistId
        ORDER BY pi.position ASC, f.title ASC
        """
    )
    fun observePlaylistItems(playlistId: Long): Flow<List<FavoriteWithPlaylist>>

    @Query(
        """
        SELECT f.* , pi.playlistId AS playlistId, pi.position AS position
        FROM favorites f
        JOIN playlist_items pi ON pi.favoriteId = f.id
        WHERE pi.playlistId = :playlistId
        ORDER BY pi.position ASC, f.title ASC
        """
    )
    suspend fun getPlaylistItems(playlistId: Long): List<FavoriteWithPlaylist>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToPlaylist(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND favoriteId = :favoriteId")
    suspend fun removeFromPlaylist(playlistId: Long, favoriteId: Long)

    @Query("DELETE FROM playlist_items WHERE favoriteId = :favoriteId")
    suspend fun removeFromAllPlaylists(favoriteId: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("SELECT favoriteId FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun favoriteIdsIn(playlistId: Long): List<Long>

    @Query("UPDATE playlist_items SET position = :position WHERE playlistId = :playlistId AND favoriteId = :favoriteId")
    suspend fun updatePosition(playlistId: Long, favoriteId: Long, position: Int)

    // ── 标签关联 ──────────────────────────────────────────────
    @Query(
        """
        SELECT t.* , (SELECT COUNT(*) FROM favorite_tags ft WHERE ft.tagId = t.id) AS songCount
        FROM tags t ORDER BY t.createdAt DESC
        """
    )
    fun observeTagsWithCount(): Flow<List<TagWithCount>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkTags(links: List<FavoriteTagEntity>)

    @Query("DELETE FROM favorite_tags WHERE favoriteId = :favoriteId AND tagId = :tagId")
    suspend fun unlinkTag(favoriteId: Long, tagId: Long)

    @Query("DELETE FROM favorite_tags WHERE favoriteId = :favoriteId")
    suspend fun clearTags(favoriteId: Long)

    @Query(
        """
        SELECT f.* FROM favorites f
        JOIN favorite_tags ft ON ft.favoriteId = f.id
        WHERE ft.tagId = :tagId
        ORDER BY f.createdAt DESC
        """
    )
    fun observeTagged(tagId: Long): Flow<List<FavoriteEntity>>

    @Query("SELECT tagId FROM favorite_tags WHERE favoriteId = :favoriteId")
    suspend fun tagIdsOf(favoriteId: Long): List<Long>
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status IN ('PENDING','RUNNING','PAUSED') ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status = 'COMPLETED' ORDER BY updatedAt DESC")
    fun observeCompleted(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status = 'FAILED' ORDER BY updatedAt DESC")
    fun observeFailed(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE taskId = :taskId")
    suspend fun get(taskId: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE songKey = :songKey AND status = 'COMPLETED' LIMIT 1")
    suspend fun findCompletedBySongKey(songKey: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE songKey = :songKey AND status != 'COMPLETED' LIMIT 1")
    suspend fun findPendingBySongKey(songKey: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: DownloadTaskEntity)

    @Update
    suspend fun update(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE taskId = :taskId")
    suspend fun delete(taskId: String)

    @Query("UPDATE download_tasks SET status = :status, error = :error, updatedAt = :ts WHERE taskId = :taskId")
    suspend fun setStatus(taskId: String, status: String, error: String?, ts: Long = System.currentTimeMillis())

    @Query("UPDATE download_tasks SET downloadedBytes = :bytes, totalBytes = :total, updatedAt = :ts WHERE taskId = :taskId")
    suspend fun setProgress(taskId: String, bytes: Long, total: Long, ts: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM download_tasks WHERE status = 'COMPLETED'")
    fun observeCompletedCount(): Flow<Int>
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity)

    /** 同一首歌重复播放：先删旧记录再插新的，保证时间戳是最新的。 */
    @Query("DELETE FROM play_history WHERE songKey = :songKey")
    suspend fun deleteByKey(songKey: String)

    /** 只保留最近 [keep] 条，删掉更旧的。 */
    @Query(
        """
        DELETE FROM play_history WHERE id NOT IN (
            SELECT id FROM play_history ORDER BY playedAt DESC LIMIT :keep
        )
        """
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM play_history")
    suspend fun clear()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE keyword = :keyword LIMIT 1")
    suspend fun find(keyword: String): SearchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE keyword = :keyword")
    suspend fun delete(keyword: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
