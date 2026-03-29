package com.example.newaudio.domain.usecase.player

import com.example.newaudio.domain.repository.IMediaRepository
import javax.inject.Inject

class SkipTrackUseCase @Inject constructor(
    private val mediaRepository: IMediaRepository
) {
    // FIX: added suspend
    suspend fun next() {
        mediaRepository.skipNext()
    }

    // FIX: added suspend
    suspend fun previous() {
        mediaRepository.skipPrevious()
    }
}