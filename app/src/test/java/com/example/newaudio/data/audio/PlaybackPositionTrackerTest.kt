package com.example.newaudio.data.audio

import androidx.media3.common.Player
import com.example.newaudio.data.media.playback.PlaybackQueueState
import com.example.newaudio.data.media.playback.PlaybackStateStore
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.repository.ISettingsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackPositionTrackerTest {
    @Test
    fun `duplicate start signal owns one ticker and pause stops it`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var playing = true
        var position = 100L
        val player = mockk<Player>(relaxed = true) {
            every { isPlaying } answers { playing }
            every { currentPosition } answers { position }
        }
        val stateStore = PlaybackStateStore()
        val tracker = tracker(player, stateStore, dispatcher)

        tracker.synchronize()
        tracker.synchronize()
        runCurrent()
        position = 200L
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(200L, stateStore.value.currentPosition)
        verify(exactly = 2) { player.currentPosition }

        playing = false
        tracker.synchronize()
        advanceTimeBy(2_000L)
        runCurrent()
        verify(exactly = 2) { player.currentPosition }
    }

    @Test
    fun `auto save uses monotonic interval and position delta`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val song = song()
        val player = mockk<Player>(relaxed = true) {
            every { isPlaying } returns true
            every { currentPosition } returns 4_000L
        }
        val stateStore = PlaybackStateStore().apply {
            update { it.copy(currentSong = song, currentPosition = 0L) }
        }
        val queueState = PlaybackQueueState().apply { setMusic(listOf(song), "/music") }
        val repository = mockk<ISettingsRepository>(relaxed = true)
        val writer = PlaybackSnapshotWriter(repository, backgroundScope, dispatcher)
        val tracker = PlaybackPositionTracker(
            player,
            stateStore,
            queueState,
            writer,
            backgroundScope,
            clock = MonotonicClock { 6_000L }
        )

        tracker.synchronize()
        runCurrent()

        coVerify { repository.saveLastPlayedSong(song, 4_000L, "/music") }
    }

    private fun kotlinx.coroutines.test.TestScope.tracker(
        player: Player,
        stateStore: PlaybackStateStore,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher
    ): PlaybackPositionTracker {
        val repository = mockk<ISettingsRepository>(relaxed = true)
        val writer = PlaybackSnapshotWriter(repository, backgroundScope, dispatcher)
        return PlaybackPositionTracker(
            player,
            stateStore,
            PlaybackQueueState(),
            writer,
            backgroundScope,
            clock = MonotonicClock { 0L }
        )
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
