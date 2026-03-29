package com.example.newaudio.domain.usecase.player

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.repository.IMediaRepository
import javax.inject.Inject

/**
 * Orchestrates the "play a song" user intent.
 *
 * Policy:
 * - If the song is inside a folder, play the whole folder as a playlist starting at the clicked song.
 * - If we can't resolve a folder context, fall back to playing only the selected song.
 */
class PlaySongUseCase @Inject constructor(
    private val mediaRepository: IMediaRepository
) {
    suspend operator fun invoke(song: Song) {
        val parentPath = mediaRepository.ensureSongInLibraryAndGetParentPath(song.path)

        if (parentPath == null) {
            mediaRepository.playPlaylist(listOf(song), 0)
            return
        }

        val songsInFolder = mediaRepository.getSongsInFolder(parentPath)

        val playlist = when {
            songsInFolder.isEmpty() -> listOf(song)
            songsInFolder.any { it.path == song.path } -> songsInFolder
            else -> songsInFolder + song
        }

        val startIndex = playlist.indexOfFirst { it.path == song.path }
            .let { if (it >= 0) it else 0 }

        mediaRepository.playPlaylist(playlist, startIndex, parentPath)
    }
}
