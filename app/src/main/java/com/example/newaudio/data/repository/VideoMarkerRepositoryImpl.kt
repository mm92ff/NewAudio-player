package com.example.newaudio.data.repository

import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.data.database.VideoMarkerEntity
import com.example.newaudio.data.database.dao.VideoMarkerDao
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.model.VideoMarker
import com.example.newaudio.domain.repository.IVideoMarkerRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class VideoMarkerRepositoryImpl @Inject constructor(
    private val markerDao: VideoMarkerDao,
    private val videoDao: VideoDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IVideoMarkerRepository {

    override fun observeMarkersForVideo(videoPath: String?): Flow<List<VideoMarker>> {
        return if (videoPath.isNullOrBlank()) {
            flowOf(emptyList())
        } else {
            markerDao.observeMarkersForVideo(videoPath)
                .map { markers -> markers.map { it.toDomainModel() } }
                .flowOn(ioDispatcher)
        }
    }

    override suspend fun getMarkersForVideo(videoPath: String): List<VideoMarker> = withContext(ioDispatcher) {
        markerDao.getMarkersForVideo(videoPath).map { it.toDomainModel() }
    }

    override suspend fun addMarker(video: Video, positionMs: Long): VideoMarker? = withContext(ioDispatcher) {
        val entity = videoDao.getVideoByPath(video.path)
        val duration = (entity?.duration ?: video.duration).coerceAtLeast(0L)
        val safePosition = positionMs.coerceIn(0L, duration.coerceAtLeast(0L))
        val existing = markerDao.getMarkersForVideo(video.path)
            .firstOrNull { abs(it.positionMs - safePosition) <= DUPLICATE_WINDOW_MS }

        if (existing != null) {
            return@withContext existing.toDomainModel()
        }

        val now = System.currentTimeMillis()
        val file = File(video.path)
        val marker = VideoMarkerEntity(
            videoPath = video.path,
            fileHash = entity?.fileHash,
            filename = entity?.filename ?: file.name,
            fileSize = entity?.size ?: file.takeIf { it.exists() }?.length().orZero(),
            durationMs = duration,
            positionMs = safePosition,
            createdAt = now,
            updatedAt = now
        )
        val id = markerDao.insert(marker)
        marker.copy(id = id).toDomainModel()
    }

    override suspend fun moveMarker(markerId: Long, positionMs: Long) = withContext(ioDispatcher) {
        markerDao.updatePosition(markerId, positionMs.coerceAtLeast(0L), System.currentTimeMillis())
    }

    override suspend fun deleteMarker(markerId: Long) = withContext(ioDispatcher) {
        markerDao.deleteById(markerId)
    }

    override suspend fun deleteMarkersForVideo(videoPath: String) = withContext(ioDispatcher) {
        markerDao.deleteByVideoPath(videoPath)
    }

    override suspend fun deleteMarkersForFolder(folderPath: String) = withContext(ioDispatcher) {
        markerDao.deleteByFolder(folderPath)
    }

    override suspend fun updateVideoPath(oldPath: String, newPath: String) = withContext(ioDispatcher) {
        markerDao.updateVideoPath(
            oldPath = oldPath,
            newPath = newPath,
            newFilename = File(newPath).name,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun updateVideoFolderPath(oldFolderPath: String, newFolderPath: String) = withContext(ioDispatcher) {
        val normalizedOld = oldFolderPath.trimEnd('/')
        val normalizedNew = newFolderPath.trimEnd('/')
        markerDao.getAllMarkers()
            .filter { marker ->
                marker.videoPath == normalizedOld || marker.videoPath.startsWith("$normalizedOld/")
            }
            .forEach { marker ->
                val suffix = marker.videoPath.removePrefix(normalizedOld).trimStart('/')
                val newPath = if (suffix.isBlank()) normalizedNew else "$normalizedNew/$suffix"
                markerDao.updateVideoPath(
                    oldPath = marker.videoPath,
                    newPath = newPath,
                    newFilename = File(newPath).name,
                    updatedAt = System.currentTimeMillis()
                )
            }
    }

    override suspend fun getAllMarkers(): List<VideoMarker> = withContext(ioDispatcher) {
        markerDao.getAllMarkers().map { it.toDomainModel() }
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private companion object {
        const val DUPLICATE_WINDOW_MS = 1_000L
    }
}
