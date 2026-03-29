package com.example.newaudio.domain.repository

import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface IPlaylistRepository {
    fun getAllPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String): Long
    suspend fun updatePlaylist(playlist: Playlist)

    // Single delete (kept as-is)
    suspend fun deletePlaylist(playlist: Playlist)
    // ✅ NEW: Batch delete for multi-select
    suspend fun deletePlaylists(playlistIds: List<Long>)

    suspend fun duplicatePlaylist(playlist: Playlist, newName: String)
    suspend fun updatePlaylistsOrder(playlists: List<Playlist>)

    // Single add
    suspend fun addSongToPlaylist(playlistId: Long, song: Song)
    // ✅ NEW: Batch add (for FileBrowser multi-select -> Add to Playlist)
    suspend fun addSongsToPlaylist(playlistId: Long, songs: List<Song>)

    // Single remove
    suspend fun removeSongFromPlaylist(playlistId: Long, songPath: String)
    // ✅ NEW: Batch remove (for playlist multi-select)
    suspend fun removeSongsFromPlaylist(playlistId: Long, songPaths: List<String>)

    suspend fun updatePlaylistSongsOrder(playlistId: Long, songs: List<Song>)

    // Optimized song swap
    suspend fun swapSongsInPlaylist(playlistId: Long, songPath1: String, position1: Int, songPath2: String, position2: Int)

    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>>

    // For export/import
    suspend fun exportPlaylists(filePath: String, userPreferences: UserPreferences): Boolean
    suspend fun importPlaylists(filePath: String): ImportResult
}

data class ImportResult(
    val playlistsImported: Int,
    val songsFound: Int,
    val songsFixed: Int,
    val songsNotFound: Int,
    val restoredPreferences: UserPreferences? = null
)