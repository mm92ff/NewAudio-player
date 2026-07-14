package com.example.newaudio.data.media.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.newaudio.domain.model.PlaybackSessionSnapshot
import com.example.newaudio.domain.repository.IMediaRepository
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

internal data class PlaybackTransitionSnapshot(
    val mediaItems: List<MediaItem>,
    val currentIndex: Int,
    val currentPosition: Long,
    val playWhenReady: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
    val queue: PlaybackQueueState.Snapshot,
    val playbackState: IMediaRepository.PlaybackState,
    val musicSession: PlaybackSessionSnapshot.MusicSession?,
    val videoSession: PlaybackSessionSnapshot.VideoSession?
)

/**
 * Captures and best-effort restores every app-side part of a playback
 * transition. Media3 rollback runs first because it may synchronously emit
 * listener callbacks; queue and app state are restored after those callbacks.
 */
@Singleton
class PlaybackTransitionCoordinator @Inject constructor(
    private val queueState: PlaybackQueueState,
    private val stateStore: PlaybackStateStore,
    private val sessionCoordinator: PlaybackSessionCoordinator
) {
    private companion object {
        private const val TAG = "PlaybackTransition"
    }

    internal fun capture(player: Player): PlaybackTransitionSnapshot {
        val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        return PlaybackTransitionSnapshot(
            mediaItems = items,
            currentIndex = player.currentMediaItemIndex.takeIf { it in items.indices } ?: 0,
            currentPosition = player.currentPosition.coerceAtLeast(0L),
            playWhenReady = player.playWhenReady,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queue = queueState.snapshot(),
            playbackState = stateStore.value,
            musicSession = sessionCoordinator.peekMusicSession(),
            videoSession = sessionCoordinator.peekVideoSession()
        )
    }

    internal fun rollback(
        player: Player,
        snapshot: PlaybackTransitionSnapshot,
        primaryError: Throwable
    ) {
        val rollbackError = runCatching {
            player.shuffleModeEnabled = snapshot.shuffleEnabled
            player.repeatMode = snapshot.repeatMode
            if (snapshot.mediaItems.isEmpty()) {
                player.clearMediaItems()
            } else {
                player.setMediaItems(
                    snapshot.mediaItems,
                    snapshot.currentIndex,
                    snapshot.currentPosition
                )
                player.setPlayWhenReady(snapshot.playWhenReady)
                player.prepare()
                if (snapshot.playWhenReady) player.play() else player.pause()
            }
        }.exceptionOrNull()

        sessionCoordinator.replaceMusicSession(snapshot.musicSession)
        sessionCoordinator.replaceVideoSession(snapshot.videoSession)
        queueState.restore(snapshot.queue)
        stateStore.restore(snapshot.playbackState)

        if (rollbackError != null) {
            primaryError.addSuppressed(rollbackError)
            Timber.tag(TAG).e(rollbackError, "Playback transition rollback failed")
        }
    }
}
