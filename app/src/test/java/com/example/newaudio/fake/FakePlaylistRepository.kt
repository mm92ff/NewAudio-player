package com.example.newaudio.fake

import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IPlaylistRepository
import com.example.newaudio.domain.repository.ImportResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

class FakePlaylistRepository : IPlaylistRepository {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    var exportCalled = false
    var exportReturnValue = true
    var exportedPreferences: UserPreferences? = null
    var importReturnPreferences: UserPreferences? = null
    var importShouldThrow = false
    var exportShouldThrow = false

    override fun getAllPlaylists(): Flow<List<Playlist>> = _playlists.asStateFlow()

    override suspend fun createPlaylist(name: String): Long {
        val id = (_playlists.value.maxOfOrNull { it.id } ?: 0L) + 1
        _playlists.value = _playlists.value + Playlist(id = id, name = name, position = _playlists.value.size)
        return id
    }

    override suspend fun updatePlaylist(playlist: Playlist) {
        _playlists.value = _playlists.value.map { if (it.id == playlist.id) playlist else it }
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        _playlists.value = _playlists.value.filter { it.id != playlist.id }
    }

    override suspend fun deletePlaylists(playlistIds: List<Long>) {
        _playlists.value = _playlists.value.filter { it.id !in playlistIds }
    }

    override suspend fun duplicatePlaylist(playlist: Playlist, newName: String) {}

    override suspend fun updatePlaylistsOrder(playlists: List<Playlist>) {
        _playlists.value = playlists
    }

    override suspend fun addSongToPlaylist(playlistId: Long, song: Song) {}

    override suspend fun addSongsToPlaylist(playlistId: Long, songs: List<Song>) {}

    override suspend fun removeSongFromPlaylist(playlistId: Long, songPath: String) {}

    override suspend fun removeSongsFromPlaylist(playlistId: Long, songPaths: List<String>) {}

    override suspend fun updatePlaylistSongsOrder(playlistId: Long, songs: List<Song>) {}

    override suspend fun swapSongsInPlaylist(playlistId: Long, songPath1: String, position1: Int, songPath2: String, position2: Int) {}

    override fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> = flowOf(emptyList())

    override suspend fun exportPlaylists(filePath: String, userPreferences: UserPreferences): Boolean {
        if (exportShouldThrow) throw RuntimeException("Export failed")
        exportCalled = true
        exportedPreferences = userPreferences
        return exportReturnValue
    }

    override suspend fun importPlaylists(filePath: String): ImportResult {
        if (importShouldThrow) throw RuntimeException("Import failed")
        return ImportResult(0, 0, 0, 0, importReturnPreferences)
    }
}
