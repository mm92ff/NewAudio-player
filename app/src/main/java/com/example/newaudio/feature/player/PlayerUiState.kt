package com.example.newaudio.feature.player

import androidx.compose.runtime.Stable
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IEqualizerRepository
import com.example.newaudio.util.UiText
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
    val currentPosition: Long = 0L,
    val totalDuration: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: UserPreferences.RepeatMode = UserPreferences.RepeatMode.NONE,
    val equalizerState: IEqualizerRepository.EqualizerState = IEqualizerRepository.EqualizerState(false, persistentListOf()),
    val errorRes: UiText? = null,
    val miniPlayerProgressBarHeight: Float = UserPreferences.default().miniPlayerProgressBarHeight,
    val fullScreenPlayerProgressBarHeight: Float = UserPreferences.default().fullScreenPlayerProgressBarHeight,
    val useMarquee: Boolean = false,
    val songMetadata: ImmutableMap<String, String?>? = null
)