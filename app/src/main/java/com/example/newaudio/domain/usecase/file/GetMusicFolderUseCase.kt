package com.example.newaudio.domain.usecase.file

import com.example.newaudio.domain.repository.ISettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMusicFolderUseCase @Inject constructor(
    private val settingsRepository: ISettingsRepository
) {
    operator fun invoke(): Flow<String> {
        return settingsRepository.getMusicFolderPath()
    }
}