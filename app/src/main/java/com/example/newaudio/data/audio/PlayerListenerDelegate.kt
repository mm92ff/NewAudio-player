package com.example.newaudio.data.audio

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.newaudio.data.media.mapping.Media3ItemMapper
import com.example.newaudio.data.media.mapping.PlaybackErrorMapper
import com.example.newaudio.data.media.playback.PlaybackQueueState
import com.example.newaudio.data.media.playback.PlaybackStateStore
import timber.log.Timber

data class PlaybackListenerCollaborators(
    val snapshotWriter: PlaybackSnapshotWriter,
    val preferenceWriter: PlaybackPreferenceWriter,
    val positionTracker: PlaybackPositionTracker,
    val errorMapper: PlaybackErrorMapper
)

/** Translates Media3 callbacks into app state and delegates side effects. */
class PlayerListenerDelegate(
    private val stateStore: PlaybackStateStore,
    private val queueState: PlaybackQueueState,
    private val itemMapper: Media3ItemMapper,
    private val player: Player,
    private val collaborators: PlaybackListenerCollaborators
) : Player.Listener {

    companion object {
        private const val TAG = "PlayerListenerDelegate"
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val queue = queueState.snapshot()
        val currentVideo = queue.videos.find { it.path == mediaItem?.mediaId }
            ?: mediaItem?.takeIf(itemMapper::isVideo)?.let(itemMapper::toVideo)
        val currentSong = if (currentVideo == null) {
            queue.songs.find { it.path == mediaItem?.mediaId }
                ?: mediaItem?.takeUnless(itemMapper::isVideo)?.let(itemMapper::toSong)
        } else {
            null
        }
        stateStore.update {
            it.copy(
                currentSong = currentSong,
                currentVideo = currentVideo
            )
        }
        saveCurrentState()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
            )) {
            updatePlaybackState()

            if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
                collaborators.preferenceWriter.requestRepeatMode(player.repeatMode)
            }
            if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                collaborators.preferenceWriter.requestShuffle(player.shuffleModeEnabled)
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) {
                saveCurrentState()
            }
        }
        if (events.containsAny(
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED
            )
        ) {
            collaborators.positionTracker.synchronize()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Timber.tag(TAG).e(error, "Player error: %s", error.message)

        val playerError = collaborators.errorMapper.map(error)
        stateStore.update { it.copy(isPlaying = false, playerError = playerError) }
    }

    private fun updatePlaybackState() {
        stateStore.update { current ->
            current.copy(
                isPlaying = player.isPlaying,
                currentPosition = player.currentPosition,
                totalDuration = if (player.duration > 0) player.duration else 0L,
                isShuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode
            )
        }
    }

    fun saveCurrentState() {
        collaborators.positionTracker.saveCurrentState()
    }
}
