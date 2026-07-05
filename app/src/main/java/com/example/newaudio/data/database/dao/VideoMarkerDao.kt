package com.example.newaudio.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newaudio.data.database.VideoMarkerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoMarkerDao {

    @Query("SELECT * FROM video_markers WHERE videoPath = :videoPath ORDER BY positionMs ASC")
    fun observeMarkersForVideo(videoPath: String): Flow<List<VideoMarkerEntity>>

    @Query("SELECT * FROM video_markers WHERE videoPath = :videoPath ORDER BY positionMs ASC")
    suspend fun getMarkersForVideo(videoPath: String): List<VideoMarkerEntity>

    @Query("SELECT * FROM video_markers ORDER BY videoPath ASC, positionMs ASC")
    suspend fun getAllMarkers(): List<VideoMarkerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(marker: VideoMarkerEntity): Long

    @Query("UPDATE video_markers SET positionMs = :positionMs, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePosition(id: Long, positionMs: Long, updatedAt: Long)

    @Query(
        """
        UPDATE video_markers
        SET videoPath = :newPath,
            filename = :newFilename,
            updatedAt = :updatedAt
        WHERE videoPath = :oldPath OR videoPath = :newPath
        """
    )
    suspend fun updateVideoPath(oldPath: String, newPath: String, newFilename: String, updatedAt: Long)

    @Query("DELETE FROM video_markers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM video_markers WHERE videoPath = :videoPath")
    suspend fun deleteByVideoPath(videoPath: String)

    @Query(
        """
        DELETE FROM video_markers
        WHERE videoPath = :folderPath
           OR videoPath LIKE :folderPath || '/%'
        """
    )
    suspend fun deleteByFolder(folderPath: String)
}
