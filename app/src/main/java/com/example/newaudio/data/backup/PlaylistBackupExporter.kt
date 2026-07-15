package com.example.newaudio.data.backup

import com.example.newaudio.data.database.dao.PlaylistDao
import com.example.newaudio.data.database.dao.VideoMarkerDao
import com.example.newaudio.data.database.dao.VideoPlaylistDao
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.PlaylistExportContainer
import com.example.newaudio.domain.repository.PlaylistExportModel
import com.example.newaudio.domain.repository.SongExportModel
import com.example.newaudio.domain.repository.VideoExportModel
import com.example.newaudio.domain.repository.VideoMarkerExportModel
import com.example.newaudio.domain.repository.VideoPlaylistExportModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

class PlaylistBackupExporter @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val videoPlaylistDao: VideoPlaylistDao,
    private val videoMarkerDao: VideoMarkerDao,
    private val destination: PlaylistBackupDestination,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun export(filePath: String, userPreferences: UserPreferences): Boolean =
        withContext(ioDispatcher) {
            try {
                val playlists = playlistDao.getAllPlaylists().first().map { entity ->
                    val songs = playlistDao.getSongsInPlaylist(entity.id).first()
                    PlaylistExportModel(
                        name = entity.name,
                        createdAt = entity.createdAt,
                        songs = songs.map { song ->
                            SongExportModel(
                                path = song.path,
                                title = song.title,
                                artist = song.artist,
                                size = 0L,
                                fileHash = null
                            )
                        }
                    )
                }

                val videoPlaylists = videoPlaylistDao.getAllVideoPlaylists().first().map { entity ->
                    val videos = videoPlaylistDao.getVideosInPlaylist(entity.id).first()
                    VideoPlaylistExportModel(
                        name = entity.name,
                        createdAt = entity.createdAt,
                        videos = videos.map { video ->
                            VideoExportModel(
                                path = video.path,
                                title = video.title,
                                duration = video.duration,
                                size = video.size
                            )
                        }
                    )
                }

                val markers = videoMarkerDao.getAllMarkers().map { marker ->
                    VideoMarkerExportModel(
                        videoPath = marker.videoPath,
                        fileHash = marker.fileHash,
                        filename = marker.filename,
                        fileSize = marker.fileSize,
                        durationMs = marker.durationMs,
                        positionMs = marker.positionMs,
                        createdAt = marker.createdAt,
                        updatedAt = marker.updatedAt
                    )
                }

                val container = PlaylistExportContainer(
                    playlists = playlists,
                    settings = userPreferences,
                    videoPlaylists = videoPlaylists,
                    videoMarkers = markers
                )
                destination.writeText(filePath, Json.encodeToString(container))
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Playlist backup export failed")
                false
            }
        }
}
