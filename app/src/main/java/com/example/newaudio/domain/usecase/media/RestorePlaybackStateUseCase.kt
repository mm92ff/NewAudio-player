package com.example.newaudio.domain.usecase.media

import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.repository.ISettingsRepository
import com.example.newaudio.domain.usecase.file.GetParentPathUseCase
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class RestorePlaybackStateUseCase @Inject constructor(
    private val settingsRepository: ISettingsRepository,
    private val getParentPathUseCase: GetParentPathUseCase,
    private val mediaRepository: IMediaRepository
) {
    suspend operator fun invoke() {
        val lastState = settingsRepository.getLastPlayedSong() ?: return

        // Use saved folder path; fall back to deriving it from the song path (legacy saves)
        var folderPath = lastState.folderPath ?: getParentPathUseCase(lastState.song.path)

        var playlist = listOf(lastState.song)
        var startIndex = 0

        if (folderPath != null) {
            try {
                // Load directly from Room DB — no StateFlow issue (no empty initialValue)
                val songsInFolder = mediaRepository.getSongsInFolder(folderPath)

                Timber.tag(TAG).d(
                    "Restoring playlist from folder: $folderPath (songs=${songsInFolder.size})"
                )

                val matchingIndex = songsInFolder.indexOfFirst { it.path == lastState.song.path }
                if (matchingIndex >= 0) {
                    playlist = songsInFolder
                    startIndex = matchingIndex
                } else {
                    folderPath = null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.tag(TAG).w(e, "Folder not accessible, falling back to single-song playlist")
                folderPath = null
            }
        }

        mediaRepository.restorePlaylist(playlist, startIndex, lastState.position, folderPath)
    }

    private companion object {
        private const val TAG = "RestorePlayback"
    }
}
