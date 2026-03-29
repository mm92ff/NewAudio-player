package com.example.newaudio.domain.usecase.player

import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.usecase.media.SavePlaybackStateUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SeekTrackUseCase @Inject constructor(
    private val mediaRepository: IMediaRepository,
    private val savePlaybackStateUseCase: SavePlaybackStateUseCase
) {
    suspend operator fun invoke(position: Long) {
        mediaRepository.seekTo(position)
        val currentSong = mediaRepository.getPlaybackState().first().currentSong
        savePlaybackStateUseCase(currentSong, position)
    }
}
