package com.example.newaudio.domain.usecase.settings

import com.example.newaudio.domain.repository.ISettingsRepository
import javax.inject.Inject

class SetSettingsCardBorderWidthUseCase @Inject constructor(
    private val settingsRepository: ISettingsRepository
) {
    suspend operator fun invoke(widthDp: Float) {
        settingsRepository.setSettingsCardBorderWidth(widthDp)
    }
}
