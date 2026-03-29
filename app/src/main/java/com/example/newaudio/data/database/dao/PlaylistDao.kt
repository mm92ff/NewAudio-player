package com.example.newaudio.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.newaudio.data.database.PlaylistEntity
import com.example.newaudio.data.database.PlaylistSongEntity
import com.example.newaudio.data.database.SongEntity
import com.example.newaudio.domain.model.Song
import kotlinx.coroutines.flow.Flow

data class PlaylistSongResult(
    val path: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val albumArtPath: String?,
    val position: Int
) {
    fun toDomainModel(): Song {
        return Song(
            path = path,
            contentUri = contentUri,
            title = title,
            artist = artist,
            duration = duration,
            albumArtPath = albumArtPath
        )
    }
}

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY position ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("""
        SELECT s.path, s.contentUri, s.title, s.artist, s.duration, s.albumArtPath, ps.position
        FROM songs s
        INNER JOIN playlist_songs ps ON s.path = ps.songPath
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
    """)
    fun getSongsInPlaylist(playlistId: Long): Flow<List<PlaylistSongResult>>

    @Query("SELECT s.path, s.contentUri, s.title, s.artist, s.duration, s.albumArtPath, ps.position FROM songs s INNER JOIN playlist_songs ps ON s.path = ps.songPath WHERE ps.playlistId = :playlistId")
    suspend fun getSongsInPlaylistSync(playlistId: Long): List<PlaylistSongResult>

    @Query("SELECT MAX(position) FROM playlists")
    suspend fun getMaxPlaylistPosition(): Int?

    @Query("SELECT MAX(position) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getMaxSongPosition(playlistId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSong(playlistSong: PlaylistSongEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSongs(playlistSongs: List<PlaylistSongEntity>)

    @androidx.room.Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @androidx.room.Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    // --- NEW: Batch delete for playlists (performance fix) ---
    @Query("DELETE FROM playlists WHERE id IN (:playlistIds)")
    suspend fun deletePlaylists(playlistIds: List<Long>)

    @androidx.room.Update
    suspend fun updatePlaylistsOrder(playlists: List<PlaylistEntity>)

    // Single delete (kept for context menu)
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songPath = :songPath")
    suspend fun removeSongFromPlaylist(playlistId: Long, songPath: String)

    // --- NEW: Batch delete for songs (performance fix) ---
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songPath IN (:songPaths)")
    suspend fun removeSongsFromPlaylist(playlistId: Long, songPaths: List<String>)

    @androidx.room.Update
    suspend fun updatePlaylistSongsOrder(playlistSongs: List<PlaylistSongEntity>)

    @Transaction
    suspend fun duplicatePlaylist(sourcePlaylistId: Long, newName: String) {
        val maxPos = getMaxPlaylistPosition() ?: -1
        val newPlaylistId = insertPlaylist(PlaylistEntity(name = newName, position = maxPos + 1))

        val songs = getSongsInPlaylistSync(sourcePlaylistId)
        val entities = songs.map {
            PlaylistSongEntity(
                playlistId = newPlaylistId,
                songPath = it.path,
                position = it.position
            )
        }
        insertPlaylistSongs(entities)
    }

    // --- Import Helper ---

    @Query("SELECT * FROM songs WHERE path = :path LIMIT 1")
    suspend fun findSongByPath(path: String): SongEntity?

    @Query("SELECT * FROM songs WHERE fileHash = :hash LIMIT 1")
    suspend fun findSongByHash(hash: String): SongEntity?

    @Query("SELECT * FROM songs WHERE filename = :name AND size = :size LIMIT 1")
    suspend fun findSongByFilenameAndSize(name: String, size: Long): SongEntity?
}