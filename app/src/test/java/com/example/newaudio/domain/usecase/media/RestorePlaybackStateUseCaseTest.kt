package com.example.newaudio.domain.usecase.media

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.repository.ISettingsRepository
import com.example.newaudio.domain.repository.LastPlayedSong
import com.example.newaudio.domain.usecase.file.GetParentPathUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RestorePlaybackStateUseCaseTest {
    private val settingsRepository = mockk<ISettingsRepository>()
    private val mediaRepository = mockk<IMediaRepository>(relaxed = true)

    @Test
    fun `missing saved song does not restore first song from stale folder`() = runTest {
        val savedSong = song("/old/expected.mp3")
        val unrelatedSong = song("/old/other.mp3")
        coEvery { settingsRepository.getLastPlayedSong() } returns
            LastPlayedSong(savedSong, position = 42L, folderPath = "/old")
        coEvery { mediaRepository.getSongsInFolder("/old") } returns listOf(unrelatedSong)

        RestorePlaybackStateUseCase(
            settingsRepository,
            GetParentPathUseCase(),
            mediaRepository
        )()

        coVerify(exactly = 1) {
            mediaRepository.restorePlaylist(listOf(savedSong), 0, 42L, null)
        }
    }

    @Test
    fun `empty saved folder falls back to single song without stale folder context`() = runTest {
        val savedSong = song("/old/expected.mp3")
        coEvery { settingsRepository.getLastPlayedSong() } returns
            LastPlayedSong(savedSong, position = 42L, folderPath = "/old")
        coEvery { mediaRepository.getSongsInFolder("/old") } returns emptyList()

        RestorePlaybackStateUseCase(
            settingsRepository,
            GetParentPathUseCase(),
            mediaRepository
        )()

        coVerify(exactly = 1) {
            mediaRepository.restorePlaylist(listOf(savedSong), 0, 42L, null)
        }
    }

    private fun song(path: String) = Song(
        path = path,
        contentUri = path,
        title = path.substringAfterLast('/'),
        artist = "Artist",
        duration = 1_000L,
        albumArtPath = null
    )
}
