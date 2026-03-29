package com.example.newaudio.domain.usecase.settings

import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.ISettingsRepository
import javax.inject.Inject

/**
 * Use case for updating the application theme.
 */
class SetThemeUseCase @Inject constructor(
    private val settingsRepository: ISettingsRepository
) {
    suspend operator fun invoke(theme: UserPreferences.Theme) = settingsRepository.setTheme(theme)
}