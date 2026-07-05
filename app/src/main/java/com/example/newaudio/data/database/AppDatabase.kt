package com.example.newaudio.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.newaudio.data.database.dao.PlaylistDao
import com.example.newaudio.data.database.dao.VideoMarkerDao
import com.example.newaudio.data.database.dao.VideoPlaylistDao

@Database(
    entities = [
        SongEntity::class,
        VideoEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        VideoPlaylistEntity::class,
        VideoPlaylistItemEntity::class,
        VideoMarkerEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun videoDao(): VideoDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun videoPlaylistDao(): VideoPlaylistDao
    abstract fun videoMarkerDao(): VideoMarkerDao
}
