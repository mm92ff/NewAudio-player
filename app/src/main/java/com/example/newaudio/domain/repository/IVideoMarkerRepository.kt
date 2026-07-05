package com.example.newaudio.domain.repository

import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.model.VideoMarker
import kotlinx.coroutines.flow.Flow

interface IVideoMarkerRepository {
    fun observeMarkersForVideo(videoPath: String?): Flow<List<VideoMarker>>
    suspend fun getMarkersForVideo(videoPath: String): List<VideoMarker>
    suspend fun addMarker(video: Video, positionMs: Long): VideoMarker?
    suspend fun moveMarker(markerId: Long, positionMs: Long)
    suspend fun deleteMarker(markerId: Long)
    suspend fun deleteMarkersForVideo(videoPath: String)
    suspend fun deleteMarkersForFolder(folderPath: String)
    suspend fun updateVideoPath(oldPath: String, newPath: String)
    suspend fun updateVideoFolderPath(oldFolderPath: String, newFolderPath: String)
    suspend fun getAllMarkers(): List<VideoMarker>
}
