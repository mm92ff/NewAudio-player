package com.example.newaudio.feature.player

import androidx.media3.common.Player
import androidx.compose.runtime.Stable
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.model.VideoMarker
import com.example.newaudio.domain.repository.IEqualizerRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf

/**
 * Represents the UI state for the entire player feature.
 */
@Stable
data class PlayerUiState(
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val currentSong: Song? = null,
    val currentVideo: Video? = null,
    val currentPosition: Long = 0L,
    val totalDuration: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: UserPreferences.RepeatMode = UserPreferences.RepeatMode.NONE,
    val equalizerState: IEqualizerRepository.EqualizerState = IEqualizerRepository.EqualizerState(false, persistentListOf()),
    val miniPlayerProgressBarHeight: Float = UserPreferences.default().miniPlayerProgressBarHeight,
    val fullScreenPlayerProgressBarHeight: Float = UserPreferences.default().fullScreenPlayerProgressBarHeight,
    val useMarquee: Boolean = false,
    val videoMarkersEnabled: Boolean = false,
    val videoMarkers: ImmutableList<VideoMarker> = persistentListOf(),
    val songMetadata: ImmutableMap<String, String?>? = null,
    val player: Player? = null
)
