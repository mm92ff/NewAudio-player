package com.example.newaudio.domain.usecase.player

import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IMediaRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Cycles repeat mode: NONE -> ALL -> ONE -> NONE
 */
class ToggleRepeatModeUseCase @Inject constructor(
    private val mediaRepository: IMediaRepository
) {
    suspend operator fun invoke() {
        val currentInt = mediaRepository.getPlaybackState().first().repeatMode

        val current = when (currentInt) {
            1 -> UserPreferences.RepeatMode.ONE
            2 -> UserPreferences.RepeatMode.ALL
            else -> UserPreferences.RepeatMode.NONE
        }

        val next = when (current) {
            UserPreferences.RepeatMode.NONE -> UserPreferences.RepeatMode.ALL
            UserPreferences.RepeatMode.ALL -> UserPreferences.RepeatMode.ONE
            UserPreferences.RepeatMode.ONE -> UserPreferences.RepeatMode.NONE
        }

        mediaRepository.setRepeatMode(next)
    }
}
