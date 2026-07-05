package com.example.newaudio.fake

import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.model.VideoMarker
import com.example.newaudio.domain.repository.IVideoMarkerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVideoMarkerRepository : IVideoMarkerRepository {
    private val markers = MutableStateFlow<List<VideoMarker>>(emptyList())

    val deletedVideos = mutableListOf<String>()
    val deletedFolders = mutableListOf<String>()
    val updatedVideoPaths = mutableListOf<Pair<String, String>>()
    val updatedFolderPaths = mutableListOf<Pair<String, String>>()
    val addedMarkerRequests = mutableListOf<Pair<Video, Long>>()
    val movedMarkers = mutableListOf<Pair<Long, Long>>()
    val deletedMarkers = mutableListOf<Long>()

    fun setMarkers(value: List<VideoMarker>) {
        markers.value = value
    }

    override fun observeMarkersForVideo(videoPath: String?): Flow<List<VideoMarker>> = markers

    override suspend fun getMarkersForVideo(videoPath: String): List<VideoMarker> {
        return markers.value.filter { it.videoPath == videoPath }
    }

    override suspend fun addMarker(video: Video, positionMs: Long): VideoMarker {
        addedMarkerRequests.add(video to positionMs)
        val marker = VideoMarker(
            id = (markers.value.maxOfOrNull { it.id } ?: 0L) + 1L,
            videoPath = video.path,
            filename = video.title,
            fileSize = 0L,
            durationMs = video.duration,
            positionMs = positionMs,
            createdAt = 1L,
            updatedAt = 1L
        )
        markers.value = markers.value + marker
        return marker
    }

    override suspend fun moveMarker(markerId: Long, positionMs: Long) {
        movedMarkers.add(markerId to positionMs)
        markers.value = markers.value.map {
            if (it.id == markerId) it.copy(positionMs = positionMs) else it
        }
    }

    override suspend fun deleteMarker(markerId: Long) {
        deletedMarkers.add(markerId)
        markers.value = markers.value.filterNot { it.id == markerId }
    }

    override suspend fun deleteMarkersForVideo(videoPath: String) {
        deletedVideos.add(videoPath)
        markers.value = markers.value.filterNot { it.videoPath == videoPath }
    }

    override suspend fun deleteMarkersForFolder(folderPath: String) {
        deletedFolders.add(folderPath)
        markers.value = markers.value.filterNot { it.videoPath.startsWith(folderPath) }
    }

    override suspend fun updateVideoPath(oldPath: String, newPath: String) {
        updatedVideoPaths.add(oldPath to newPath)
        markers.value = markers.value.map {
            if (it.videoPath == oldPath) it.copy(videoPath = newPath) else it
        }
    }

    override suspend fun updateVideoFolderPath(oldFolderPath: String, newFolderPath: String) {
        updatedFolderPaths.add(oldFolderPath to newFolderPath)
    }

    override suspend fun getAllMarkers(): List<VideoMarker> = markers.value
}
