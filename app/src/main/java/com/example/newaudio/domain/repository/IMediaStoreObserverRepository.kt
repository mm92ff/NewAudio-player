package com.example.newaudio.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * A repository that observes the Android MediaStore for any changes
 * to the audio files.
 */
interface IMediaStoreObserverRepository {
    /**
     * Emits a value every time a change in the audio files is detected.
     * The flow is debounced to avoid multiple emissions for a single event.
     */
    fun observeAudioChanges(): Flow<Unit>
}