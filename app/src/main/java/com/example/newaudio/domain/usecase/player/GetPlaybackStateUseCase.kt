package com.example.newaudio.domain.usecase.player

import com.example.newaudio.domain.repository.IMediaRepository
import javax.inject.Inject

/**
 * Use case for observing the current playback state.
 */
class GetPlaybackStateUseCase @Inject constructor(
    private val mediaRepository: IMediaRepository
) {
    operator fun invoke() = mediaRepository.getPlaybackState()
}