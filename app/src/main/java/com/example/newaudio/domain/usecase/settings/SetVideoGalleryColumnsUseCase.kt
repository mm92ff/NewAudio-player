package com.example.newaudio.domain.usecase.settings

import com.example.newaudio.domain.repository.ISettingsRepository
import javax.inject.Inject

class SetVideoGalleryColumnsUseCase @Inject constructor(
    private val settingsRepository: ISettingsRepository
) {
    suspend operator fun invoke(columns: Int) {
        settingsRepository.setVideoGalleryColumns(columns)
    }
}
