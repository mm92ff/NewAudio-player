package com.example.newaudio.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "video_playlists")
data class VideoPlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "video_playlist_items",
    primaryKeys = ["playlistId", "videoPath"],
    foreignKeys = [
        ForeignKey(
            entity = VideoPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["path"],
            childColumns = ["videoPath"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["videoPath"])
    ]
)
data class VideoPlaylistItemEntity(
    val playlistId: Long,
    val videoPath: String,
    val position: Int
)
