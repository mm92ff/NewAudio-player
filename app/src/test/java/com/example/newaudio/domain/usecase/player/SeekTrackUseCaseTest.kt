package com.example.newaudio.domain.usecase.player

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.usecase.media.SavePlaybackStateUseCase
import com.example.newaudio.fake.FakeMediaRepository
import com.example.newaudio.fake.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SeekTrackUseCaseTest {

    private val mediaRepo = FakeMediaRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val saveState = SavePlaybackStateUseCase(settingsRepo)
    private val useCase = SeekTrackUseCase(mediaRepo, saveState)

    private val song = Song(
        path = "/music/song.mp3",
        contentUri = "content://song",
        title = "Song",
        artist = "Artist",
        duration = 300_000L,
        albumArtPath = null
    )

    @Test
    fun `seek calls repository seekTo with correct position`() = runTest {
        useCase(30_000L)
        assertEquals(30_000L, mediaRepo.seekToPosition)
    }

    @Test
    fun `seek saves playback state when song is playing`() = runTest {
        mediaRepo.setState(IMediaRepository.PlaybackState(currentSong = song))
        useCase(30_000L)
        assertNotNull(settingsRepo.savedLastPlayedSong)
        assertEquals(30_000L, settingsRepo.savedLastPlayedSong?.position)
    }

    @Test
    fun `seek does not save state when no song is playing`() = runTest {
        // currentSong = null (default state)
        useCase(0L)
        assertNull(settingsRepo.savedLastPlayedSong)
    }
}
