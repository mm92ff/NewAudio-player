package com.example.newaudio.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DirectSubFolderVideoCount(
    val path: String,
    val videoCount: Int
)

data class VideoMinimal(
    val path: String,
    val contentUri: String,
    val title: String,
    val duration: Long,
    val thumbnailUri: String?,
    val parentPath: String,
    val filename: String,
    val width: Int,
    val height: Int
)

@Dao
interface VideoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<VideoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: VideoEntity)

    @Query("DELETE FROM videos WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query(
        """
        DELETE FROM videos
        WHERE parentPath = :folderPath
           OR parentPath LIKE :folderPath || '/%'
           OR path LIKE :folderPath || '/%'
        """
    )
    suspend fun deleteByFolder(folderPath: String)

    @Query("DELETE FROM videos")
    suspend fun clearAll()

    @Query(
        """
        SELECT path, contentUri, title, duration, thumbnailUri, parentPath, filename, width, height
        FROM videos
        WHERE parentPath = :parentPath
        ORDER BY title ASC
        """
    )
    fun observeVideosInFolderMinimal(parentPath: String): Flow<List<VideoMinimal>>

    @Query(
        """
        SELECT DISTINCT parentPath
        FROM videos
        WHERE parentPath LIKE :parentPath || '/%'
          AND parentPath NOT LIKE :parentPath || '/%/%'
        """
    )
    fun observeSubFolders(parentPath: String): Flow<List<String>>

    @Query(
        """
        SELECT parentPath AS path, COUNT(*) AS videoCount
        FROM videos
        WHERE parentPath LIKE :parentPath || '/%'
        GROUP BY parentPath
        """
    )
    fun observeAllSubFolderVideoCounts(parentPath: String): Flow<List<DirectSubFolderVideoCount>>

    @Query("SELECT * FROM videos WHERE path = :path")
    suspend fun getVideoByPath(path: String): VideoEntity?

    @Query("SELECT * FROM videos WHERE fileHash = :fileHash LIMIT 1")
    suspend fun findVideoByHash(fileHash: String): VideoEntity?

    @Query(
        """
        SELECT * FROM videos
        WHERE filename = :filename
          AND size = :size
          AND duration = :duration
        LIMIT 1
        """
    )
    suspend fun findVideoByFilenameSizeAndDuration(
        filename: String,
        size: Long,
        duration: Long
    ): VideoEntity?

    @Query("SELECT COUNT(*) FROM videos")
    suspend fun countAllVideos(): Int

    @Query("SELECT * FROM videos WHERE parentPath = :parentPath ORDER BY title ASC")
    suspend fun getVideosInFolderSync(parentPath: String): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE path LIKE :parentPath || '/%'")
    suspend fun getAllVideosInTree(parentPath: String): List<VideoEntity>

    @Query("SELECT path FROM videos WHERE path LIKE :parentPath || '/%'")
    suspend fun getAllVideoPathsInTree(parentPath: String): List<String>

    @Query("DELETE FROM videos WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    @Query(
        """
        UPDATE videos
        SET path = :newPath,
            contentUri = :newContentUri,
            parentPath = :newParentPath,
            filename = :newFilename
        WHERE path = :oldPath
        """
    )
    suspend fun updatePath(
        oldPath: String,
        newPath: String,
        newContentUri: String,
        newParentPath: String,
        newFilename: String
    )

    @Query("UPDATE videos SET contentUri = :contentUri WHERE path = :path")
    suspend fun updateContentUri(path: String, contentUri: String)
}
