package com.example.newaudio.domain.usecase.settings

import com.example.newaudio.domain.repository.ISettingsRepository
import javax.inject.Inject

/**
 * Use case for retrieving the user's current settings.
 */
class GetUserSettingsUseCase @Inject constructor(
    private val settingsRepository: ISettingsRepository
) {
    operator fun invoke() = settingsRepository.userPreferences
}