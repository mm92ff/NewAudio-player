package com.example.newaudio.domain.usecase.settings

import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.ISettingsRepository
import javax.inject.Inject

class SetVideoDisplayModeUseCase @Inject constructor(
    private val settingsRepository: ISettingsRepository
) {
    suspend operator fun invoke(mode: UserPreferences.VideoDisplayMode) {
        settingsRepository.setVideoDisplayMode(mode)
    }
}
