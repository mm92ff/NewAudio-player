package com.example.newaudio.domain.usecase.settings

import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.repository.IMediaScannerRepository
import com.example.newaudio.domain.repository.ISettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ResetDatabaseUseCase @Inject constructor(
    private val mediaRepository: IMediaRepository,
    private val settingsRepository: ISettingsRepository,
    private val mediaScannerRepository: IMediaScannerRepository
) {
    suspend operator fun invoke() {
        mediaRepository.clearDatabase()
        val musicPath = settingsRepository.getMusicFolderPath().first()
        if (musicPath.isNotEmpty()) {
            mediaScannerRepository.scanDirectory(musicPath)
        }
    }
}
