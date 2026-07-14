package com.example.newaudio.data.audio

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.repository.ISettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSnapshotWriterTest {
    @Test
    fun `ordinary persistence failure does not stop later requests`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = mockk<ISettingsRepository>(relaxed = true)
        coEvery { repository.saveLastPlayedSong(any(), 1L, any()) } throws
            IllegalStateException("disk")
        val writer = PlaybackSnapshotWriter(repository, backgroundScope, dispatcher)

        assertTrue(writer.request(song(), 1L, "/music"))
        runCurrent()
        assertTrue(writer.request(song(), 2L, "/music"))
        runCurrent()

        coVerify { repository.saveLastPlayedSong(song(), 2L, "/music") }
    }

    @Test
    fun `cancelled owner scope rejects later requests`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(Job() + dispatcher)
        val writer = PlaybackSnapshotWriter(mockk(relaxed = true), scope, dispatcher)

        scope.cancel()
        runCurrent()

        assertFalse(writer.request(song(), 1L, "/music"))
    }

    private fun song() = Song(
        "/music/song.mp3",
        "content://song",
        "Song",
        "Artist",
        1_000L,
        null
    )
}
