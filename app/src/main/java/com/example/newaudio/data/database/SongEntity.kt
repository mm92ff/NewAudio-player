package com.example.newaudio.data.database

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.newaudio.domain.model.Song
import java.io.File

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["parentPath"]),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["filename"]),
        Index(value = ["filename", "size"]),
        Index(value = ["parentPath", "title"]),
        Index(value = ["fileHash"]) // Index for fast lookup after moving files
    ]
)
data class SongEntity(
    @PrimaryKey
    val path: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val albumArtPath: String?,
    val parentPath: String,
    val filename: String,
    val lastModified: Long,
    val size: Long,
    val fileHash: String? = null // For stable matching during import/move operations
) {
    fun toDomainModel(): Song {
        return Song(
            path = path,
            contentUri = contentUri,
            title = title,
            artist = artist,
            duration = duration,
            albumArtPath = albumArtPath
        )
    }

    companion object {
        fun fromDomainModel(
            song: Song, 
            parentPath: String, 
            lastModified: Long = 0, 
            size: Long = 0,
            fileHash: String? = null
        ): SongEntity {
            val safeUri = if (song.contentUri.isNotBlank()) {
                song.contentUri
            } else {
                Uri.fromFile(File(song.path)).toString()
            }

            return SongEntity(
                path = song.path,
                contentUri = safeUri,
                title = song.title,
                artist = song.artist,
                album = "Unknown",
                duration = song.duration,
                albumArtPath = song.albumArtPath,
                parentPath = parentPath,
                filename = File(song.path).name,
                lastModified = lastModified,
                size = size,
                fileHash = fileHash
            )
        }
    }
}
