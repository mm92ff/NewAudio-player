package com.example.newaudio.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.newaudio.R
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.LogLevel
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IEqualizerRepository
import com.example.newaudio.domain.repository.IErrorRepository
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.repository.ISettingsRepository
import com.example.newaudio.domain.usecase.media.GetSongMetadataUseCase
import com.example.newaudio.domain.usecase.player.InitializePlaybackSessionUseCase
import com.example.newaudio.domain.usecase.player.SeekTrackUseCase
import com.example.newaudio.domain.usecase.player.SkipTrackUseCase
import com.example.newaudio.domain.usecase.player.TogglePlaybackUseCase
import com.example.newaudio.domain.usecase.player.ToggleRepeatModeUseCase
import com.example.newaudio.domain.usecase.player.ToggleShuffleUseCase
import com.example.newaudio.util.Constants
import com.example.newaudio.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val mediaRepository: IMediaRepository,
    private val initializePlaybackSessionUseCase: InitializePlaybackSessionUseCase,
    private val togglePlaybackUseCase: TogglePlaybackUseCase,
    private val seekTrackUseCase: SeekTrackUseCase,
    private val skipTrackUseCase: SkipTrackUseCase,
    private val toggleShuffleUseCase: ToggleShuffleUseCase,
    private val toggleRepeatModeUseCase: ToggleRepeatModeUseCase,
    private val getSongMetadataUseCase: GetSongMetadataUseCase,
    equalizerRepository: IEqualizerRepository,
    private val settingsRepository: ISettingsRepository,
    private val errorRepository: IErrorRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val _songMetadata = MutableStateFlow<ImmutableMap<String, String?>?>(null)
    private var previousRepeatMode: UserPreferences.RepeatMode = UserPreferences.RepeatMode.NONE

    private val _errorEvents = Channel<UiText>()
    val errorEvents: Flow<UiText> = _errorEvents.receiveAsFlow()

    val uiState: StateFlow<PlayerUiState> = combine(
        mediaRepository.getPlaybackState(),
        equalizerRepository.getEqualizerState(),
        settingsRepository.userPreferences,
        _songMetadata.asStateFlow()
    ) { playbackState, equalizerState, userSettings, songMetadata ->
        PlayerUiState(
            isLoading = playbackState.isRestoring,
            isPlaying = playbackState.isPlaying,
            currentSong = playbackState.currentSong,
            currentPosition = playbackState.currentPosition,
            totalDuration = playbackState.totalDuration,
            isShuffleEnabled = playbackState.isShuffleEnabled,
            repeatMode = mapRepeatMode(playbackState.repeatMode),
            equalizerState = equalizerState,
            miniPlayerProgressBarHeight = userSettings.miniPlayerProgressBarHeight,
            fullScreenPlayerProgressBarHeight = userSettings.fullScreenPlayerProgressBarHeight,
            useMarquee = userSettings.useMarquee,
            songMetadata = songMetadata
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.STATE_FLOW_SHARING_TIMEOUT_MS),
        initialValue = PlayerUiState(isLoading = true)
    )

    init {
        errorRepository.log(LogLevel.INFO, TAG, "PlayerViewModel initialized")

        viewModelScope.launch(ioDispatcher) {
            runCatching { initializePlaybackSessionUseCase() }
                .onFailure { e ->
                    Timber.tag(TAG).e(e, "Playback session initialization failed")
                    errorRepository.log(LogLevel.ERROR, TAG, "Playback session initialization failed", e)
                    _errorEvents.trySend(UiText.StringResource(R.string.unknown_error))
                }
        }

        // Observe player errors from PlaybackState and route them through _errorEvents
        viewModelScope.launch {
            mediaRepository.getPlaybackState()
                .collect { playbackState ->
                    playbackState.playerError?.let { playerError ->
                        // Convert PlayerError to UiText based on the message content
                        // Check if this is the network error message from PlayerListenerDelegate
                        val errorUiText = if (playerError.message == "Network error") {
                            UiText.StringResource(R.string.error_network)
                        } else {
                            UiText.DynamicString(playerError.message)
                        }

                        // Emit error event to UI
                        _errorEvents.trySend(errorUiText)

                        // Clear the error from PlaybackState after consuming it
                        mediaRepository.clearPlayerError()
                    }
                }
        }
    }

    fun onShowSongMetadata() = safeLaunch {
        val song = uiState.value.currentSong ?: return@safeLaunch
        val metadata = getSongMetadataUseCase(song.path)
        _songMetadata.update { metadata.toImmutableMap() }
    }

    fun onDismissSongMetadataDialog() {
        _songMetadata.update { null }
    }

    fun onPlayPlaylist(songs: List<Song>, startIndex: Int) = safeLaunch {
        errorRepository.log(LogLevel.INFO, TAG, "Playing playlist with ${songs.size} songs, starting at index $startIndex")
        mediaRepository.playPlaylist(songs, startIndex)
    }

    fun onPlayPauseToggle() = safeLaunch {
        errorRepository.log(LogLevel.INFO, TAG, "Play/Pause toggled")
        togglePlaybackUseCase()
    }

    fun onSeek(position: Float) = safeLaunch {
        val pos = position.toLong()
        errorRepository.log(LogLevel.INFO, TAG, "Seeking to $pos")
        seekTrackUseCase(pos)
    }

    fun onSkipNext() = safeLaunch {
        errorRepository.log(LogLevel.INFO, TAG, "Skipping to next song")
        skipTrackUseCase.next()
    }

    fun onSkipPrevious() = safeLaunch {
        errorRepository.log(LogLevel.INFO, TAG, "Skipping to previous song")
        skipTrackUseCase.previous()
    }

    fun onToggleShuffle() = safeLaunch {
        errorRepository.log(LogLevel.INFO, TAG, "Toggling shuffle")
        toggleShuffleUseCase()
    }

    fun onCycleRepeatMode() = safeLaunch {
        errorRepository.log(LogLevel.INFO, TAG, "Cycling repeat mode")
        toggleRepeatModeUseCase()
    }

    fun onToggleRepeatOne() = safeLaunch {
        val currentMode = uiState.value.repeatMode
        if (currentMode == UserPreferences.RepeatMode.ONE) {
            errorRepository.log(LogLevel.INFO, TAG, "Toggling repeat one OFF, reverting to $previousRepeatMode")
            mediaRepository.setRepeatMode(previousRepeatMode)
        } else {
            errorRepository.log(LogLevel.INFO, TAG, "Toggling repeat one ON")
            previousRepeatMode = currentMode
            mediaRepository.setRepeatMode(UserPreferences.RepeatMode.ONE)
        }
    }

    private fun safeLaunch(block: suspend () -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            try {
                block()
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown error in Player"
                Timber.tag(TAG).e(e, "Action failed: %s", errorMessage)
                errorRepository.log(LogLevel.ERROR, TAG, errorMessage, e)
                _errorEvents.trySend(UiText.StringResource(R.string.unknown_error))
            }
        }
    }

    private fun mapRepeatMode(exoMode: Int): UserPreferences.RepeatMode {
        return when (exoMode) {
            Player.REPEAT_MODE_ONE -> UserPreferences.RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> UserPreferences.RepeatMode.ALL
            else -> UserPreferences.RepeatMode.NONE
        }
    }
}
