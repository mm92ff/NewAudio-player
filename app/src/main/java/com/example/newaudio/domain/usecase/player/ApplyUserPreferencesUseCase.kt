package com.example.newaudio.domain.usecase.player

import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.repository.ISettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Fetches user preferences from repository and applies them to the active media session.
 */
class ApplyUserPreferencesUseCase @Inject constructor(
    private val settingsRepository: ISettingsRepository,
    private val mediaRepository: IMediaRepository
) {
    suspend operator fun invoke() {
        val prefs = settingsRepository.userPreferences.firstOrNull() ?: return
        
        mediaRepository.setShuffleEnabled(prefs.isShuffleEnabled)
        mediaRepository.setRepeatMode(prefs.repeatMode)
    }
}
