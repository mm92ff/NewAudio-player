package com.example.newaudio.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.newaudio.data.database.VideoEntity
import com.example.newaudio.data.database.VideoPlaylistEntity
import com.example.newaudio.data.database.VideoPlaylistItemEntity
import com.example.newaudio.domain.model.Video
import kotlinx.coroutines.flow.Flow
import java.io.File

data class VideoPlaylistVideoResult(
    val path: String,
    val contentUri: String,
    val title: String,
    val duration: Long,
    val thumbnailUri: String?,
    val size: Long,
    val width: Int,
    val height: Int,
    val position: Int
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
}

@Dao
interface VideoPlaylistDao {

    @Query("SELECT * FROM video_playlists ORDER BY position ASC")
    fun getAllVideoPlaylists(): Flow<List<VideoPlaylistEntity>>

    @Query(
        """
        SELECT v.path, v.contentUri, v.title, v.duration, v.thumbnailUri, v.size, v.width, v.height, vpi.position
        FROM videos v
        INNER JOIN video_playlist_items vpi ON v.path = vpi.videoPath
        WHERE vpi.playlistId = :playlistId
        ORDER BY vpi.position ASC
        """
    )
    fun getVideosInPlaylist(playlistId: Long): Flow<List<VideoPlaylistVideoResult>>

    @Query(
        """
        SELECT v.path, v.contentUri, v.title, v.duration, v.thumbnailUri, v.size, v.width, v.height, vpi.position
        FROM videos v
        INNER JOIN video_playlist_items vpi ON v.path = vpi.videoPath
        WHERE vpi.playlistId = :playlistId
        ORDER BY vpi.position ASC
        """
    )
    suspend fun getVideosInPlaylistSync(playlistId: Long): List<VideoPlaylistVideoResult>

    @Query("SELECT MAX(position) FROM video_playlists")
    suspend fun getMaxPlaylistPosition(): Int?

    @Query("SELECT MAX(position) FROM video_playlist_items WHERE playlistId = :playlistId")
    suspend fun getMaxVideoPosition(playlistId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: VideoPlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistVideo(item: VideoPlaylistItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistVideos(items: List<VideoPlaylistItemEntity>)

    @Update
    suspend fun updatePlaylist(playlist: VideoPlaylistEntity)

    @androidx.room.Delete
    suspend fun deletePlaylist(playlist: VideoPlaylistEntity)

    @Query("DELETE FROM video_playlists WHERE id IN (:playlistIds)")
    suspend fun deletePlaylists(playlistIds: List<Long>)

    @Update
    suspend fun updatePlaylistsOrder(playlists: List<VideoPlaylistEntity>)

    @Query("DELETE FROM video_playlist_items WHERE playlistId = :playlistId AND videoPath = :videoPath")
    suspend fun removeVideoFromPlaylist(playlistId: Long, videoPath: String)

    @Query("DELETE FROM video_playlist_items WHERE playlistId = :playlistId AND videoPath IN (:videoPaths)")
    suspend fun removeVideosFromPlaylist(playlistId: Long, videoPaths: List<String>)

    @Update
    suspend fun updatePlaylistVideosOrder(items: List<VideoPlaylistItemEntity>)

    @Query("SELECT * FROM videos WHERE path = :path LIMIT 1")
    suspend fun findVideoByPath(path: String): VideoEntity?

    @Query("SELECT * FROM videos WHERE filename = :name AND size = :size LIMIT 1")
    suspend fun findVideoByFilenameAndSize(name: String, size: Long): VideoEntity?

    @Transaction
    suspend fun duplicatePlaylist(sourcePlaylistId: Long, newName: String) {
        val maxPos = getMaxPlaylistPosition() ?: -1
        val newPlaylistId = insertPlaylist(VideoPlaylistEntity(name = newName, position = maxPos + 1))
        val videos = getVideosInPlaylistSync(sourcePlaylistId)
        val entities = videos.map {
            VideoPlaylistItemEntity(
                playlistId = newPlaylistId,
                videoPath = it.path,
                position = it.position
            )
        }
        insertPlaylistVideos(entities)
    }

    @Transaction
    suspend fun importPlaylistWithVideos(
        playlist: VideoPlaylistEntity,
        videos: List<VideoPlaylistItemEntity>
    ): Long {
        val playlistId = insertPlaylist(playlist)
        val videosWithCorrectId = videos.map { it.copy(playlistId = playlistId) }
        insertPlaylistVideos(videosWithCorrectId)
        return playlistId
    }
}
