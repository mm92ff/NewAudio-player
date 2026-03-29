package com.example.newaudio.domain.usecase.settings

import com.example.newaudio.domain.repository.ISettingsRepository
import javax.inject.Inject

class SetTransparentListItemsUseCase @Inject constructor(
    private val settingsRepository: ISettingsRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        settingsRepository.setTransparentListItems(enabled)
    }
}
