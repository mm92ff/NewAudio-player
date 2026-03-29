package com.example.newaudio.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.newaudio.data.database.dao.PlaylistDao

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
}
