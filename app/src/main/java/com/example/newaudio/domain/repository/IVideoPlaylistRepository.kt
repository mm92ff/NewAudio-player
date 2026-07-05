package com.example.newaudio.domain.repository

import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.model.VideoPlaylist
import kotlinx.coroutines.flow.Flow

interface IVideoPlaylistRepository {
    fun getAllVideoPlaylists(): Flow<List<VideoPlaylist>>
    suspend fun createVideoPlaylist(name: String): Long
    suspend fun updateVideoPlaylist(playlist: VideoPlaylist)
    suspend fun deleteVideoPlaylist(playlist: VideoPlaylist)
    suspend fun deleteVideoPlaylists(playlistIds: List<Long>)
    suspend fun duplicateVideoPlaylist(playlist: VideoPlaylist, newName: String)
    suspend fun updateVideoPlaylistsOrder(playlists: List<VideoPlaylist>)
    suspend fun addVideoToPlaylist(playlistId: Long, video: Video)
    suspend fun addVideosToPlaylist(playlistId: Long, videos: List<Video>)
    suspend fun removeVideoFromPlaylist(playlistId: Long, videoPath: String)
    suspend fun removeVideosFromPlaylist(playlistId: Long, videoPaths: List<String>)
    suspend fun updatePlaylistVideosOrder(playlistId: Long, videos: List<Video>)
    suspend fun swapVideosInPlaylist(
        playlistId: Long,
        videoPath1: String,
        position1: Int,
        videoPath2: String,
        position2: Int
    )
    fun getVideosInPlaylist(playlistId: Long): Flow<List<Video>>
}
