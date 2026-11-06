package com.r3zound.music.data.repo

import com.r3zound.music.core.util.QualityOptions
import com.r3zound.music.core.model.MusicItem
import com.r3zound.music.core.model.QualityOption
import com.r3zound.music.data.local.FavoriteDao
import com.r3zound.music.data.local.FavoriteEntity
import com.r3zound.music.data.local.FavoriteTagEntity
import com.r3zound.music.data.local.FavoriteWithPlaylist
import com.r3zound.music.data.local.HistoryEntity
import com.r3zound.music.data.local.PlaylistDao
import com.r3zound.music.data.local.PlaylistEntity
import com.r3zound.music.data.local.PlaylistItemEntity
import com.r3zound.music.data.local.TagEntity
import com.r3zound.music.data.local.TagWithCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** 收藏夹：自建歌单 + 标签 + 曲目元数据落库。 */
@Singleton
class FavoriteRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val favoriteDao: FavoriteDao,
    private val json: Json,
) {
    // ── 歌单 ──────────────────────────────────────────────────
    fun observePlaylists(): Flow<List<PlaylistEntity>> = playlistDao.observeAll()

    fun observePlaylistItems(playlistId: Long): Flow<List<FavoriteWithPlaylist>> =
        favoriteDao.observePlaylistItems(playlistId)

    suspend fun createPlaylist(name: String, description: String? = null, coverVariant: Int = 0): Long =
        playlistDao.insert(
            PlaylistEntity(
                name = name.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                coverVariant = coverVariant,
            )
        )

    suspend fun renamePlaylist(id: Long, name: String) {
        val current = playlistDao.getById(id) ?: return
        // 内置的默认收藏不允许改名
        if (current.isDefault) return
        playlistDao.update(current.copy(name = name.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun updatePlaylistDescription(id: Long, description: String?) {
        val current = playlistDao.getById(id) ?: return
        if (current.isDefault) return
        playlistDao.update(current.copy(description = description, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePlaylist(id: Long) {
        val current = playlistDao.getById(id) ?: return
        // 内置的默认收藏不允许删除
        if (current.isDefault) return
        val ids = favoriteDao.favoriteIdsIn(id)
        favoriteDao.clearPlaylist(id)
        playlistDao.delete(id)
        // 清空后不再被任何歌单引用的收藏，视为无主收藏，保留在「全部歌曲」里
        ids.forEach { fid -> favoriteDao.getById(fid) }
    }

    suspend fun playlistSongCount(playlistId: Long): Int = playlistDao.countItems(playlistId)

    // ── 默认收藏 ──────────────────────────────────────────────
    /**
     * 「默认收藏」是内置歌单，首次启动自动创建，之后一直复用。
     *
     * 搜索结果每次被播放时自动入到这个歌单（见 [addToDefaultPlaylist]），
     * 用户不需要手动点收藏。用户自己再建同名歌单也不影响这个内置歌单。
     */
    suspend fun ensureDefaultPlaylist(): Long = defaultPlaylistMutex.withLock {
        playlistDao.getDefault()?.let { return@withLock it.id }
        playlistDao.insert(
            PlaylistEntity(
                name = DEFAULT_PLAYLIST_NAME,
                description = "播放搜索结果时自动收藏到这里",
                coverVariant = 0,
                isDefault = true,
            )
        )
    }

    suspend fun defaultPlaylistId(): Long = ensureDefaultPlaylist()

    fun observeDefaultPlaylistId(): Flow<Long?> = playlistDao.observeDefaultId()

    /**
     * 把歌曲加入默认收藏。已存在则返回 false。
     *
     * 走 [addToPlaylist] 的同一条链路，因此会同时落 favorites 表（可被标签、被其它歌单引用）。
     */
    suspend fun addToDefaultPlaylist(item: MusicItem): Boolean {
        val playlistId = ensureDefaultPlaylist()
        return addToPlaylist(playlistId, item)
    }

    // ── 收藏 ──────────────────────────────────────────────────
    fun observeFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.observeAll()

    fun observeFavoriteCount(): Flow<Int> = favoriteDao.observeCount()

    suspend fun isFavorited(item: MusicItem): Boolean =
        favoriteDao.findByKey(songKey(item)) != null

    suspend fun favoriteIds(item: MusicItem): List<Long> =
        favoriteDao.findByKey(songKey(item))?.let { listOf(it.id) } ?: emptyList()

    /** 收藏单曲；已存在则不重复添加，返回收藏记录 id。 */
    suspend fun addFavorite(item: MusicItem): Long {
        val key = songKey(item)
        favoriteDao.findByKey(key)?.let { return it.id }
        val entity = FavoriteEntity(
            songKey = key,
            provider = item.provider,
            songId = item.id,
            title = item.title,
            artist = item.artist,
            album = item.album,
            cover = item.cover,
            duration = item.duration,
            extraJson = item.extra?.toString(),
            qualityOptionsJson = encodeQualityOptions(item.qualityOptions),
        )
        return favoriteDao.insert(entity)
    }

    /**
     * 设置收藏状态。
     *
     * **收藏时除了写 favorites 表，还会把歌加入内置的「默认收藏」歌单。**
     * 「默认收藏」就是 APP 里的收藏夹入口，用户点收藏按钮期望的就是「歌出现在收藏夹里」，
     * 只写 favorites 表的话歌只会落在「全部歌曲」里，收藏夹是空的。
     *
     * @return true 表示现在处于已收藏状态。
     */
    suspend fun setFavorite(item: MusicItem, favorited: Boolean): Boolean {
        if (favorited) {
            addFavorite(item)
            addToDefaultPlaylist(item)
            return true
        }
        val id = favoriteIds(item).firstOrNull() ?: return false
        removeFavorite(id)
        return false
    }

    suspend fun removeFavorite(favoriteId: Long) {
        favoriteDao.removeFromAllPlaylists(favoriteId)
        favoriteDao.clearTags(favoriteId)
        favoriteDao.delete(favoriteId)
    }

    /** 把歌曲加入歌单；歌单内已存在则忽略。 */
    suspend fun addToPlaylist(playlistId: Long, item: MusicItem): Boolean {
        val favoriteId = addFavorite(item)
        val existing = favoriteDao.favoriteIdsIn(playlistId)
        if (existing.contains(favoriteId)) return false
        val position = favoriteDao.maxPosition(playlistId) + 1
        favoriteDao.addToPlaylist(listOf(PlaylistItemEntity(playlistId, favoriteId, position)))
        playlistDao.touch(playlistId)
        return true
    }

    suspend fun addFavoritesToPlaylist(playlistId: Long, items: List<MusicItem>): Int {
        var added = 0
        items.forEach { if (addToPlaylist(playlistId, it)) added++ }
        return added
    }

    suspend fun removeFromPlaylist(playlistId: Long, favoriteId: Long) {
        favoriteDao.removeFromPlaylist(playlistId, favoriteId)
        playlistDao.touch(playlistId)
    }

    suspend fun reorder(playlistId: Long, orderedFavoriteIds: List<Long>) {
        orderedFavoriteIds.forEachIndexed { index, fid ->
            favoriteDao.updatePosition(playlistId, fid, index)
        }
        playlistDao.touch(playlistId)
    }

    suspend fun clearPlaylist(playlistId: Long) {
        favoriteDao.clearPlaylist(playlistId)
        playlistDao.touch(playlistId)
    }

    // ── 标签 ──────────────────────────────────────────────────
    fun observeTags(): Flow<List<TagWithCount>> = favoriteDao.observeTagsWithCount()

    /**
     * 某个标签下的歌曲。
     *
     * DAO 的 `SELECT f.*` 只查收藏表本身，拿不到歌单关联信息，
     * 所以这里返回的是 [FavoriteEntity] 而不是 [FavoriteWithPlaylist]。
     */
    fun observeTaggedSongs(tagId: Long): Flow<List<FavoriteEntity>> =
        favoriteDao.observeTagged(tagId)

    suspend fun createTag(name: String, colorIndex: Int = 0): Long {
        val trimmed = name.trim()
        favoriteDao.findTagByName(trimmed)?.let { return it.id }
        return favoriteDao.insertTag(TagEntity(name = trimmed, colorIndex = colorIndex))
    }

    suspend fun deleteTag(id: Long) = favoriteDao.deleteTag(id)

    suspend fun setTags(favoriteId: Long, tagIds: List<Long>) {
        favoriteDao.clearTags(favoriteId)
        if (tagIds.isNotEmpty()) {
            favoriteDao.linkTags(tagIds.map { FavoriteTagEntity(favoriteId, it) })
        }
    }

    suspend fun tagIdsOf(favoriteId: Long): List<Long> = favoriteDao.tagIdsOf(favoriteId)

    // ── 备份：导出 / 导入 JSON ────────────────────────────────

    /**
     * 导出整个收藏夹为 JSON 文本。
     *
     * 内容包含歌单、标签、收藏曲目，以及两者的关联关系。
     * `extraJson` 一并导出——它是服务端原样返回的 extra，
     * 还原后必须原样回传，否则取流会降级或失败。
     */
    suspend fun exportJson(): String {
        val playlists = playlistDao.getAll()
        val favorites = favoriteDao.getAll()
        val tags = favoriteDao.getAllTags()
        val items = playlistDao.getAllItems()
        val links = favoriteDao.getAllTagLinks()

        val root = buildJsonObject {
            put("app", JsonPrimitive("MuMusic"))
            put("version", JsonPrimitive(1))
            put("exportedAt", JsonPrimitive(System.currentTimeMillis()))
            put("playlists", buildJsonArray {
                playlists.forEach { p ->
                    add(
                        buildJsonObject {
                            put("id", JsonPrimitive(p.id))
                            put("name", JsonPrimitive(p.name))
                            put("description", JsonPrimitive(p.description))
                            put("coverVariant", JsonPrimitive(p.coverVariant))
                            put("createdAt", JsonPrimitive(p.createdAt))
                            put("updatedAt", JsonPrimitive(p.updatedAt))
                        }
                    )
                }
            })
            put("tags", buildJsonArray {
                tags.forEach { t ->
                    add(
                        buildJsonObject {
                            put("id", JsonPrimitive(t.id))
                            put("name", JsonPrimitive(t.name))
                            put("colorIndex", JsonPrimitive(t.colorIndex))
                            put("createdAt", JsonPrimitive(t.createdAt))
                        }
                    )
                }
            })
            put("favorites", buildJsonArray {
                favorites.forEach { f ->
                    add(
                        buildJsonObject {
                            put("id", JsonPrimitive(f.id))
                            put("songKey", JsonPrimitive(f.songKey))
                            put("provider", JsonPrimitive(f.provider))
                            put("songId", JsonPrimitive(f.songId))
                            put("title", JsonPrimitive(f.title))
                            put("artist", JsonPrimitive(f.artist))
                            put("album", JsonPrimitive(f.album))
                            put("cover", JsonPrimitive(f.cover))
                            put("duration", JsonPrimitive(f.duration))
                            put("extraJson", JsonPrimitive(f.extraJson))
                            put("qualityOptionsJson", JsonPrimitive(f.qualityOptionsJson))
                            put("createdAt", JsonPrimitive(f.createdAt))
                        }
                    )
                }
            })
            put("playlistItems", buildJsonArray {
                items.forEach { pi ->
                    add(
                        buildJsonObject {
                            put("playlistId", JsonPrimitive(pi.playlistId))
                            put("favoriteId", JsonPrimitive(pi.favoriteId))
                            put("position", JsonPrimitive(pi.position))
                            put("addedAt", JsonPrimitive(pi.addedAt))
                        }
                    )
                }
            })
            put("favoriteTags", buildJsonArray {
                links.forEach { ft ->
                    add(
                        buildJsonObject {
                            put("favoriteId", JsonPrimitive(ft.favoriteId))
                            put("tagId", JsonPrimitive(ft.tagId))
                        }
                    )
                }
            })
        }
        return root.toString()
    }

    /**
     * 从 JSON 文本导入收藏夹。
     *
     * 冲突策略：**同名歌单/同名标签/同一首歌都复用已有记录**，不会产生副本；
     * 导入是「合并」而不是「覆盖」。
     */
    suspend fun importJson(raw: String): ImportResult {
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: throw IllegalArgumentException("不是合法的备份文件")

        var playlists = 0
        var tags = 0
        var songs = 0

        // 歌单：旧 id → 新 id
        val playlistIdMap = HashMap<Long, Long>()
        root.arrayOrNull("playlists")?.forEach { el ->
            val o = el.asObjectOrNull() ?: return@forEach
            val oldId = o.long("id") ?: return@forEach
            val name = o.string("name") ?: return@forEach
            val newId = playlistDao.insert(
                PlaylistEntity(
                    name = name,
                    description = o.string("description"),
                    coverVariant = o.long("coverVariant")?.toInt() ?: 0,
                    createdAt = o.long("createdAt") ?: System.currentTimeMillis(),
                    updatedAt = o.long("updatedAt") ?: System.currentTimeMillis(),
                )
            )
            playlistIdMap[oldId] = newId
            playlists++
        }

        // 标签：旧 id → 新 id，同名复用
        val tagIdMap = HashMap<Long, Long>()
        root.arrayOrNull("tags")?.forEach { el ->
            val o = el.asObjectOrNull() ?: return@forEach
            val oldId = o.long("id") ?: return@forEach
            val name = o.string("name") ?: return@forEach
            val newId = createTag(name, o.long("colorIndex")?.toInt() ?: 0)
            tagIdMap[oldId] = newId
            tags++
        }

        // 收藏曲目：同一首歌（songKey）复用已有记录
        val favoriteIdMap = HashMap<Long, Long>()
        root.arrayOrNull("favorites")?.forEach { el ->
            val o = el.asObjectOrNull() ?: return@forEach
            val oldId = o.long("id") ?: return@forEach
            val songKey = o.string("songKey") ?: return@forEach
            val provider = o.string("provider") ?: return@forEach
            val songId = o.string("songId") ?: return@forEach
            val title = o.string("title") ?: return@forEach
            val existing = favoriteDao.findByKey(songKey)
            val newId = if (existing != null) {
                existing.id
            } else {
                favoriteDao.insert(
                    FavoriteEntity(
                        songKey = songKey,
                        provider = provider,
                        songId = songId,
                        title = title,
                        artist = o.string("artist").orEmpty(),
                        album = o.string("album"),
                        cover = o.string("cover"),
                        duration = o.string("duration"),
                        extraJson = o.string("extraJson"),
                        qualityOptionsJson = o.string("qualityOptionsJson"),
                        createdAt = o.long("createdAt") ?: System.currentTimeMillis(),
                    )
                )
            }
            favoriteIdMap[oldId] = newId
            songs++
        }

        // 歌单关联
        val playlistLinks = root.arrayOrNull("playlistItems")?.mapNotNull { el ->
            val o = el.asObjectOrNull() ?: return@mapNotNull null
            val oldPlaylist = o.long("playlistId") ?: return@mapNotNull null
            val oldFavorite = o.long("favoriteId") ?: return@mapNotNull null
            val newPlaylist = playlistIdMap[oldPlaylist] ?: return@mapNotNull null
            val newFavorite = favoriteIdMap[oldFavorite] ?: return@mapNotNull null
            PlaylistItemEntity(
                playlistId = newPlaylist,
                favoriteId = newFavorite,
                position = o.long("position")?.toInt() ?: 0,
                addedAt = o.long("addedAt") ?: System.currentTimeMillis(),
            )
        }.orEmpty()
        if (playlistLinks.isNotEmpty()) favoriteDao.addToPlaylist(playlistLinks)

        // 标签关联
        val tagLinks = root.arrayOrNull("favoriteTags")?.mapNotNull { el ->
            val o = el.asObjectOrNull() ?: return@mapNotNull null
            val oldFavorite = o.long("favoriteId") ?: return@mapNotNull null
            val oldTag = o.long("tagId") ?: return@mapNotNull null
            val newFavorite = favoriteIdMap[oldFavorite] ?: return@mapNotNull null
            val newTag = tagIdMap[oldTag] ?: return@mapNotNull null
            FavoriteTagEntity(favoriteId = newFavorite, tagId = newTag)
        }.orEmpty()
        if (tagLinks.isNotEmpty()) favoriteDao.linkTags(tagLinks)

        return ImportResult(playlists = playlists, tags = tags, songs = songs)
    }

    // ── JSON 小工具 ────────────────────────────────────────────

    private fun JsonObject.arrayOrNull(key: String): JsonArray? =
        (this[key] as? JsonArray)

    private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonObject.string(key: String): String? {
        val el = this[key] ?: return null
        if (el is JsonNull) return null
        return runCatching { el.jsonPrimitive.content }.getOrNull()
    }

    private fun JsonObject.long(key: String): Long? {
        val el = this[key] ?: return null
        if (el is JsonNull) return null
        return runCatching { el.jsonPrimitive.longOrNull }.getOrNull()
    }

    // ── 转换 ──────────────────────────────────────────────────
    fun songKey(item: MusicItem): String = "${item.provider}:${item.id}"

    fun entityToItem(entity: FavoriteEntity): MusicItem = MusicItem(
        id = entity.songId,
        title = entity.title,
        artist = entity.artist,
        album = entity.album,
        cover = entity.cover,
        duration = entity.duration,
        provider = entity.provider,
        extra = entity.extraJson?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() },
        qualityOptions = decodeQualityOptions(entity.qualityOptionsJson),
    )

    fun withPlaylist(entity: FavoriteEntity): MusicItem = entityToItem(entity)

    /**
     * 播放历史记录 → 曲目。
     *
     * 音质档位直接从 extra 里恢复（extra.qualityOptions），
     * 这样从历史重播时音质选择器仍然是满的。
     */
    fun entityToItem(entity: HistoryEntity): MusicItem {
        val extra = entity.extraJson?.let {
            runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull()
        }
        return MusicItem(
            id = entity.songId,
            title = entity.title,
            artist = entity.artist,
            album = entity.album,
            cover = entity.cover,
            duration = entity.duration,
            provider = entity.provider,
            extra = extra,
            qualityOptions = QualityOptions.parse(extra),
        )
    }

    private fun encodeQualityOptions(options: List<QualityOption>): String? {
        if (options.isEmpty()) return null
        val arr = JsonArray(options.map {
            json.encodeToJsonElement(
                QualityOptionSurrogate.serializer(),
                QualityOptionSurrogate(it.value, it.label, it.quality, it.format),
            )
        })
        return arr.toString()
    }

    private fun decodeQualityOptions(raw: String?): List<QualityOption> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            (json.parseToJsonElement(raw) as? JsonArray).orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val v = o["value"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: return@mapNotNull null
                QualityOption(
                    value = v,
                    label = o["label"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: v,
                    quality = o["quality"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty(),
                    format = o["format"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }

    @kotlinx.serialization.Serializable
    private data class QualityOptionSurrogate(
        val value: String,
        val label: String,
        val quality: String,
        val format: String,
    )

    companion object {
        /** 内置「默认收藏」歌单的名称。 */
        const val DEFAULT_PLAYLIST_NAME = "默认收藏"
    }

    /**
     * 保护「默认收藏」的创建。
     *
     * 收藏页 ViewModel 和首页 ViewModel 都会在 init 里调 ensureDefaultPlaylist()，
     * 冷启动时两个协程可能同时看到「还没有默认歌单」，各自插一条 → 出现两个默认歌单。
     */
    private val defaultPlaylistMutex = Mutex()
}

/** 导入结果，用于在设置页告诉用户导入了多少东西。 */
data class ImportResult(
    val playlists: Int = 0,
    val tags: Int = 0,
    val songs: Int = 0,
)
