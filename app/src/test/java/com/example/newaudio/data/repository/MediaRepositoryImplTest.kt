package com.example.newaudio.data.repository

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.example.newaudio.data.audio.PlayerListenerDelegate
import com.example.newaudio.data.media.controller.MediaControllerFactory
import com.example.newaudio.data.media.controller.MediaControllerGateway
import com.example.newaudio.data.media.controller.MediaControllerUnavailableException
import com.example.newaudio.data.media.controller.PlayerListenerDelegateFactory
import com.example.newaudio.data.media.deletion.DeletedMediaReconciler
import com.example.newaudio.data.media.deletion.DeletedMediaDecisionCalculator
import com.example.newaudio.data.media.library.MediaLibraryRepository
import com.example.newaudio.data.media.mapping.Media3ItemMapper
import com.example.newaudio.data.media.mapping.Media3PlaybackStateSynchronizer
import com.example.newaudio.data.media.playback.PlaybackQueueState
import com.example.newaudio.data.media.playback.PlaybackSessionCoordinator
import com.example.newaudio.data.media.playback.PlaybackStateStore
import com.example.newaudio.data.media.playback.PlaybackTransitionCoordinator
import com.example.newaudio.data.media.playback.ControllerPlaybackSnapshot
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.fake.FakeSettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaRepositoryImplTest {

    @Test
    fun `empty playlist does not initialize controller`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = buildFixture(dispatcher, controller(0, 0))

        fixture.repository.playPlaylist(emptyList(), 0, null)

        assertEquals(0, fixture.factoryCalls.get())
    }

    @Test
    fun `playPlaylist applies preferences updates queue and starts requested song`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = controller(itemCount = 0, currentIndex = 0)
        val fixture = buildFixture(dispatcher, controller)
        val songs = listOf(song("a"), song("b"))

        fixture.repository.playPlaylist(songs, startIndex = 1, folderPath = "/music")

        assertEquals(songs, fixture.queueState.snapshot().songs)
        assertEquals("/music", fixture.queueState.snapshot().folderPath)
        verify { controller.shuffleModeEnabled = false }
        verify { controller.repeatMode = Player.REPEAT_MODE_ALL }
        verify {
            controller.setMediaItems(
                match<List<MediaItem>> { items -> items.map { it.mediaId } == songs.map { it.path } },
                1,
                0L
            )
        }
        verify { controller.prepare() }
        verify { controller.play() }
    }

    @Test
    fun `required playback reports known service outage without hiding it as success`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = controller(itemCount = 0, currentIndex = 0)
        val fixture = buildFixture(
            dispatcher,
            controller,
            factoryFailure = MediaControllerUnavailableException(
                cause = IllegalStateException("service offline")
            )
        )

        fixture.repository.playPlaylist(listOf(song("a")), 0, "/music")

        assertNotNull(fixture.stateStore.value.playerError)
        assertFalse(fixture.stateStore.value.isRestoring)
        verify(exactly = 0) { controller.setMediaItems(any<List<MediaItem>>(), any(), any()) }
    }

    @Test
    fun `resume music restores captured position pause state and audio queue`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = controller(itemCount = 1, currentIndex = 0).also {
            every { it.currentPosition } returns 20L
            every { it.isPlaying } returns false
        }
        val fixture = buildFixture(dispatcher, controller)
        val songs = listOf(song("a"), song("b"))
        fixture.queueState.setMusic(songs, "/music")
        fixture.stateStore.update { it.copy(currentSong = songs[1]) }
        fixture.sessionCoordinator.captureBeforeVideoPlayback(
            fixture.stateStore.value,
            ControllerPlaybackSnapshot(1, 123L, false)
        )
        fixture.queueState.setVideos(listOf(video("/video/a.mp4")), "/video")
        fixture.stateStore.update {
            it.copy(currentSong = null, currentVideo = video("/video/a.mp4"))
        }

        val restored = fixture.repository.resumeLastMusicSession()

        assertEquals(true, restored)
        assertEquals(songs, fixture.queueState.snapshot().songs)
        assertEquals(songs[1], fixture.stateStore.value.currentSong)
        assertFalse(fixture.stateStore.value.isPlaying)
        verify {
            controller.setMediaItems(any<List<MediaItem>>(), 1, 123L)
        }
        verify { controller.pause() }
    }

    @Test
    fun `library count is delegated to media library repository`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = buildFixture(dispatcher, controller(0, 0))
        coEvery { fixture.library.getSongCount() } returns 7

        assertEquals(7, fixture.repository.getLibrarySongCount())
    }

    @Test
    fun `skipNext wraps last video to first media item`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = controller(itemCount = 3, currentIndex = 2)
        val fixture = buildFixture(dispatcher, controller)
        fixture.stateStore.update {
            it.copy(currentVideo = video("/video/last.mp4"))
        }

        fixture.repository.skipNext()

        verify(exactly = 1) { controller.seekTo(0, 0L) }
        verify(exactly = 0) { controller.seekToNextMediaItem() }
    }

    @Test
    fun `skipPrevious wraps first video to last media item`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = controller(itemCount = 3, currentIndex = 0)
        val fixture = buildFixture(dispatcher, controller)
        fixture.stateStore.update {
            it.copy(currentVideo = video("/video/first.mp4"))
        }

        fixture.repository.skipPrevious()

        verify(exactly = 1) { controller.seekTo(2, 0L) }
        verify(exactly = 0) { controller.seekToPreviousMediaItem() }
    }

    @Test
    fun `skipNext delegates to regular player navigation when no video is active`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = controller(itemCount = 3, currentIndex = 2)
        val fixture = buildFixture(dispatcher, controller)

        fixture.repository.skipNext()

        verify(exactly = 1) { controller.seekToNextMediaItem() }
        verify(exactly = 0) { controller.seekTo(any<Int>(), any<Long>()) }
    }

    @Test
    fun `failed playlist command keeps previous queue and app state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = controller(itemCount = 0, currentIndex = 0)
        every {
            controller.setMediaItems(any<List<MediaItem>>(), any(), any())
        } throws IllegalStateException("set items")
        val fixture = buildFixture(dispatcher, controller)
        val oldSongs = listOf(song("old"))
        fixture.queueState.setMusic(oldSongs, "/old")
        fixture.stateStore.update { it.copy(currentSong = oldSongs[0], currentPosition = 55L) }

        try {
            fixture.repository.playPlaylist(listOf(song("new")), 0, "/new")
            fail("setMediaItems failure should propagate")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertEquals(oldSongs, fixture.queueState.snapshot().songs)
        assertEquals("/old", fixture.queueState.snapshot().folderPath)
        assertEquals(oldSongs[0], fixture.stateStore.value.currentSong)
        assertEquals(55L, fixture.stateStore.value.currentPosition)
        verify { controller.clearMediaItems() }
    }

    @Test
    fun `failed music resume leaves stored session available`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = controller(itemCount = 0, currentIndex = 0)
        every {
            controller.setMediaItems(any<List<MediaItem>>(), any(), any())
        } throws IllegalStateException("resume")
        val fixture = buildFixture(dispatcher, controller)
        val songs = listOf(song("a"), song("b"))
        fixture.queueState.setMusic(songs, "/music")
        fixture.stateStore.update { it.copy(currentSong = songs[1]) }
        fixture.sessionCoordinator.captureBeforeVideoPlayback(
            fixture.stateStore.value,
            ControllerPlaybackSnapshot(1, 123L, false)
        )
        val stored = fixture.sessionCoordinator.peekMusicSession()
        val videos = listOf(video("/video/a.mp4"))
        fixture.queueState.setVideos(videos, "/video")
        fixture.stateStore.update { it.copy(currentSong = null, currentVideo = videos[0]) }

        try {
            fixture.repository.resumeLastMusicSession()
            fail("resume failure should propagate")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertTrue(fixture.sessionCoordinator.peekMusicSession() === stored)
        assertEquals(videos, fixture.queueState.snapshot().videos)
        assertEquals(videos[0], fixture.stateStore.value.currentVideo)
    }

    @Test
    fun `transition rollback failure is attached to original command failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = controller(itemCount = 0, currentIndex = 0)
        every {
            controller.setMediaItems(any<List<MediaItem>>(), any(), any())
        } throws IllegalStateException("command")
        every { controller.clearMediaItems() } throws IllegalArgumentException("rollback")
        val fixture = buildFixture(dispatcher, controller)

        val error = try {
            fixture.repository.playPlaylist(listOf(song("new")), 0, "/new")
            fail("command failure should propagate")
            error("unreachable")
        } catch (error: IllegalStateException) {
            error
        }

        val rollback = generateSequence(error as Throwable) { it.cause }
            .flatMap { it.suppressed.asSequence() }
            .first()
        assertEquals("rollback", rollback.message)
    }

    @Test
    fun `synchronous listener state change is restored when prepare later fails`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = controller(itemCount = 0, currentIndex = 0)
        val fixture = buildFixture(dispatcher, controller)
        val oldSong = song("old")
        val callbackSong = song("callback")
        fixture.queueState.setMusic(listOf(oldSong), "/old")
        fixture.stateStore.update { it.copy(currentSong = oldSong, currentPosition = 44L) }
        every {
            controller.setMediaItems(any<List<MediaItem>>(), any(), any())
        } answers {
            fixture.stateStore.update {
                it.copy(currentSong = callbackSong, currentPosition = 0L)
            }
            Unit
        }
        every { controller.prepare() } throws IllegalStateException("prepare")

        try {
            fixture.repository.playPlaylist(listOf(song("new")), 0, "/new")
            fail("prepare failure should propagate")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertEquals(oldSong, fixture.stateStore.value.currentSong)
        assertEquals(44L, fixture.stateStore.value.currentPosition)
        assertEquals(listOf(oldSong), fixture.queueState.snapshot().songs)
        assertEquals("/old", fixture.queueState.snapshot().folderPath)
    }

    private fun controller(itemCount: Int, currentIndex: Int): MediaController {
        return mockk(relaxed = true) {
            every { mediaItemCount } returns itemCount
            every { currentMediaItemIndex } returns currentIndex
        }
    }

    private fun buildFixture(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        controller: MediaController,
        factoryFailure: Throwable? = null
    ): Fixture {
        val stateStore = PlaybackStateStore()
        val queueState = PlaybackQueueState()
        val itemMapper = Media3ItemMapper()
        val listenerFactory = mockk<PlayerListenerDelegateFactory>()
        val synchronizer = mockk<Media3PlaybackStateSynchronizer>()
        val factoryCalls = AtomicInteger()
        every { listenerFactory.create(controller, any()) } returns mockk<PlayerListenerDelegate>(relaxed = true)
        coEvery { synchronizer.synchronize(controller) } returns false
        val gateway = MediaControllerGateway(
            controllerFactory = object : MediaControllerFactory {
                override suspend fun create(): MediaController {
                    factoryCalls.incrementAndGet()
                    factoryFailure?.let { throw it }
                    return controller
                }
            },
            listenerFactory = listenerFactory,
            stateSynchronizer = synchronizer,
            stateStore = stateStore,
            mainDispatcher = dispatcher
        )
        val library = mockk<MediaLibraryRepository>(relaxed = true)
        val sessionCoordinator = PlaybackSessionCoordinator(queueState)
        return Fixture(
            repository = MediaRepositoryImpl(
                settingsRepository = FakeSettingsRepository(),
                stateStore = stateStore,
                queueState = queueState,
                itemMapper = itemMapper,
                controllerGateway = gateway,
                sessionCoordinator = sessionCoordinator,
                transitionCoordinator = PlaybackTransitionCoordinator(
                    queueState,
                    stateStore,
                    sessionCoordinator
                ),
                mediaLibraryRepository = library,
                deletedMediaReconciler = DeletedMediaReconciler(
                    stateStore,
                    queueState,
                    itemMapper,
                    DeletedMediaDecisionCalculator()
                )
            ),
            stateStore = stateStore,
            queueState = queueState,
            sessionCoordinator = sessionCoordinator,
            library = library,
            factoryCalls = factoryCalls
        )
    }

    private data class Fixture(
        val repository: MediaRepositoryImpl,
        val stateStore: PlaybackStateStore,
        val queueState: PlaybackQueueState,
        val sessionCoordinator: PlaybackSessionCoordinator,
        val library: MediaLibraryRepository,
        val factoryCalls: AtomicInteger
    )

    private fun video(path: String): Video = Video(
        path = path,
        contentUri = "content://video/${path.hashCode()}",
        title = path.substringAfterLast('/').substringBeforeLast('.'),
        duration = 1_000L,
        thumbnailUri = null,
        width = 1920,
        height = 1080
    )

    private fun song(name: String): Song = Song(
        path = "/music/$name.mp3",
        contentUri = "content://song/$name",
        title = name,
        artist = "Artist",
        duration = 1_000L,
        albumArtPath = null
    )
}
