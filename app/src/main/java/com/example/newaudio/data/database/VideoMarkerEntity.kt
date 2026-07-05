package com.example.newaudio.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.newaudio.domain.model.VideoMarker

@Entity(
    tableName = "video_markers",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["path"],
            childColumns = ["videoPath"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["videoPath"]),
        Index(value = ["fileHash"]),
        Index(value = ["filename", "fileSize", "durationMs"]),
        Index(value = ["videoPath", "positionMs"])
    ]
)
data class VideoMarkerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val videoPath: String,
    val fileHash: String?,
    val filename: String,
    val fileSize: Long,
    val durationMs: Long,
    val positionMs: Long,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomainModel(): VideoMarker = VideoMarker(
        id = id,
        videoPath = videoPath,
        fileHash = fileHash,
        filename = filename,
        fileSize = fileSize,
        durationMs = durationMs,
        positionMs = positionMs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
