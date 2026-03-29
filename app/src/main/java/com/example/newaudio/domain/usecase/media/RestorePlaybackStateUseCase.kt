package com.example.newaudio.domain.usecase.media

import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.repository.ISettingsRepository
import com.example.newaudio.domain.usecase.file.GetParentPathUseCase
import timber.log.Timber
import javax.inject.Inject

class RestorePlaybackStateUseCase @Inject constructor(
    private val settingsRepository: ISettingsRepository,
    private val getParentPathUseCase: GetParentPathUseCase,
    private val mediaRepository: IMediaRepository
) {
    suspend operator fun invoke() {
        val lastState = settingsRepository.getLastPlayedSong() ?: return

        // Use saved folder path; fall back to deriving it from the song path (legacy saves)
        val folderPath = lastState.folderPath ?: getParentPathUseCase(lastState.song.path)

        var playlist = listOf(lastState.song)
        var startIndex = 0

        if (folderPath != null) {
            try {
                // Load directly from Room DB — no StateFlow issue (no empty initialValue)
                val songsInFolder = mediaRepository.getSongsInFolder(folderPath)

                Timber.tag(TAG).d(
                    "Restoring playlist from folder: $folderPath (songs=${songsInFolder.size})"
                )

                if (songsInFolder.isNotEmpty()) {
                    playlist = songsInFolder
                    startIndex = songsInFolder
                        .indexOfFirst { it.path == lastState.song.path }
                        .coerceAtLeast(0)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Folder not accessible, falling back to single-song playlist")
            }
        }

        mediaRepository.restorePlaylist(playlist, startIndex, lastState.position, folderPath)
    }

    private companion object {
        private const val TAG = "RestorePlayback"
    }
}
