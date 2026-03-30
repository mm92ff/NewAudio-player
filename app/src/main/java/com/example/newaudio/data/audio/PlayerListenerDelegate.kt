package com.example.newaudio.data.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.newaudio.R
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.repository.ISettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs

class PlayerListenerDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackState: MutableStateFlow<IMediaRepository.PlaybackState>,
    private val settingsRepository: ISettingsRepository, // Direktes Repo statt UseCase
    private val player: Player,
    private val coroutineScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : Player.Listener {

    companion object {
        private const val TAG = "PlayerListenerDelegate"
        private const val POSITION_UPDATE_INTERVAL_MS = 1000L
        private const val AUTO_SAVE_INTERVAL_MS = 5000L
    }

    private var positionUpdateJob: Job? = null

    // Volatile for thread visibility in case Main/IO run on different cores
    @Volatile
    private var lastSaveTime = 0L

    @Volatile
    private var lastSavedPosition = 0L

    var currentPlaylist: List<Song> = emptyList()
    var currentFolderPath: String? = null

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val currentSong = currentPlaylist.find { it.path == mediaItem?.mediaId } ?: mediaItem?.toSong()
        playbackState.update { it.copy(currentSong = currentSong, playerError = null) }
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
            handlePositionTracking()

            if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
                persistRepeatMode(player.repeatMode)
            }
            if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                persistShuffleMode(player.shuffleModeEnabled)
            }
            // When pausing, save immediately
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) {
                saveCurrentState()
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Timber.tag(TAG).e(error, "Player error: %s", error.message)

        // Determine if this is a network-related error
        val errorMessage = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                context.getString(R.string.error_network)
            }
            else -> error.message ?: "Unknown error"
        }

        val playerError = IMediaRepository.PlayerError(error.errorCode, errorMessage)
        playbackState.update { it.copy(isPlaying = false, playerError = playerError) }
    }

    private fun updatePlaybackState() {
        playbackState.update { current ->
            current.copy(
                isPlaying = player.isPlaying,
                currentPosition = player.currentPosition,
                totalDuration = if (player.duration > 0) player.duration else 0L,
                isShuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                playerError = if (player.playbackState != Player.STATE_IDLE) null else current.playerError
            )
        }
    }

    private fun handlePositionTracking() {
        positionUpdateJob?.cancel()
        if (player.isPlaying) {
            positionUpdateJob = coroutineScope.launch {
                while (isActive) {
                    val currentPos = player.currentPosition
                    playbackState.update { it.copy(currentPosition = currentPos) }

                    val now = System.currentTimeMillis()
                    if (now - lastSaveTime > AUTO_SAVE_INTERVAL_MS &&
                        abs(currentPos - lastSavedPosition) > 3000L) {
                        saveCurrentState()
                    }
                    delay(POSITION_UPDATE_INTERVAL_MS)
                }
            }
        }
    }

    private fun persistRepeatMode(repeatMode: Int) {
        coroutineScope.launch(ioDispatcher) {
            val mode = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> UserPreferences.RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> UserPreferences.RepeatMode.ALL
                else -> UserPreferences.RepeatMode.NONE
            }
            settingsRepository.setRepeatMode(mode)
        }
    }

    private fun persistShuffleMode(shuffleEnabled: Boolean) {
        coroutineScope.launch(ioDispatcher) {
            settingsRepository.setShuffleEnabled(shuffleEnabled)
        }
    }

    fun saveCurrentState() {
        val currentState = playbackState.value
        val song = currentState.currentSong ?: return

        lastSaveTime = System.currentTimeMillis()
        lastSavedPosition = currentState.currentPosition

        coroutineScope.launch(ioDispatcher) {
            settingsRepository.saveLastPlayedSong(song, currentState.currentPosition, currentFolderPath)
        }
    }

    private fun MediaItem.toSong(): Song = Song(
        path = this.mediaId,
        // ✅ NEW: Try to get the URI from the player configuration, fall back to ID
        contentUri = this.localConfiguration?.uri?.toString() ?: this.mediaId,
        title = this.mediaMetadata.title?.toString() ?: "Unknown Title",
        artist = this.mediaMetadata.artist?.toString() ?: "Unknown Artist",
        duration = 0,
        albumArtPath = null
    )
}