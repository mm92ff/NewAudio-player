package com.example.newaudio.data.audio

import androidx.media3.common.Player
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.ISettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackPreferenceWriterTest {
    @Test
    fun `slow repeat write cannot finish after and overwrite newest value`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = mockk<ISettingsRepository>(relaxed = true)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val writes = mutableListOf<UserPreferences.RepeatMode>()
        coEvery { repository.setRepeatMode(any()) } coAnswers {
            val mode = firstArg<UserPreferences.RepeatMode>()
            writes += mode
            if (mode == UserPreferences.RepeatMode.ONE) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        val writer = PlaybackPreferenceWriter(repository, backgroundScope, dispatcher)

        writer.requestRepeatMode(Player.REPEAT_MODE_ONE)
        runCurrent()
        firstStarted.await()
        writer.requestRepeatMode(Player.REPEAT_MODE_OFF)
        writer.requestRepeatMode(Player.REPEAT_MODE_ALL)
        releaseFirst.complete(Unit)
        runCurrent()

        assertEquals(
            listOf(UserPreferences.RepeatMode.ONE, UserPreferences.RepeatMode.ALL),
            writes
        )
    }

    @Test
    fun `repeat and shuffle use independent workers`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = mockk<ISettingsRepository>(relaxed = true)
        val writer = PlaybackPreferenceWriter(repository, backgroundScope, dispatcher)

        writer.requestRepeatMode(Player.REPEAT_MODE_ALL)
        writer.requestShuffle(true)
        runCurrent()

        io.mockk.coVerify { repository.setRepeatMode(UserPreferences.RepeatMode.ALL) }
        io.mockk.coVerify { repository.setShuffleEnabled(true) }
    }
}
