package com.example.newaudio.fake

import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.model.VideoPlaylist
import com.example.newaudio.domain.repository.IVideoPlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeVideoPlaylistRepository : IVideoPlaylistRepository {

    private val playlists = MutableStateFlow<List<VideoPlaylist>>(emptyList())
    private val videosByPlaylist = MutableStateFlow<Map<Long, List<Video>>>(emptyMap())
    private var nextId = 1L

    var addedVideo: Pair<Long, Video>? = null
    var addedVideos: Pair<Long, List<Video>>? = null

    override fun getAllVideoPlaylists(): Flow<List<VideoPlaylist>> = playlists

    override suspend fun createVideoPlaylist(name: String): Long {
        val id = nextId++
        playlists.update { current ->
            current + VideoPlaylist(id = id, name = name, position = current.size)
        }
        return id
    }

    override suspend fun updateVideoPlaylist(playlist: VideoPlaylist) {
        playlists.update { current -> current.map { if (it.id == playlist.id) playlist else it } }
    }

    override suspend fun deleteVideoPlaylist(playlist: VideoPlaylist) {
        playlists.update { current -> current.filterNot { it.id == playlist.id } }
        videosByPlaylist.update { current -> current - playlist.id }
    }

    override suspend fun deleteVideoPlaylists(playlistIds: List<Long>) {
        playlists.update { current -> current.filterNot { it.id in playlistIds } }
        videosByPlaylist.update { current -> current - playlistIds.toSet() }
    }

    override suspend fun duplicateVideoPlaylist(playlist: VideoPlaylist, newName: String) {
        val id = createVideoPlaylist(newName)
        val videos = videosByPlaylist.value[playlist.id].orEmpty()
        videosByPlaylist.update { current -> current + (id to videos) }
    }

    override suspend fun updateVideoPlaylistsOrder(playlists: List<VideoPlaylist>) {
        this.playlists.value = playlists
    }

    override suspend fun addVideoToPlaylist(playlistId: Long, video: Video) {
        addedVideo = playlistId to video
        videosByPlaylist.update { current ->
            current + (playlistId to (current[playlistId].orEmpty() + video))
        }
    }

    override suspend fun addVideosToPlaylist(playlistId: Long, videos: List<Video>) {
        addedVideos = playlistId to videos
        videosByPlaylist.update { current ->
            current + (playlistId to (current[playlistId].orEmpty() + videos))
        }
    }

    override suspend fun removeVideoFromPlaylist(playlistId: Long, videoPath: String) {
        videosByPlaylist.update { current ->
            current + (playlistId to current[playlistId].orEmpty().filterNot { it.path == videoPath })
        }
    }

    override suspend fun removeVideosFromPlaylist(playlistId: Long, videoPaths: List<String>) {
        videosByPlaylist.update { current ->
            current + (playlistId to current[playlistId].orEmpty().filterNot { it.path in videoPaths })
        }
    }

    override suspend fun updatePlaylistVideosOrder(playlistId: Long, videos: List<Video>) {
        videosByPlaylist.update { current -> current + (playlistId to videos) }
    }

    override suspend fun swapVideosInPlaylist(
        playlistId: Long,
        videoPath1: String,
        position1: Int,
        videoPath2: String,
        position2: Int
    ) = Unit

    override fun getVideosInPlaylist(playlistId: Long): Flow<List<Video>> {
        return videosByPlaylist.map { it[playlistId].orEmpty() }
    }
}
