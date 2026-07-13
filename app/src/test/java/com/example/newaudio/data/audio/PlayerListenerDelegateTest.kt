package com.example.newaudio.data.audio

import android.content.Context
import androidx.media3.common.Player
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.repository.ISettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerListenerDelegateTest {
    @Test
    fun `serialized snapshot writer cannot let an older request overwrite the latest`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = mockk<ISettingsRepository>(relaxed = true)
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val persistedPositions = mutableListOf<Long>()
        coEvery { settings.saveLastPlayedSong(any(), any(), any()) } coAnswers {
            val position = arg<Long>(1)
            persistedPositions += position
            if (position == 100L) {
                firstWriteStarted.complete(Unit)
                releaseFirstWrite.await()
            }
        }
        val playbackState = MutableStateFlow(
            IMediaRepository.PlaybackState(currentSong = song(), currentPosition = 100L)
        )
        val delegate = PlayerListenerDelegate(
            context = mockk<Context>(relaxed = true),
            playbackState = playbackState,
            settingsRepository = settings,
            player = mockk<Player>(relaxed = true),
            coroutineScope = backgroundScope,
            ioDispatcher = dispatcher
        )
        runCurrent()

        delegate.saveCurrentState()
        runCurrent()
        firstWriteStarted.await()

        playbackState.value = playbackState.value.copy(currentPosition = 200L)
        delegate.saveCurrentState()
        playbackState.value = playbackState.value.copy(currentPosition = 300L)
        delegate.saveCurrentState()

        releaseFirstWrite.complete(Unit)
        runCurrent()

        assertEquals(listOf(100L, 300L), persistedPositions)
    }

    private fun song() = Song(
        path = "/music/song.mp3",
        contentUri = "content://song",
        title = "Song",
        artist = "Artist",
        duration = 1_000L,
        albumArtPath = null
    )
}
