package com.example.newaudio.domain.usecase.player

import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.usecase.media.RestorePlaybackStateUseCase
import com.example.newaudio.domain.usecase.media.ScanLibraryIfEmptyUseCase
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * One-shot orchestration:
 * 1) init controller
 * 2) apply prefs
 * 3) scan if DB empty
 * 4) restore last playback if nothing currently loaded
 */
class InitializePlaybackSessionUseCase @Inject constructor(
    private val mediaRepository: IMediaRepository,
    private val applyUserPreferencesUseCase: ApplyUserPreferencesUseCase,
    private val scanLibraryIfEmptyUseCase: ScanLibraryIfEmptyUseCase,
    private val restorePlaybackStateUseCase: RestorePlaybackStateUseCase
) {
    suspend operator fun invoke() {
        mediaRepository.initialize()

        runCatching { applyUserPreferencesUseCase() }
            .onFailure { Timber.tag(TAG).w(it, "Applying user preferences failed") }

        runCatching { scanLibraryIfEmptyUseCase() }
            .onFailure { Timber.tag(TAG).w(it, "Initial scan failed") }

        val state = mediaRepository.getPlaybackState().first()
        if (state.currentSong == null) {
            runCatching { restorePlaybackStateUseCase() }
                .onFailure { Timber.tag(TAG).w(it, "Restore playback failed") }
        }
    }

    private companion object {
        private const val TAG = "PlaybackInit"
    }
}
