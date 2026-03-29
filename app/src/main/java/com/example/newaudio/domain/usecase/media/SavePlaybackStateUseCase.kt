package com.example.newaudio.domain.usecase.media

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.repository.ISettingsRepository
import javax.inject.Inject

class SavePlaybackStateUseCase @Inject constructor(
    private val settingsRepository: ISettingsRepository
) {
    suspend operator fun invoke(currentSong: Song?, currentPosition: Long) {
        if (currentSong != null) {
            settingsRepository.saveLastPlayedSong(currentSong, currentPosition)
        }
    }
}
