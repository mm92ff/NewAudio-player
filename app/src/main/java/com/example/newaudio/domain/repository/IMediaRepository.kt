package com.example.newaudio.domain.repository

import androidx.media3.common.Player
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.model.Video
import kotlinx.coroutines.flow.Flow

interface IMediaRepository {

    data class PlayerError(val code: Int, val message: String)

    data class PlaybackState(
        val isPlaying: Boolean = false,
        val currentSong: Song? = null,
        val currentVideo: Video? = null,
        val currentPosition: Long = 0,
        val totalDuration: Long = 0,
        val isShuffleEnabled: Boolean = false,
        val repeatMode: Int = 0,
        val isRestoring: Boolean = true,
        val playerError: PlayerError? = null,
        val player: Player? = null
    )

    fun getPlaybackState(): Flow<PlaybackState>

    // new: explicit initialization (repo can still auto-init internally)
    suspend fun initialize()

    // new: used by "scan-if-empty" policy
    suspend fun getLibrarySongCount(): Int
    suspend fun getLibraryVideoCount(): Int

    suspend fun playPlaylist(songs: List<Song>, startIndex: Int, folderPath: String? = null)
    suspend fun playVideoPlaylist(videos: List<Video>, startIndex: Int, folderPath: String? = null)
    suspend fun resumeLastMusicSession(): Boolean
    suspend fun resumeLastVideoSession(): Boolean

    suspend fun restorePlaylist(songs: List<Song>, startIndex: Int, startPosition: Long, folderPath: String? = null)

    suspend fun ensureSongInLibraryAndGetParentPath(songPath: String): String?
    suspend fun getSongsInFolder(parentPath: String): List<Song>
    suspend fun getVideosInFolder(parentPath: String): List<Video>

    suspend fun togglePlayback()

    suspend fun toggleShuffle()

    // new: preferences/apply
    suspend fun setShuffleEnabled(enabled: Boolean)

    // REMOVED: toggleRepeatMode() no longer exists. Logic lives in the UseCase.

    suspend fun setRepeatMode(repeatMode: UserPreferences.RepeatMode)

    suspend fun skipNext()

    suspend fun skipPrevious()

    suspend fun seekTo(position: Long)

    suspend fun removeDeletedMedia(paths: List<String>)

    suspend fun clearPlayerError()

    suspend fun clearDatabase()
}
