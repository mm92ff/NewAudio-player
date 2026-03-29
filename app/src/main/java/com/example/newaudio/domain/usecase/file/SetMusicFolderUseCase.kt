package com.example.newaudio.domain.usecase.file

import com.example.newaudio.domain.repository.IMediaScannerRepository
import com.example.newaudio.domain.repository.ISettingsRepository
import javax.inject.Inject

class SetMusicFolderUseCase @Inject constructor(
    private val settingsRepository: ISettingsRepository,
    private val mediaScannerRepository: IMediaScannerRepository
) {
    suspend operator fun invoke(folderPath: String) {
        settingsRepository.setMusicFolderPath(folderPath)
        if (folderPath.isNotEmpty()) {
            mediaScannerRepository.scanDirectory(folderPath)
        }
    }
}
