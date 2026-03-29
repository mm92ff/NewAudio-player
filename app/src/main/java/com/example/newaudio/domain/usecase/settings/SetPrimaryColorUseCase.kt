package com.example.newaudio.domain.usecase.settings

import com.example.newaudio.domain.repository.ISettingsRepository
import javax.inject.Inject

/**
 * Use case for updating the primary color of the application theme.
 */
class SetPrimaryColorUseCase @Inject constructor(
    private val settingsRepository: ISettingsRepository
) {
    suspend operator fun invoke(color: String) = settingsRepository.setPrimaryColor(color)
}