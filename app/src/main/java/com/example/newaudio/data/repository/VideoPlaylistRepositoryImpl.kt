package com.example.newaudio.data.repository

import com.example.newaudio.data.database.VideoPlaylistEntity
import com.example.newaudio.data.database.VideoPlaylistItemEntity
import com.example.newaudio.data.database.dao.VideoPlaylistDao
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.model.VideoPlaylist
import com.example.newaudio.domain.repository.IVideoPlaylistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoPlaylistRepositoryImpl @Inject constructor(
    private val videoPlaylistDao: VideoPlaylistDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IVideoPlaylistRepository {

    override fun getAllVideoPlaylists(): Flow<List<VideoPlaylist>> {
        return videoPlaylistDao.getAllVideoPlaylists()
            .map { entities -> entities.map { it.toDomainModel() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun createVideoPlaylist(name: String): Long = withContext(ioDispatcher) {
        val maxPos = videoPlaylistDao.getMaxPlaylistPosition() ?: -1
        videoPlaylistDao.insertPlaylist(VideoPlaylistEntity(name = name, position = maxPos + 1))
    }

    override suspend fun updateVideoPlaylist(playlist: VideoPlaylist) = withContext(ioDispatcher) {
        videoPlaylistDao.updatePlaylist(playlist.toEntity())
    }

    override suspend fun deleteVideoPlaylist(playlist: VideoPlaylist) = withContext(ioDispatcher) {
        videoPlaylistDao.deletePlaylist(playlist.toEntity())
    }

    override suspend fun deleteVideoPlaylists(playlistIds: List<Long>) = withContext(ioDispatcher) {
        videoPlaylistDao.deletePlaylists(playlistIds)
    }

    override suspend fun duplicateVideoPlaylist(playlist: VideoPlaylist, newName: String) = withContext(ioDispatcher) {
        videoPlaylistDao.duplicatePlaylist(playlist.id, newName)
    }

    override suspend fun updateVideoPlaylistsOrder(playlists: List<VideoPlaylist>) = withContext(ioDispatcher) {
        videoPlaylistDao.updatePlaylistsOrder(playlists.map { it.toEntity() })
    }

    override suspend fun addVideoToPlaylist(playlistId: Long, video: Video) = withContext(ioDispatcher) {
        val maxPos = videoPlaylistDao.getMaxVideoPosition(playlistId) ?: -1
        videoPlaylistDao.insertPlaylistVideo(
            VideoPlaylistItemEntity(
                playlistId = playlistId,
                videoPath = video.path,
                position = maxPos + 1
            )
        )
    }

    override suspend fun addVideosToPlaylist(playlistId: Long, videos: List<Video>) = withContext(ioDispatcher) {
        val maxPos = videoPlaylistDao.getMaxVideoPosition(playlistId) ?: -1
        val entities = videos.mapIndexed { index, video ->
            VideoPlaylistItemEntity(
                playlistId = playlistId,
                videoPath = video.path,
                position = maxPos + 1 + index
            )
        }
        videoPlaylistDao.insertPlaylistVideos(entities)
    }

    override suspend fun removeVideoFromPlaylist(playlistId: Long, videoPath: String) = withContext(ioDispatcher) {
        videoPlaylistDao.removeVideoFromPlaylist(playlistId, videoPath)
    }

    override suspend fun removeVideosFromPlaylist(playlistId: Long, videoPaths: List<String>) = withContext(ioDispatcher) {
        videoPlaylistDao.removeVideosFromPlaylist(playlistId, videoPaths)
    }

    override suspend fun updatePlaylistVideosOrder(playlistId: Long, videos: List<Video>) = withContext(ioDispatcher) {
        val entities = videos.mapIndexed { index, video ->
            VideoPlaylistItemEntity(playlistId, video.path, index)
        }
        videoPlaylistDao.updatePlaylistVideosOrder(entities)
    }

    override suspend fun swapVideosInPlaylist(
        playlistId: Long,
        videoPath1: String,
        position1: Int,
        videoPath2: String,
        position2: Int
    ) = withContext(ioDispatcher) {
        videoPlaylistDao.updatePlaylistVideosOrder(
            listOf(
                VideoPlaylistItemEntity(playlistId, videoPath1, position1),
                VideoPlaylistItemEntity(playlistId, videoPath2, position2)
            )
        )
    }

    override fun getVideosInPlaylist(playlistId: Long): Flow<List<Video>> {
        return videoPlaylistDao.getVideosInPlaylist(playlistId)
            .map { results -> results.map { it.toDomainModel() } }
            .flowOn(ioDispatcher)
    }

    private fun VideoPlaylistEntity.toDomainModel(): VideoPlaylist {
        return VideoPlaylist(
            id = id,
            name = name,
            position = position,
            createdAt = createdAt
        )
    }

    private fun VideoPlaylist.toEntity(): VideoPlaylistEntity {
        return VideoPlaylistEntity(
            id = id,
            name = name,
            position = position,
            createdAt = createdAt
        )
    }
}
