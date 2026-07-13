package com.example.newaudio.data.repository

import android.content.Context
import android.net.Uri
import com.example.newaudio.data.database.PlaylistEntity
import com.example.newaudio.data.database.PlaylistSongEntity
import com.example.newaudio.data.database.DatabaseTransactionRunner
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.data.database.VideoMarkerEntity
import com.example.newaudio.data.database.VideoPlaylistEntity
import com.example.newaudio.data.database.VideoPlaylistItemEntity
import com.example.newaudio.data.database.dao.PlaylistDao
import com.example.newaudio.data.database.dao.VideoMarkerDao
import com.example.newaudio.data.database.dao.VideoPlaylistDao
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IPlaylistRepository
import com.example.newaudio.domain.repository.ImportResult
import com.example.newaudio.domain.repository.ImportFailure
import com.example.newaudio.domain.repository.PlaylistExportContainer
import com.example.newaudio.domain.repository.PlaylistExportModel
import com.example.newaudio.domain.repository.SongExportModel
import com.example.newaudio.domain.repository.VideoExportModel
import com.example.newaudio.domain.repository.VideoMarkerExportModel
import com.example.newaudio.domain.repository.VideoPlaylistExportModel
import com.example.newaudio.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val videoPlaylistDao: VideoPlaylistDao,
    private val videoDao: VideoDao,
    private val videoMarkerDao: VideoMarkerDao,
    private val transactionRunner: DatabaseTransactionRunner,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IPlaylistRepository {

    override fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists()
            .map { entities ->
                entities.map { Playlist(it.id, it.name, it.position, it.createdAt) }
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun createPlaylist(name: String): Long = withContext(ioDispatcher) {
        val maxPos = playlistDao.getMaxPlaylistPosition() ?: -1
        playlistDao.insertPlaylist(PlaylistEntity(name = name, position = maxPos + 1))
    }

    override suspend fun updatePlaylist(playlist: Playlist) = withContext(ioDispatcher) {
        playlistDao.updatePlaylist(PlaylistEntity(playlist.id, playlist.name, playlist.position, playlist.createdAt))
    }

    override suspend fun deletePlaylist(playlist: Playlist) = withContext(ioDispatcher) {
        playlistDao.deletePlaylist(PlaylistEntity(playlist.id, playlist.name, playlist.position, playlist.createdAt))
    }

    // ✅ NEW: Batch delete playlists
    override suspend fun deletePlaylists(playlistIds: List<Long>) = withContext(ioDispatcher) {
        playlistDao.deletePlaylists(playlistIds)
    }

    override suspend fun duplicatePlaylist(playlist: Playlist, newName: String) = withContext(ioDispatcher) {
        playlistDao.duplicatePlaylist(playlist.id, newName)
    }

    override suspend fun updatePlaylistsOrder(playlists: List<Playlist>) = withContext(ioDispatcher) {
        val entities = playlists.map { PlaylistEntity(it.id, it.name, it.position, it.createdAt) }
        playlistDao.updatePlaylistsOrder(entities)
    }

    override suspend fun addSongToPlaylist(playlistId: Long, song: Song) = withContext(ioDispatcher) {
        val maxPos = playlistDao.getMaxSongPosition(playlistId) ?: -1
        playlistDao.insertPlaylistSong(
            PlaylistSongEntity(
                playlistId = playlistId,
                songPath = song.path,
                position = maxPos + 1
            )
        )
    }

    // ✅ NEW: Batch add songs (optimized for FileBrowser multi-select)
    override suspend fun addSongsToPlaylist(playlistId: Long, songs: List<Song>) = withContext(ioDispatcher) {
        // 1. Get highest position (only 1 DB access)
        val maxPos = playlistDao.getMaxSongPosition(playlistId) ?: -1

        // 2. Prepare entities with sequential positions
        val entities = songs.mapIndexed { index, song ->
            PlaylistSongEntity(
                playlistId = playlistId,
                songPath = song.path,
                position = maxPos + 1 + index
            )
        }

        // 3. Insert everything at once (only 1 DB transaction)
        playlistDao.insertPlaylistSongs(entities)
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songPath: String) = withContext(ioDispatcher) {
        playlistDao.removeSongFromPlaylist(playlistId, songPath)
    }

    // ✅ NEW: Batch remove songs
    override suspend fun removeSongsFromPlaylist(playlistId: Long, songPaths: List<String>) = withContext(ioDispatcher) {
        playlistDao.removeSongsFromPlaylist(playlistId, songPaths)
    }

    override suspend fun updatePlaylistSongsOrder(playlistId: Long, songs: List<Song>) = withContext(ioDispatcher) {
        val entities = songs.mapIndexed { index, song ->
            PlaylistSongEntity(playlistId, song.path, index)
        }
        playlistDao.updatePlaylistSongsOrder(entities)
    }

    override suspend fun swapSongsInPlaylist(
        playlistId: Long,
        songPath1: String,
        position1: Int,
        songPath2: String,
        position2: Int
    ) = withContext(ioDispatcher) {
        val update1 = PlaylistSongEntity(playlistId, songPath1, position1)
        val update2 = PlaylistSongEntity(playlistId, songPath2, position2)
        playlistDao.updatePlaylistSongsOrder(listOf(update1, update2))
    }

    override fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> {
        return playlistDao.getSongsInPlaylist(playlistId)
            .map { results ->
                results.map { it.toDomainModel() }
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun exportPlaylists(filePath: String, userPreferences: UserPreferences): Boolean = withContext(ioDispatcher) {
        try {
            val playlists = playlistDao.getAllPlaylists().first()
            val exportList = playlists.map { entity ->
                val songs = playlistDao.getSongsInPlaylist(entity.id).first()
                PlaylistExportModel(
                    name = entity.name,
                    createdAt = entity.createdAt,
                    songs = songs.map {
                        SongExportModel(it.path, it.title, it.artist, 0L, null)
                    }
                )
            }

            val videoPlaylists = videoPlaylistDao.getAllVideoPlaylists().first()
            val videoExportList = videoPlaylists.map { entity ->
                val videos = videoPlaylistDao.getVideosInPlaylist(entity.id).first()
                VideoPlaylistExportModel(
                    name = entity.name,
                    createdAt = entity.createdAt,
                    videos = videos.map {
                        VideoExportModel(
                            path = it.path,
                            title = it.title,
                            duration = it.duration,
                            size = it.size
                        )
                    }
                )
            }

            val container = PlaylistExportContainer(
                playlists = exportList,
                settings = userPreferences,
                videoPlaylists = videoExportList,
                videoMarkers = videoMarkerDao.getAllMarkers().map { marker ->
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
            )
            val jsonString = Json.encodeToString(container)

            val uri = Uri.parse(filePath)

            val outputStream = if (uri.scheme == "file") {
                FileOutputStream(File(requireNotNull(uri.path) { "Invalid file URI: $uri" }))
            } else {
                context.contentResolver.openOutputStream(uri)
                    ?: throw FileNotFoundException("Could not open output stream for $uri")
            }
            outputStream.use { it.write(jsonString.toByteArray()) }

            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun importPlaylists(filePath: String): ImportResult = withContext(ioDispatcher) {
        var playlistsImported = 0
        var songsFound = 0
        var songsFixed = 0
        var songsNotFound = 0
        var restoredPreferences: UserPreferences? = null

        try {
            val jsonString = readImportText(filePath)
            val lenientJson = Json { ignoreUnknownKeys = true }
            val container = lenientJson.decodeFromString<PlaylistExportContainer>(jsonString)
            validateImport(container)

            transactionRunner.run {
                restoredPreferences = container.settings

                container.playlists.forEachIndexed { pIdx, exportModel ->
                val playlistEntity = PlaylistEntity(
                    name = exportModel.name,
                    position = pIdx,
                    createdAt = exportModel.createdAt
                )

                // Collect songs that can be resolved
                val resolvedSongs = mutableListOf<PlaylistSongEntity>()

                exportModel.songs.forEachIndexed { index, songExport ->
                    var finalPath: String? = null

                    val directMatch = playlistDao.findSongByPath(songExport.path)
                    if (directMatch != null) {
                        finalPath = directMatch.path
                        songsFound++
                    } else {
                        val hashMatch = songExport.fileHash?.let { playlistDao.findSongByHash(it) }
                        if (hashMatch != null) {
                            finalPath = hashMatch.path
                            songsFixed++
                        } else {
                            val fileName = File(songExport.path).name
                            val sizeMatch = if (songExport.size > 0) {
                                playlistDao.findSongByFilenameAndSize(fileName, songExport.size)
                            } else null

                            if (sizeMatch != null) {
                                finalPath = sizeMatch.path
                                songsFixed++
                            } else {
                                if (File(songExport.path).exists()) {
                                    finalPath = songExport.path
                                    songsFound++
                                }
                            }
                        }
                    }

                    if (finalPath != null) {
                        resolvedSongs.add(PlaylistSongEntity(0L, finalPath, index)) // playlistId will be set in DAO
                    } else {
                        songsNotFound++
                    }
                }

                // Import playlist and songs in a single transaction
                playlistDao.importPlaylistWithSongs(playlistEntity, resolvedSongs)
                playlistsImported++
            }

                container.videoPlaylists.forEachIndexed { pIdx, exportModel ->
                val playlistEntity = VideoPlaylistEntity(
                    name = exportModel.name,
                    position = pIdx,
                    createdAt = exportModel.createdAt
                )

                val resolvedVideos = mutableListOf<VideoPlaylistItemEntity>()

                exportModel.videos.forEachIndexed { index, videoExport ->
                    var finalPath: String? = null

                    val directMatch = videoPlaylistDao.findVideoByPath(videoExport.path)
                    if (directMatch != null) {
                        finalPath = directMatch.path
                        songsFound++
                    } else {
                        val fileName = File(videoExport.path).name
                        val sizeMatch = if (videoExport.size > 0) {
                            videoPlaylistDao.findVideoByFilenameAndSize(fileName, videoExport.size)
                        } else {
                            null
                        }

                        if (sizeMatch != null) {
                            finalPath = sizeMatch.path
                            songsFixed++
                        } else if (File(videoExport.path).exists()) {
                            finalPath = videoExport.path
                            songsFound++
                        }
                    }

                    if (finalPath != null) {
                        resolvedVideos.add(VideoPlaylistItemEntity(0L, finalPath, index))
                    } else {
                        songsNotFound++
                    }
                }

                videoPlaylistDao.importPlaylistWithVideos(playlistEntity, resolvedVideos)
                playlistsImported++
            }

                container.videoMarkers.forEach { markerExport ->
                val video = resolveMarkerVideo(markerExport) ?: run {
                    songsNotFound++
                    return@forEach
                }
                val duplicate = videoMarkerDao.getMarkersForVideo(video.path)
                    .any { marker -> kotlin.math.abs(marker.positionMs - markerExport.positionMs) <= 1_000L }
                if (!duplicate) {
                    videoMarkerDao.insert(
                        VideoMarkerEntity(
                            videoPath = video.path,
                            fileHash = video.fileHash ?: markerExport.fileHash,
                            filename = video.filename,
                            fileSize = video.size.takeIf { it > 0L } ?: markerExport.fileSize,
                            durationMs = video.duration.takeIf { it > 0L } ?: markerExport.durationMs,
                            positionMs = markerExport.positionMs.coerceIn(
                                0L,
                                (video.duration.takeIf { it > 0L } ?: markerExport.durationMs).coerceAtLeast(0L)
                            ),
                            createdAt = markerExport.createdAt,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    songsFixed++
                }
                }

                ImportResult(playlistsImported, songsFound, songsFixed, songsNotFound, restoredPreferences)
            }
        } catch (error: BackupImportException) {
            Timber.e(error, "Import rejected: ${error.failure}")
            ImportResult(0, 0, 0, 0, failure = error.failure)
        } catch (error: SerializationException) {
            Timber.e(error, "Import JSON is invalid")
            ImportResult(0, 0, 0, 0, failure = ImportFailure.INVALID_FORMAT)
        } catch (error: FileNotFoundException) {
            Timber.e(error, "Import file not found")
            ImportResult(0, 0, 0, 0, failure = ImportFailure.NOT_FOUND)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Timber.e(error, "Import failed")
            ImportResult(0, 0, 0, 0, failure = ImportFailure.IO_ERROR)
        }
    }

    private fun readImportText(filePath: String): String {
        val uri = Uri.parse(filePath)
        val stream = if (uri.scheme == "file") {
            val path = uri.path ?: throw FileNotFoundException("Invalid file URI: $uri")
            val file = File(path)
            if (!file.exists()) throw FileNotFoundException(path)
            if (file.length() > Constants.Security.MAX_IMPORT_BYTES) throw BackupImportException(ImportFailure.TOO_LARGE)
            file.inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("Could not open input stream for $uri")
        }

        return stream.use { it.readUtf8Limited(Constants.Security.MAX_IMPORT_BYTES) }
    }

    private fun InputStream.readUtf8Limited(maxBytes: Long): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw BackupImportException(ImportFailure.TOO_LARGE)
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun validateImport(container: PlaylistExportContainer) {
        if (container.version !in MIN_IMPORT_VERSION..MAX_IMPORT_VERSION) {
            throw BackupImportException(ImportFailure.UNSUPPORTED_VERSION)
        }
        if (container.playlists.size + container.videoPlaylists.size > MAX_PLAYLISTS ||
            container.videoMarkers.size > MAX_MARKERS
        ) {
            throw BackupImportException(ImportFailure.LIMIT_EXCEEDED)
        }

        var mediaItems = 0L
        container.playlists.forEach { playlist ->
            requireSafeString(playlist.name, MAX_NAME_LENGTH)
            requireValid(playlist.createdAt >= 0L)
            mediaItems += playlist.songs.size
            playlist.songs.forEach { song ->
                requireSafePath(song.path)
                requireSafeString(song.title, MAX_TEXT_LENGTH)
                requireSafeString(song.artist, MAX_TEXT_LENGTH)
                song.fileHash?.let { requireSafeString(it, MAX_HASH_LENGTH) }
                requireValid(song.size >= 0L)
            }
        }
        container.videoPlaylists.forEach { playlist ->
            requireSafeString(playlist.name, MAX_NAME_LENGTH)
            requireValid(playlist.createdAt >= 0L)
            mediaItems += playlist.videos.size
            playlist.videos.forEach { video ->
                requireSafePath(video.path)
                requireSafeString(video.title, MAX_TEXT_LENGTH)
                requireValid(video.duration >= 0L && video.size >= 0L)
            }
        }
        container.videoMarkers.forEach { marker ->
            requireSafePath(marker.videoPath)
            requireSafeString(marker.filename, MAX_NAME_LENGTH)
            marker.fileHash?.let { requireSafeString(it, MAX_HASH_LENGTH) }
            requireValid(
                marker.fileSize >= 0L &&
                    marker.durationMs >= 0L &&
                    marker.positionMs in 0L..marker.durationMs.coerceAtLeast(0L) &&
                    marker.createdAt >= 0L &&
                    marker.updatedAt >= 0L
            )
        }
        container.settings?.let(::validatePreferences)
        if (mediaItems > MAX_MEDIA_ITEMS) throw BackupImportException(ImportFailure.LIMIT_EXCEEDED)
    }

    private fun validatePreferences(preferences: UserPreferences) {
        requireValid(COLOR_PATTERN.matches(preferences.primaryColor))
        requireValid(COLOR_PATTERN.matches(preferences.settingsCardBorderColor))
        requireSafeOptionalPath(preferences.musicFolderPath)
        requireSafeOptionalPath(preferences.videoFolderPath)
        requireValid(preferences.miniPlayerProgressBarHeight.isFinite() && preferences.miniPlayerProgressBarHeight in 0f..100f)
        requireValid(preferences.fullScreenPlayerProgressBarHeight.isFinite() && preferences.fullScreenPlayerProgressBarHeight in 0f..100f)
        requireValid(preferences.backgroundTintFraction.isFinite() && preferences.backgroundTintFraction in 0f..1f)
        requireValid(preferences.settingsCardBorderWidth.isFinite() && preferences.settingsCardBorderWidth in 0f..20f)
        requireValid(preferences.videoGalleryColumns in 1..10)
    }

    private fun requireSafePath(value: String) {
        requireSafeString(value, MAX_PATH_LENGTH)
        requireValid(value.startsWith('/') || value.startsWith("content://") || value.startsWith("file://"))
    }

    private fun requireSafeOptionalPath(value: String) {
        if (value.isNotBlank()) requireSafePath(value)
    }

    private fun requireValid(condition: Boolean) {
        if (!condition) throw BackupImportException(ImportFailure.INVALID_FORMAT)
    }

    private fun requireSafeString(value: String, maxLength: Int) {
        if (value.length > maxLength || value.indexOf('\u0000') >= 0) {
            throw BackupImportException(ImportFailure.LIMIT_EXCEEDED)
        }
    }

    private suspend fun resolveMarkerVideo(markerExport: VideoMarkerExportModel) =
        videoDao.getVideoByPath(markerExport.videoPath)
            ?: markerExport.fileHash?.let { videoDao.findVideoByHash(it) }
            ?: videoDao.findVideoByFilenameSizeAndDuration(
                filename = markerExport.filename,
                size = markerExport.fileSize,
                duration = markerExport.durationMs
            )

    private class BackupImportException(val failure: ImportFailure) : Exception(failure.name)

    private companion object {
        const val MIN_IMPORT_VERSION = 1
        const val MAX_IMPORT_VERSION = 4
        const val MAX_PLAYLISTS = 1_000
        const val MAX_MEDIA_ITEMS = 100_000L
        const val MAX_MARKERS = 50_000
        const val MAX_NAME_LENGTH = 255
        const val MAX_TEXT_LENGTH = 1_024
        const val MAX_PATH_LENGTH = 4_096
        const val MAX_HASH_LENGTH = 256
        val COLOR_PATTERN = Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")
    }
}
