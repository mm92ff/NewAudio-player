package com.example.newaudio.domain.repository

import androidx.media3.common.Player
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.model.Video
import kotlinx.coroutines.flow.Flow

/**
 * Application-facing media contract.
 *
 * Required playlist operations publish state only after the Media3 command
 * phase succeeds. Optional transport controls are no-ops only while the
 * playback service is temporarily unavailable; cancellation and operation
 * failures still propagate.
 */
interface IMediaRepository {

    data class PlayerError(val code: Int, val message: String)

    /**
     * Immutable UI playback state. Song and video are mutually exclusive;
     * position and duration are milliseconds. [isRestoring] is true only while
     * startup restoration is unresolved. [player] is an optional read-only
     * handle for UI integration, not ownership of the controller lifecycle.
     */
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

    /** Establishes or awaits the single shared controller connection. */
    suspend fun initialize()

    suspend fun getLibrarySongCount(): Int
    suspend fun getLibraryVideoCount(): Int

    suspend fun playPlaylist(songs: List<Song>, startIndex: Int, folderPath: String? = null)
    suspend fun playVideoPlaylist(videos: List<Video>, startIndex: Int, folderPath: String? = null)
    /** Returns true only when a stored music session was successfully applied and consumed. */
    suspend fun resumeLastMusicSession(): Boolean

    /** Returns true only when a stored video session was successfully applied and consumed. */
    suspend fun resumeLastVideoSession(): Boolean

    suspend fun restorePlaylist(songs: List<Song>, startIndex: Int, startPosition: Long, folderPath: String? = null)

    suspend fun ensureSongInLibraryAndGetParentPath(songPath: String): String?
    suspend fun getSongsInFolder(parentPath: String): List<Song>
    suspend fun getVideosInFolder(parentPath: String): List<Video>

    suspend fun togglePlayback()

    suspend fun toggleShuffle()

    suspend fun setShuffleEnabled(enabled: Boolean)

    suspend fun setRepeatMode(repeatMode: UserPreferences.RepeatMode)

    suspend fun skipNext()

    suspend fun skipPrevious()

    suspend fun seekTo(position: Long)

    /** Reconciles exact paths or descendant paths with the active playback queue. */
    suspend fun removeDeletedMedia(paths: List<String>)

    suspend fun clearPlayerError()

    suspend fun clearDatabase()
}
