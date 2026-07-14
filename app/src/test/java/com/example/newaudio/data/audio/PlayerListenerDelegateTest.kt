package com.example.newaudio.data.audio

import androidx.media3.common.FlagSet
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.newaudio.data.media.mapping.Media3ItemMapper
import com.example.newaudio.data.media.mapping.PlaybackErrorMapper
import com.example.newaudio.data.media.playback.PlaybackQueueState
import com.example.newaudio.data.media.playback.PlaybackStateStore
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.repository.ISettingsRepository
import com.example.newaudio.fake.FakeSettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerListenerDelegateTest {
    @Test
    fun `media transition resolves audio from shared queue and clears video`() = runTest {
        val stateStore = PlaybackStateStore()
        val queueState = PlaybackQueueState().apply { setMusic(listOf(song()), "/music") }
        val delegate = delegate(stateStore, queueState)

        delegate.onMediaItemTransition(
            MediaItem.Builder().setMediaId(song().path).build(),
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        )

        assertEquals(song(), stateStore.value.currentSong)
        assertNull(stateStore.value.currentVideo)
    }

    @Test
    fun `media transition resolves video from shared queue and clears song`() = runTest {
        val video = Video("/video/a.mp4", "content://video/a", "A", 2_000L, null)
        val stateStore = PlaybackStateStore().apply { update { it.copy(currentSong = song()) } }
        val queueState = PlaybackQueueState().apply { setVideos(listOf(video), "/video") }
        val delegate = delegate(stateStore, queueState)

        delegate.onMediaItemTransition(
            MediaItem.Builder().setMediaId(video.path).build(),
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        )

        assertEquals(video, stateStore.value.currentVideo)
        assertNull(stateStore.value.currentSong)
    }

    @Test
    fun `player error stops state and exposes original decoder message`() = runTest {
        val stateStore = PlaybackStateStore().apply { update { it.copy(isPlaying = true) } }
        val delegate = delegate(stateStore, PlaybackQueueState())

        delegate.onPlayerError(
            PlaybackException(
                "decoder failed",
                null,
                PlaybackException.ERROR_CODE_DECODING_FAILED
            )
        )

        assertFalse(stateStore.value.isPlaying)
        assertEquals(PlaybackException.ERROR_CODE_DECODING_FAILED, stateStore.value.playerError?.code)
        assertEquals("decoder failed", stateStore.value.playerError?.message)
    }

    @Test
    fun `player events update state and persist repeat and shuffle preferences`() = runTest {
        val stateStore = PlaybackStateStore()
        val settings = FakeSettingsRepository()
        val player = mockk<Player>(relaxed = true) {
            every { isPlaying } returns true
            every { currentPosition } returns 321L
            every { duration } returns 999L
            every { shuffleModeEnabled } returns true
            every { repeatMode } returns Player.REPEAT_MODE_ONE
        }
        val delegate = delegate(
            stateStore = stateStore,
            queueState = PlaybackQueueState(),
            settingsRepository = settings,
            player = player
        )
        val events = Player.Events(
            FlagSet.Builder()
                .add(Player.EVENT_IS_PLAYING_CHANGED)
                .add(Player.EVENT_REPEAT_MODE_CHANGED)
                .add(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
                .build()
        )

        delegate.onEvents(player, events)
        runCurrent()

        assertEquals(true, stateStore.value.isPlaying)
        assertEquals(321L, stateStore.value.currentPosition)
        assertEquals(999L, stateStore.value.totalDuration)
        assertEquals(true, settings.userPreferences.first().isShuffleEnabled)
        assertEquals(UserPreferences.RepeatMode.ONE, settings.userPreferences.first().repeatMode)
    }

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
        val stateStore = PlaybackStateStore().apply {
            update { it.copy(currentSong = song(), currentPosition = 100L) }
        }
        val queueState = PlaybackQueueState().apply { setMusic(listOf(song()), "/music") }
        val delegate = PlayerListenerDelegate(
            stateStore = stateStore,
            queueState = queueState,
            itemMapper = Media3ItemMapper(),
            player = mockk<Player>(relaxed = true),
            collaborators = collaborators(
                settings,
                stateStore,
                queueState,
                mockk(relaxed = true),
                dispatcher
            )
        )
        runCurrent()

        delegate.saveCurrentState()
        runCurrent()
        firstWriteStarted.await()

        stateStore.update { it.copy(currentPosition = 200L) }
        delegate.saveCurrentState()
        stateStore.update { it.copy(currentPosition = 300L) }
        delegate.saveCurrentState()

        releaseFirstWrite.complete(Unit)
        runCurrent()

        assertEquals(listOf(100L, 300L), persistedPositions)
    }

    private fun kotlinx.coroutines.test.TestScope.delegate(
        stateStore: PlaybackStateStore,
        queueState: PlaybackQueueState,
        settingsRepository: ISettingsRepository = mockk(relaxed = true),
        player: Player = mockk(relaxed = true)
    ): PlayerListenerDelegate {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val collaborators = collaborators(
            settingsRepository,
            stateStore,
            queueState,
            player,
            dispatcher
        )
        return PlayerListenerDelegate(
            stateStore = stateStore,
            queueState = queueState,
            itemMapper = Media3ItemMapper(),
            player = player,
            collaborators = collaborators
        )
    }

    private fun kotlinx.coroutines.test.TestScope.collaborators(
        settingsRepository: ISettingsRepository,
        stateStore: PlaybackStateStore,
        queueState: PlaybackQueueState,
        player: Player,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher
    ): PlaybackListenerCollaborators {
        val snapshotWriter = PlaybackSnapshotWriter(
            settingsRepository,
            backgroundScope,
            dispatcher
        )
        return PlaybackListenerCollaborators(
            snapshotWriter = snapshotWriter,
            preferenceWriter = PlaybackPreferenceWriter(
                settingsRepository,
                backgroundScope,
                dispatcher
            ),
            positionTracker = PlaybackPositionTracker(
                player,
                stateStore,
                queueState,
                snapshotWriter,
                backgroundScope,
                clock = MonotonicClock { 10_000L }
            ),
            errorMapper = playbackErrorMapper()
        )
    }

    private fun playbackErrorMapper(): PlaybackErrorMapper {
        val mapper = mockk<PlaybackErrorMapper>()
        every { mapper.map(any()) } answers {
            val error = firstArg<PlaybackException>()
            com.example.newaudio.domain.repository.IMediaRepository.PlayerError(
                error.errorCode,
                error.message ?: "Unknown error"
            )
        }
        return mapper
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
