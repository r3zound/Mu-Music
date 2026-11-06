package com.r3zound.music.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        FavoriteEntity::class,
        PlaylistItemEntity::class,
        TagEntity::class,
        FavoriteTagEntity::class,
        DownloadTaskEntity::class,
        HistoryEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun downloadDao(): DownloadDao
    abstract fun historyDao(): HistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        const val NAME = "mu-music.db"
    }
}
