package com.example.newaudio.domain.usecase.media

import com.example.newaudio.domain.model.Song
import com.example.newaudio.fake.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavePlaybackStateUseCaseTest {

    private val repo = FakeSettingsRepository()
    private val useCase = SavePlaybackStateUseCase(repo)

    private val song = Song(
        path = "/music/song.mp3",
        contentUri = "content://song",
        title = "Song",
        artist = "Artist",
        duration = 240_000L,
        albumArtPath = null
    )

    @Test
    fun `saves last played song when song is not null`() = runTest {
        useCase(song, 60_000L)
        assertEquals(song, repo.savedLastPlayedSong?.song)
        assertEquals(60_000L, repo.savedLastPlayedSong?.position)
    }

    @Test
    fun `does not save when song is null`() = runTest {
        useCase(null, 0L)
        assertNull(repo.savedLastPlayedSong)
    }

    @Test
    fun `saves with position zero`() = runTest {
        useCase(song, 0L)
        assertEquals(0L, repo.savedLastPlayedSong?.position)
    }
}
