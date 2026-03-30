package com.example.newaudio.data.repository

import android.content.Context
import android.net.Uri
import com.example.newaudio.data.database.PlaylistEntity
import com.example.newaudio.data.database.PlaylistSongEntity
import com.example.newaudio.data.database.dao.PlaylistDao
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IPlaylistRepository
import com.example.newaudio.domain.repository.ImportResult
import com.example.newaudio.domain.repository.PlaylistExportContainer
import com.example.newaudio.domain.repository.PlaylistExportModel
import com.example.newaudio.domain.repository.SongExportModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
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

            val container = PlaylistExportContainer(playlists = exportList, settings = userPreferences)
            val jsonString = Json.encodeToString(container)

            val uri = Uri.parse(filePath)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray())
            } ?: throw FileNotFoundException("Could not open output stream for $uri")

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
            val uri = Uri.parse(filePath)
            val jsonString = try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).readText()
                } ?: throw FileNotFoundException("Could not open input stream for $uri")
            } catch (e: FileNotFoundException) {
                val file = File(filePath)
                if (file.exists()) file.readText() else return@withContext ImportResult(0,0,0,0)
            }

            val lenientJson = Json { ignoreUnknownKeys = true }
            val container = lenientJson.decodeFromString<PlaylistExportContainer>(jsonString)
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
        } catch (e: Exception) {
            Timber.e(e, "Import failed")
        }

        ImportResult(playlistsImported, songsFound, songsFixed, songsNotFound, restoredPreferences)
    }
}