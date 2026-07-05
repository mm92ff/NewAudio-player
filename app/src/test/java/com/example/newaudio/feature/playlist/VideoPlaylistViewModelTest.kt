package com.example.newaudio.feature.playlist

import com.example.newaudio.R
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.fake.FakeSettingsRepository
import com.example.newaudio.fake.FakeVideoPlaylistRepository
import com.example.newaudio.util.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlaylistViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val videoPlaylistRepo = FakeVideoPlaylistRepository()
    private val settingsRepo = FakeSettingsRepository()

    private fun buildViewModel(): VideoPlaylistViewModel = VideoPlaylistViewModel(
        videoPlaylistRepository = videoPlaylistRepo,
        getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepo)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onCreatePlaylist creates video playlist`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onCreatePlaylist("Training")
        advanceUntilIdle()

        val playlists = vm.uiState.value.playlists
        assertEquals(1, playlists.size)
        assertEquals("Training", playlists[0].name)
    }

    @Test
    fun `onRenamePlaylist updates video playlist name`() = runTest {
        val playlistId = videoPlaylistRepo.createVideoPlaylist("Old")
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onRenamePlaylist(vm.uiState.value.playlists.single { it.id == playlistId }, "New")
        advanceUntilIdle()

        assertEquals("New", vm.uiState.value.playlists.single().name)
    }

    @Test
    fun `onDeletePlaylist removes video playlist`() = runTest {
        videoPlaylistRepo.createVideoPlaylist("Delete me")
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onDeletePlaylist(vm.uiState.value.playlists.single())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.playlists.isEmpty())
    }

    @Test
    fun `onDuplicatePlaylist copies playlist videos`() = runTest {
        val playlistId = videoPlaylistRepo.createVideoPlaylist("Original")
        val video = testVideo("clip.mp4")
        videoPlaylistRepo.addVideoToPlaylist(playlistId, video)
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onDuplicatePlaylist(vm.uiState.value.playlists.single())
        advanceUntilIdle()

        val duplicated = vm.uiState.value.playlists.single { it.name == "Original_v2.0" }
        assertEquals(listOf(video), videoPlaylistRepo.getVideosInPlaylist(duplicated.id).first())
    }

    @Test
    fun `togglePlaylistExpansion loads and clears video playlist entries`() = runTest {
        val playlistId = videoPlaylistRepo.createVideoPlaylist("Videos")
        val video = testVideo("clip.mp4")
        videoPlaylistRepo.addVideoToPlaylist(playlistId, video)
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.togglePlaylistExpansion(playlistId)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.expandedIds.contains(playlistId))
        assertEquals(listOf(video), vm.uiState.value.playlistVideos[playlistId])

        vm.togglePlaylistExpansion(playlistId)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.expandedIds.contains(playlistId))
        assertTrue(vm.uiState.value.playlistVideos.isEmpty())
    }

    @Test
    fun `onPlayPlaylist emits videos from repository`() = runTest {
        val playlistId = videoPlaylistRepo.createVideoPlaylist("Videos")
        val video = testVideo("clip.mp4")
        videoPlaylistRepo.addVideoToPlaylist(playlistId, video)
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val event = async { vm.events.first() }
        vm.onPlayPlaylist(vm.uiState.value.playlists.single())
        advanceUntilIdle()

        val playEvent = event.await() as VideoPlaylistEvent.PlayVideoPlaylist
        assertEquals(listOf(video), playEvent.videos)
    }

    @Test
    fun `onPlayVideoInPlaylist emits selected video with full playlist queue`() = runTest {
        val playlistId = videoPlaylistRepo.createVideoPlaylist("Videos")
        val first = testVideo("first.mp4")
        val second = testVideo("second.mp4")
        videoPlaylistRepo.addVideosToPlaylist(playlistId, listOf(first, second))
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val event = async { vm.events.first() }
        vm.onPlayVideoInPlaylist(second, playlistId)
        advanceUntilIdle()

        val playEvent = event.await() as VideoPlaylistEvent.PlayVideoInPlaylist
        assertEquals(second, playEvent.video)
        assertEquals(listOf(first, second), playEvent.allVideos)
    }

    @Test
    fun `onPlayPlaylist with empty playlist sends empty snackbar`() = runTest {
        videoPlaylistRepo.createVideoPlaylist("Empty")
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val sideEffect = async { vm.sideEffects.first() }
        vm.onPlayPlaylist(vm.uiState.value.playlists.single())
        advanceUntilIdle()

        val snackbar = sideEffect.await() as PlaylistSideEffect.ShowSnackbar
        assertTrue(snackbar.message is UiText.StringResource)
        assertEquals(R.string.playlist_is_empty, (snackbar.message as UiText.StringResource).resId)
    }

    @Test
    fun `removeSelected removes selected video from video playlist`() = runTest {
        val playlistId = videoPlaylistRepo.createVideoPlaylist("Videos")
        val video = testVideo("clip.mp4")
        videoPlaylistRepo.addVideoToPlaylist(playlistId, video)
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.toggleEditMode()
        vm.toggleVideoSelection(playlistId, video.path)
        advanceUntilIdle()
        vm.removeSelected()
        advanceUntilIdle()

        assertTrue(videoPlaylistRepo.getVideosInPlaylist(playlistId).first().isEmpty())
    }

    @Test
    fun `moveSelectedDown reorders selected video`() = runTest {
        val playlistId = videoPlaylistRepo.createVideoPlaylist("Videos")
        val first = testVideo("first.mp4")
        val second = testVideo("second.mp4")
        videoPlaylistRepo.addVideosToPlaylist(playlistId, listOf(first, second))
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.togglePlaylistExpansion(playlistId)
        vm.toggleEditMode()
        vm.toggleVideoSelection(playlistId, first.path)
        advanceUntilIdle()

        vm.moveSelectedDown()
        advanceTimeBy(501)
        advanceUntilIdle()

        assertEquals(listOf(second, first), videoPlaylistRepo.getVideosInPlaylist(playlistId).first())
    }

    @Test
    fun `moveSelectedUp reorders selected playlist`() = runTest {
        val firstId = videoPlaylistRepo.createVideoPlaylist("First")
        val secondId = videoPlaylistRepo.createVideoPlaylist("Second")
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.toggleEditMode()
        vm.togglePlaylistSelection(secondId)
        advanceUntilIdle()

        vm.moveSelectedUp()
        advanceTimeBy(501)
        advanceUntilIdle()

        assertEquals(listOf(secondId, firstId), vm.uiState.value.playlists.map { it.id })
    }

    private fun testVideo(fileName: String): Video {
        return Video(
            path = "/videos/$fileName",
            contentUri = "file:///videos/$fileName",
            title = fileName.removeSuffix(".mp4"),
            duration = 30_000L,
            thumbnailUri = null,
            width = 1920,
            height = 1080
        )
    }
}
