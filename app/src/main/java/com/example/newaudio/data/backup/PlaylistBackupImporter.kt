package com.example.newaudio.data.backup

import com.example.newaudio.data.database.DatabaseTransactionRunner
import com.example.newaudio.data.database.PlaylistEntity
import com.example.newaudio.data.database.PlaylistSongEntity
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.data.database.VideoMarkerEntity
import com.example.newaudio.data.database.VideoPlaylistEntity
import com.example.newaudio.data.database.VideoPlaylistItemEntity
import com.example.newaudio.data.database.dao.PlaylistDao
import com.example.newaudio.data.database.dao.VideoMarkerDao
import com.example.newaudio.data.database.dao.VideoPlaylistDao
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.repository.ImportFailure
import com.example.newaudio.domain.repository.ImportResult
import com.example.newaudio.domain.repository.PlaylistExportContainer
import com.example.newaudio.domain.repository.VideoMarkerExportModel
import com.example.newaudio.util.Constants
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

class PlaylistBackupImporter @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val videoPlaylistDao: VideoPlaylistDao,
    private val videoDao: VideoDao,
    private val videoMarkerDao: VideoMarkerDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val source: PlaylistBackupSource,
    private val validator: PlaylistImportValidator,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun import(filePath: String): ImportResult = withContext(ioDispatcher) {
        try {
            val jsonString = source.readText(filePath, Constants.Security.MAX_IMPORT_BYTES)
            val container = LENIENT_JSON.decodeFromString<PlaylistExportContainer>(jsonString)
            validator.validate(container)

            transactionRunner.run {
                val stats = MutableImportStats()
                importAudioPlaylists(container, stats)
                importVideoPlaylists(container, stats)
                importVideoMarkers(container, stats)
                stats.toResult(container)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: PlaylistBackupException) {
            Timber.e(error, "Import rejected: ${error.failure}")
            ImportResult(0, 0, 0, 0, failure = error.failure)
        } catch (error: SerializationException) {
            Timber.e(error, "Import JSON is invalid")
            ImportResult(0, 0, 0, 0, failure = ImportFailure.INVALID_FORMAT)
        } catch (error: FileNotFoundException) {
            Timber.e(error, "Import file not found")
            ImportResult(0, 0, 0, 0, failure = ImportFailure.NOT_FOUND)
        } catch (error: Exception) {
            Timber.e(error, "Import failed")
            ImportResult(0, 0, 0, 0, failure = ImportFailure.IO_ERROR)
        }
    }

    private suspend fun importAudioPlaylists(
        container: PlaylistExportContainer,
        stats: MutableImportStats
    ) {
        container.playlists.forEachIndexed { playlistIndex, exportModel ->
            val playlist = PlaylistEntity(
                name = exportModel.name,
                position = playlistIndex,
                createdAt = exportModel.createdAt
            )
            val songs = exportModel.songs.mapIndexedNotNull { songIndex, song ->
                val directMatch = playlistDao.findSongByPath(song.path)
                val hashMatch = if (directMatch == null) {
                    song.fileHash?.let { playlistDao.findSongByHash(it) }
                } else {
                    null
                }
                val sizeMatch = if (directMatch == null && hashMatch == null && song.size > 0L) {
                    playlistDao.findSongByFilenameAndSize(File(song.path).name, song.size)
                } else {
                    null
                }

                val finalPath = when {
                    directMatch != null -> directMatch.path.also { stats.songsFound++ }
                    hashMatch != null -> hashMatch.path.also { stats.songsFixed++ }
                    sizeMatch != null -> sizeMatch.path.also { stats.songsFixed++ }
                    File(song.path).exists() -> song.path.also { stats.songsFound++ }
                    else -> null
                }

                if (finalPath == null) {
                    stats.songsNotFound++
                    null
                } else {
                    PlaylistSongEntity(playlistId = 0L, songPath = finalPath, position = songIndex)
                }
            }
            playlistDao.importPlaylistWithSongs(playlist, songs)
            stats.playlistsImported++
        }
    }

    private suspend fun importVideoPlaylists(
        container: PlaylistExportContainer,
        stats: MutableImportStats
    ) {
        container.videoPlaylists.forEachIndexed { playlistIndex, exportModel ->
            val playlist = VideoPlaylistEntity(
                name = exportModel.name,
                position = playlistIndex,
                createdAt = exportModel.createdAt
            )
            val videos = exportModel.videos.mapIndexedNotNull { videoIndex, video ->
                val directMatch = videoPlaylistDao.findVideoByPath(video.path)
                val sizeMatch = if (directMatch == null && video.size > 0L) {
                    videoPlaylistDao.findVideoByFilenameAndSize(File(video.path).name, video.size)
                } else {
                    null
                }

                val finalPath = when {
                    directMatch != null -> directMatch.path.also { stats.songsFound++ }
                    sizeMatch != null -> sizeMatch.path.also { stats.songsFixed++ }
                    File(video.path).exists() -> video.path.also { stats.songsFound++ }
                    else -> null
                }

                if (finalPath == null) {
                    stats.songsNotFound++
                    null
                } else {
                    VideoPlaylistItemEntity(
                        playlistId = 0L,
                        videoPath = finalPath,
                        position = videoIndex
                    )
                }
            }
            videoPlaylistDao.importPlaylistWithVideos(playlist, videos)
            stats.playlistsImported++
        }
    }

    private suspend fun importVideoMarkers(
        container: PlaylistExportContainer,
        stats: MutableImportStats
    ) {
        container.videoMarkers.forEach { markerExport ->
            val video = resolveMarkerVideo(markerExport)
            if (video == null) {
                stats.songsNotFound++
                return@forEach
            }

            val duplicate = videoMarkerDao.getMarkersForVideo(video.path)
                .any { marker ->
                    kotlin.math.abs(marker.positionMs - markerExport.positionMs) <= 1_000L
                }
            if (!duplicate) {
                val duration = video.duration.takeIf { it > 0L }
                    ?: markerExport.durationMs
                videoMarkerDao.insert(
                    VideoMarkerEntity(
                        videoPath = video.path,
                        fileHash = video.fileHash ?: markerExport.fileHash,
                        filename = video.filename,
                        fileSize = video.size.takeIf { it > 0L } ?: markerExport.fileSize,
                        durationMs = duration,
                        positionMs = markerExport.positionMs.coerceIn(
                            minimumValue = 0L,
                            maximumValue = duration.coerceAtLeast(0L)
                        ),
                        createdAt = markerExport.createdAt,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                stats.songsFixed++
            }
        }
    }

    private suspend fun resolveMarkerVideo(marker: VideoMarkerExportModel) =
        videoDao.getVideoByPath(marker.videoPath)
            ?: marker.fileHash?.let { videoDao.findVideoByHash(it) }
            ?: videoDao.findVideoByFilenameSizeAndDuration(
                filename = marker.filename,
                size = marker.fileSize,
                duration = marker.durationMs
            )

    private class MutableImportStats(
        var playlistsImported: Int = 0,
        var songsFound: Int = 0,
        var songsFixed: Int = 0,
        var songsNotFound: Int = 0
    ) {
        fun toResult(container: PlaylistExportContainer) = ImportResult(
            playlistsImported = playlistsImported,
            songsFound = songsFound,
            songsFixed = songsFixed,
            songsNotFound = songsNotFound,
            restoredPreferences = container.settings
        )
    }

    private companion object {
        val LENIENT_JSON = Json { ignoreUnknownKeys = true }
    }
}
