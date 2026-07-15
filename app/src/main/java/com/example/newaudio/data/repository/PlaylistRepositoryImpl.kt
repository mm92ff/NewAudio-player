package com.example.newaudio.data.repository

import com.example.newaudio.data.database.PlaylistEntity
import com.example.newaudio.data.database.PlaylistSongEntity
import com.example.newaudio.data.database.dao.PlaylistDao
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.repository.IPlaylistRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IPlaylistRepository {

    override fun getAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.getAllPlaylists()
            .map { entities ->
                entities.map { entity ->
                    Playlist(entity.id, entity.name, entity.position, entity.createdAt)
                }
            }
            .flowOn(ioDispatcher)

    override suspend fun createPlaylist(name: String): Long = withContext(ioDispatcher) {
        val maxPosition = playlistDao.getMaxPlaylistPosition() ?: -1
        playlistDao.insertPlaylist(PlaylistEntity(name = name, position = maxPosition + 1))
    }

    override suspend fun updatePlaylist(playlist: Playlist) = withContext(ioDispatcher) {
        playlistDao.updatePlaylist(playlist.toEntity())
    }

    override suspend fun deletePlaylist(playlist: Playlist) = withContext(ioDispatcher) {
        playlistDao.deletePlaylist(playlist.toEntity())
    }

    override suspend fun deletePlaylists(playlistIds: List<Long>) = withContext(ioDispatcher) {
        playlistDao.deletePlaylists(playlistIds)
    }

    override suspend fun duplicatePlaylist(playlist: Playlist, newName: String) =
        withContext(ioDispatcher) {
            playlistDao.duplicatePlaylist(playlist.id, newName)
        }

    override suspend fun updatePlaylistsOrder(playlists: List<Playlist>) =
        withContext(ioDispatcher) {
            playlistDao.updatePlaylistsOrder(playlists.map { it.toEntity() })
        }

    override suspend fun addSongToPlaylist(playlistId: Long, song: Song) =
        withContext(ioDispatcher) {
            val maxPosition = playlistDao.getMaxSongPosition(playlistId) ?: -1
            playlistDao.insertPlaylistSong(
                PlaylistSongEntity(
                    playlistId = playlistId,
                    songPath = song.path,
                    position = maxPosition + 1
                )
            )
        }

    override suspend fun addSongsToPlaylist(playlistId: Long, songs: List<Song>) =
        withContext(ioDispatcher) {
            val maxPosition = playlistDao.getMaxSongPosition(playlistId) ?: -1
            val entities = songs.mapIndexed { index, song ->
                PlaylistSongEntity(
                    playlistId = playlistId,
                    songPath = song.path,
                    position = maxPosition + 1 + index
                )
            }
            playlistDao.insertPlaylistSongs(entities)
        }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songPath: String) =
        withContext(ioDispatcher) {
            playlistDao.removeSongFromPlaylist(playlistId, songPath)
        }

    override suspend fun removeSongsFromPlaylist(playlistId: Long, songPaths: List<String>) =
        withContext(ioDispatcher) {
            playlistDao.removeSongsFromPlaylist(playlistId, songPaths)
        }

    override suspend fun updatePlaylistSongsOrder(playlistId: Long, songs: List<Song>) =
        withContext(ioDispatcher) {
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
        playlistDao.updatePlaylistSongsOrder(
            listOf(
                PlaylistSongEntity(playlistId, songPath1, position1),
                PlaylistSongEntity(playlistId, songPath2, position2)
            )
        )
    }

    override fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> =
        playlistDao.getSongsInPlaylist(playlistId)
            .map { results -> results.map { it.toDomainModel() } }
            .flowOn(ioDispatcher)

    private fun Playlist.toEntity() = PlaylistEntity(id, name, position, createdAt)
}
