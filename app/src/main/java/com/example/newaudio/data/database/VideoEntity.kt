package com.example.newaudio.data.database

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.newaudio.domain.model.Video
import java.io.File

@Entity(
    tableName = "videos",
    indices = [
        Index(value = ["parentPath"]),
        Index(value = ["title"]),
        Index(value = ["filename"]),
        Index(value = ["filename", "size"]),
        Index(value = ["parentPath", "title"]),
        Index(value = ["fileHash"])
    ]
)
data class VideoEntity(
    @PrimaryKey
    val path: String,
    val contentUri: String,
    val title: String,
    val duration: Long,
    val thumbnailUri: String?,
    val parentPath: String,
    val filename: String,
    val lastModified: Long,
    val size: Long,
    val width: Int = 0,
    val height: Int = 0,
    val fileHash: String? = null
) {
    fun toDomainModel(): Video {
        return Video(
            path = path,
            contentUri = contentUri,
            title = title.takeIf { it.isNotBlank() } ?: File(path).nameWithoutExtension.ifBlank { "Unknown Video" },
            duration = duration,
            thumbnailUri = thumbnailUri,
            width = width,
            height = height
        )
    }

    companion object {
        fun fromDomainModel(
            video: Video,
            parentPath: String,
            lastModified: Long = 0,
            size: Long = 0,
            fileHash: String? = null
        ): VideoEntity {
            val safeUri = if (video.contentUri.isNotBlank()) {
                video.contentUri
            } else {
                Uri.fromFile(File(video.path)).toString()
            }

            return VideoEntity(
                path = video.path,
                contentUri = safeUri,
                title = video.title,
                duration = video.duration,
                thumbnailUri = video.thumbnailUri,
                parentPath = parentPath,
                filename = File(video.path).name,
                lastModified = lastModified,
                size = size,
                width = video.width,
                height = video.height,
                fileHash = fileHash
            )
        }
    }
}
