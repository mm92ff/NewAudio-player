package com.example.newaudio.data.media.playback

import com.example.newaudio.domain.model.PlaybackSessionSnapshot
import com.example.newaudio.domain.model.SessionStartPosition
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.model.resolveMusicSessionStart
import com.example.newaudio.domain.model.resolveVideoSessionStart
import com.example.newaudio.domain.repository.IMediaRepository
import javax.inject.Inject
import javax.inject.Singleton

data class ControllerPlaybackSnapshot(
    val currentIndex: Int,
    val currentPosition: Long,
    val playWhenReady: Boolean
)

/**
 * Captures the queue being left during an audio/video switch and exposes
 * stored sessions with consume-on-success semantics.
 *
 * Peeking and resolving never remove a session. A caller must consume the
 * exact previously peeked instance after all player commands have succeeded.
 */
@Singleton
class PlaybackSessionCoordinator @Inject constructor(
    private val queueState: PlaybackQueueState
) {
    private var musicSession: PlaybackSessionSnapshot.MusicSession? = null
    private var videoSession: PlaybackSessionSnapshot.VideoSession? = null

    @Synchronized
    fun captureBeforeMusicPlayback(
        state: IMediaRepository.PlaybackState,
        player: ControllerPlaybackSnapshot
    ) {
        if (state.currentVideo == null) return
        val queue = queueState.snapshot()
        val index = resolveCurrentIndex(
            player.currentIndex,
            queue.videos.size,
            state.currentVideo.path
        ) { queue.videos[it].path } ?: return
        val video = queue.videos.getOrNull(index) ?: return
        videoSession = PlaybackSessionSnapshot.VideoSession(
            videos = queue.videos,
            currentIndex = index,
            currentPath = video.path,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            folderPath = queue.folderPath,
            wasPlaying = player.playWhenReady
        )
    }

    @Synchronized
    fun captureBeforeVideoPlayback(
        state: IMediaRepository.PlaybackState,
        player: ControllerPlaybackSnapshot
    ) {
        if (state.currentSong == null) return
        val queue = queueState.snapshot()
        val index = resolveCurrentIndex(
            player.currentIndex,
            queue.songs.size,
            state.currentSong.path
        ) { queue.songs[it].path } ?: return
        val song = queue.songs.getOrNull(index) ?: return
        musicSession = PlaybackSessionSnapshot.MusicSession(
            songs = queue.songs,
            currentIndex = index,
            currentPath = song.path,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            folderPath = queue.folderPath,
            wasPlaying = player.playWhenReady
        )
    }

    @Synchronized
    fun previewMusicStart(
        songs: List<Song>,
        requestedIndex: Int,
        folderPath: String?
    ): SessionStartPosition {
        return musicSession.resolveMusicSessionStart(songs, requestedIndex, folderPath)
    }

    @Synchronized
    fun previewVideoStart(
        videos: List<Video>,
        requestedIndex: Int,
        folderPath: String?
    ): SessionStartPosition {
        return videoSession.resolveVideoSessionStart(videos, requestedIndex, folderPath)
    }

    @Synchronized
    fun peekMusicSession(): PlaybackSessionSnapshot.MusicSession? = musicSession

    @Synchronized
    fun peekVideoSession(): PlaybackSessionSnapshot.VideoSession? = videoSession

    @Synchronized
    fun consumeMusicSession(): PlaybackSessionSnapshot.MusicSession? {
        return musicSession.also { musicSession = null }
    }

    @Synchronized
    fun consumeVideoSession(): PlaybackSessionSnapshot.VideoSession? {
        return videoSession.also { videoSession = null }
    }

    @Synchronized
    fun consumeMusicSession(expected: PlaybackSessionSnapshot.MusicSession?): Boolean {
        if (expected == null || musicSession !== expected) return false
        musicSession = null
        return true
    }

    @Synchronized
    fun consumeVideoSession(expected: PlaybackSessionSnapshot.VideoSession?): Boolean {
        if (expected == null || videoSession !== expected) return false
        videoSession = null
        return true
    }

    @Synchronized
    fun replaceMusicSession(session: PlaybackSessionSnapshot.MusicSession?) {
        musicSession = session
    }

    @Synchronized
    fun replaceVideoSession(session: PlaybackSessionSnapshot.VideoSession?) {
        videoSession = session
    }

    private fun resolveCurrentIndex(
        playerIndex: Int,
        playlistSize: Int,
        currentPath: String?,
        pathAt: (Int) -> String
    ): Int? {
        if (playlistSize <= 0) return null
        if (playerIndex in 0 until playlistSize) return playerIndex
        return currentPath?.let { path ->
            (0 until playlistSize).firstOrNull { pathAt(it) == path }
        }
    }
}
