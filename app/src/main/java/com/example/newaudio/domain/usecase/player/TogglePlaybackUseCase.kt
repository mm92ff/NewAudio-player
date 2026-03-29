package com.example.newaudio.domain.usecase.player

import com.example.newaudio.domain.repository.IMediaRepository
import javax.inject.Inject

class TogglePlaybackUseCase @Inject constructor(
    private val mediaRepository: IMediaRepository
) {
    // FIX: added suspend
    suspend operator fun invoke() {
        mediaRepository.togglePlayback()
    }
}