package com.example.newaudio.data.media.playback

import com.example.newaudio.domain.repository.IMediaRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Single owner of the mutable application-side playback state. */
@Singleton
class PlaybackStateStore @Inject constructor() {
    private val mutableState = MutableStateFlow(IMediaRepository.PlaybackState())

    val state: StateFlow<IMediaRepository.PlaybackState> = mutableState.asStateFlow()
    val value: IMediaRepository.PlaybackState
        get() = mutableState.value

    fun update(transform: (IMediaRepository.PlaybackState) -> IMediaRepository.PlaybackState) {
        mutableState.update(transform)
    }

    fun restore(state: IMediaRepository.PlaybackState) {
        mutableState.value = state
    }

    fun clearPlayerError() {
        update { it.copy(playerError = null) }
    }
}
