package com.example.newaudio.feature.filebrowser

import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.MediaBrowserMode
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.usecase.file.CreateFolderResult
import com.example.newaudio.domain.usecase.file.*
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.fake.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mediaRepo = FakeMediaRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val playlistRepo = FakePlaylistRepository()
    private val videoPlaylistRepo = FakeVideoPlaylistRepository()
    private val scannerRepo = FakeMediaScannerRepository()

    // Mock the use cases since they have complex dependencies
    private val getSortedFileTreeUseCase = mockk<GetSortedFileTreeUseCase>(relaxed = true)
    private val saveFolderOrderUseCase = mockk<SaveFolderOrderUseCase>(relaxed = true)
    private val deleteFileUseCase = mockk<DeleteFileUseCase>(relaxed = true)
    private val deleteMultipleFilesUseCase = mockk<DeleteMultipleFilesUseCase>(relaxed = true)
    private val renameFileUseCase = mockk<RenameFileUseCase>(relaxed = true)
    private val createFolderUseCase = mockk<CreateFolderUseCase>(relaxed = true)
    private val copyMultipleFilesUseCase = mockk<CopyMultipleFilesUseCase>(relaxed = true)
    private val moveMultipleFilesUseCase = mockk<MoveMultipleFilesUseCase>(relaxed = true)
    private val getRootPathUseCase = mockk<GetRootPathUseCase>(relaxed = true)
    private val getParentPathUseCase = mockk<GetParentPathUseCase>(relaxed = true)
    private val syncCurrentFolderUseCase = mockk<SyncCurrentFolderUseCase>(relaxed = true)
    private val mediaStoreObserverRepo = mockk<com.example.newaudio.domain.repository.IMediaStoreObserverRepository>(relaxed = true)

    private fun buildViewModel(fileItems: List<FileItem> = emptyList()): FileBrowserViewModel {
        // Set up basic mock behavior
        coEvery { getRootPathUseCase(any()) } returns "/sdcard/Music"
        coEvery { getParentPathUseCase(any()) } returns "/sdcard"
        coEvery { createFolderUseCase(any(), any()) } returns CreateFolderResult.SUCCESS
        every { getSortedFileTreeUseCase(any(), any()) } returns flowOf(fileItems)

        return FileBrowserViewModel(
            getSortedFileTreeUseCase = getSortedFileTreeUseCase,
            saveFolderOrderUseCase = saveFolderOrderUseCase,
            deleteFileUseCase = deleteFileUseCase,
            deleteMultipleFilesUseCase = deleteMultipleFilesUseCase,
            renameFileUseCase = renameFileUseCase,
            createFolderUseCase = createFolderUseCase,
            copyMultipleFilesUseCase = copyMultipleFilesUseCase,
            moveMultipleFilesUseCase = moveMultipleFilesUseCase,
            getRootPathUseCase = getRootPathUseCase,
            getParentPathUseCase = getParentPathUseCase,
            syncCurrentFolderUseCase = syncCurrentFolderUseCase,
            mediaRepository = mediaRepo,
            getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepo),
            mediaStoreObserverRepository = mediaStoreObserverRepo,
            playlistRepository = playlistRepo,
            videoPlaylistRepository = videoPlaylistRepo,
            ioDispatcher = testDispatcher
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is not in edit mode`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isEditMode)
        assertTrue(vm.uiState.value.selectedPaths.isEmpty())
    }

    @Test
    fun `toggleEditMode switches edit mode state and clears selections`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Initially not in edit mode
        assertFalse(vm.uiState.value.isEditMode)

        // Toggle to edit mode
        vm.toggleEditMode()
        assertTrue(vm.uiState.value.isEditMode)
        assertTrue(vm.uiState.value.selectedPaths.isEmpty())

        // Toggle back to normal mode
        vm.toggleEditMode()
        assertFalse(vm.uiState.value.isEditMode)
    }

    @Test
    fun `onItemLongClicked enables edit mode when not already enabled`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val testFile = FileItem.OtherFile(
            path = "/test/document.txt",
            name = "document.txt"
        )

        // Initially not in edit mode
        assertFalse(vm.uiState.value.isEditMode)

        // Long click should enable edit mode
        vm.onItemLongClicked(testFile)
        assertTrue(vm.uiState.value.isEditMode)
        assertTrue(vm.uiState.value.selectedPaths.contains(testFile.path))
    }

    @Test
    fun `onSelectAll toggles between selecting all items and none`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Initially no items selected
        assertTrue(vm.uiState.value.selectedPaths.isEmpty())

        // Call onSelectAll when no items exist (should do nothing gracefully)
        vm.onSelectAll()
        assertTrue(vm.uiState.value.selectedPaths.isEmpty())
    }

    @Test
    fun `initial state has empty playlists list`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.playlists.isEmpty())
    }

    @Test
    fun `deleting active inline video removes it from list and hides inline player`() = runTest {
        val activeVideo = videoFile(
            path = "/sdcard/Movies/fight.mp4",
            name = "fight.mp4"
        )
        val otherVideo = videoFile(
            path = "/sdcard/Movies/next.mp4",
            name = "next.mp4"
        )
        coEvery { deleteFileUseCase(any(), activeVideo) } returns true

        val vm = buildViewModel(listOf(activeVideo, otherVideo))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        mediaRepo.setState(
            com.example.newaudio.domain.repository.IMediaRepository.PlaybackState(
                currentVideo = activeVideo.video
            )
        )
        vm.onShowInlineVideo()
        advanceUntilIdle()

        vm.onDeleteConfirmed(activeVideo)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showInlineVideo)
        assertNull(vm.uiState.value.activeVideoPath)
        assertEquals(listOf(otherVideo.path), vm.uiState.value.fileItems.map { it.path })
        assertEquals(listOf(activeVideo.path), mediaRepo.removedDeletedMediaPaths)
        coVerify { deleteFileUseCase("/sdcard/Music", activeVideo) }
        coVerify { syncCurrentFolderUseCase("/sdcard/Music", vm.uiState.value.browserMode) }
    }

    @Test
    fun `showing mini player video inline switches from music mode to video mode`() = runTest {
        val activeVideo = videoFile(
            path = "/sdcard/Movies/fight.mp4",
            name = "fight.mp4"
        )
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        mediaRepo.setState(
            com.example.newaudio.domain.repository.IMediaRepository.PlaybackState(
                currentVideo = activeVideo.video
            )
        )
        advanceUntilIdle()

        assertEquals(MediaBrowserMode.MUSIC, vm.uiState.value.browserMode)

        vm.onShowMiniPlayerVideoInline()
        advanceUntilIdle()

        assertEquals(MediaBrowserMode.VIDEO, vm.uiState.value.browserMode)
        assertTrue(vm.uiState.value.showInlineVideo)
    }

    @Test
    fun `clicking video file plays visible video playlist and opens inline player`() = runTest {
        val firstVideo = videoFile(
            path = "/sdcard/Movies/first.mp4",
            name = "first.mp4"
        )
        val secondVideo = videoFile(
            path = "/sdcard/Movies/second.mp4",
            name = "second.mp4"
        )
        val vm = buildViewModel(listOf(firstVideo, secondVideo))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onItemClicked(secondVideo)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showInlineVideo)
        assertEquals(listOf(firstVideo.video, secondVideo.video), mediaRepo.lastVideoPlaylistPlayed)
        assertEquals(1, mediaRepo.lastStartIndex)
        assertEquals("/sdcard/Movies", mediaRepo.lastFolderPath?.replace('\\', '/'))
    }

    @Test
    fun `switching to video resumes last video session and opens inline player when enabled`() = runTest {
        settingsRepo.setResumeSessionOnModeSwitch(true)
        val activeVideo = videoFile(
            path = "/sdcard/Movies/Training/fight.mp4",
            name = "fight.mp4"
        )
        mediaRepo.resumeLastVideoSessionResult = true
        mediaRepo.resumeLastVideoSessionState = com.example.newaudio.domain.repository.IMediaRepository.PlaybackState(
            currentVideo = activeVideo.video
        )
        val vm = buildViewModel()
        coEvery { getRootPathUseCase(MediaBrowserMode.VIDEO) } returns "/sdcard/Movies"
        every { getParentPathUseCase(activeVideo.path) } returns "/sdcard/Movies/Training"
        every { getParentPathUseCase("/sdcard/Movies/Training") } returns "/sdcard/Movies"
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onToggleBrowserMode()
        advanceUntilIdle()

        assertEquals(MediaBrowserMode.VIDEO, vm.uiState.value.browserMode)
        assertEquals(1, mediaRepo.resumeLastVideoSessionCalled)
        assertEquals(0, mediaRepo.resumeLastMusicSessionCalled)
        assertEquals("/sdcard/Movies/Training", vm.uiState.value.currentPath)
        assertTrue(vm.uiState.value.canNavigateBack)
        assertTrue(vm.uiState.value.showInlineVideo)

        vm.navigateUp()
        advanceUntilIdle()

        assertEquals("/sdcard/Movies", vm.uiState.value.currentPath)
        assertFalse(vm.uiState.value.canNavigateBack)
    }

    @Test
    fun `switching to music resumes last music session without opening inline video when enabled`() = runTest {
        settingsRepo.setResumeSessionOnModeSwitch(true)
        val restoredSong = song(
            path = "/sdcard/Music/Albums/restored.mp3",
            title = "restored"
        )
        mediaRepo.resumeLastVideoSessionResult = true
        mediaRepo.resumeLastMusicSessionResult = true
        mediaRepo.resumeLastMusicSessionState = com.example.newaudio.domain.repository.IMediaRepository.PlaybackState(
            currentSong = restoredSong
        )
        val vm = buildViewModel()
        every { getParentPathUseCase(restoredSong.path) } returns "/sdcard/Music/Albums"
        every { getParentPathUseCase("/sdcard/Music/Albums") } returns "/sdcard/Music"
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onToggleBrowserMode()
        advanceUntilIdle()
        vm.onToggleBrowserMode()
        advanceUntilIdle()

        assertEquals(MediaBrowserMode.MUSIC, vm.uiState.value.browserMode)
        assertEquals(1, mediaRepo.resumeLastVideoSessionCalled)
        assertEquals(1, mediaRepo.resumeLastMusicSessionCalled)
        assertEquals("/sdcard/Music/Albums", vm.uiState.value.currentPath)
        assertTrue(vm.uiState.value.canNavigateBack)
        assertFalse(vm.uiState.value.showInlineVideo)

        vm.navigateUp()
        advanceUntilIdle()

        assertEquals("/sdcard/Music", vm.uiState.value.currentPath)
        assertFalse(vm.uiState.value.canNavigateBack)
    }

    @Test
    fun `switching to music navigates to currently playing song folder when session resume is disabled`() = runTest {
        settingsRepo.setResumeSessionOnModeSwitch(false)
        val activeSong = song(
            path = "/sdcard/Music/Reachable/active.mp3",
            title = "active"
        )

        val vm = buildViewModel()
        every { getParentPathUseCase(activeSong.path) } returns "/sdcard/Music/Reachable"
        coEvery { getRootPathUseCase(MediaBrowserMode.VIDEO) } returns "/sdcard/Movies"
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        mediaRepo.setState(
            com.example.newaudio.domain.repository.IMediaRepository.PlaybackState(
                currentSong = activeSong
            )
        )
        advanceUntilIdle()

        vm.onToggleBrowserMode()
        advanceUntilIdle()
        assertEquals(MediaBrowserMode.VIDEO, vm.uiState.value.browserMode)

        vm.onToggleBrowserMode()
        advanceUntilIdle()

        assertEquals(MediaBrowserMode.MUSIC, vm.uiState.value.browserMode)
        assertEquals("/sdcard/Music/Reachable", vm.uiState.value.currentPath)
        assertEquals(0, mediaRepo.resumeLastMusicSessionCalled)
        assertFalse(vm.uiState.value.showInlineVideo)
    }

    @Test
    fun `exiting inline video navigates to active video folder`() = runTest {
        val activeVideo = videoFile(
            path = "/sdcard/Movies/Sub/fight.mp4",
            name = "fight.mp4"
        )
        coEvery { getRootPathUseCase(MediaBrowserMode.VIDEO) } returns "/sdcard/Movies"
        every { getSortedFileTreeUseCase("/sdcard/Movies/Sub", MediaBrowserMode.VIDEO) } returns flowOf(listOf(activeVideo))

        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        mediaRepo.setState(
            com.example.newaudio.domain.repository.IMediaRepository.PlaybackState(
                currentVideo = activeVideo.video
            )
        )
        advanceUntilIdle()

        vm.onShowInlineVideo()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showInlineVideo)

        vm.onExitInlineVideo()
        advanceUntilIdle()

        assertEquals(MediaBrowserMode.VIDEO, vm.uiState.value.browserMode)
        assertEquals("/sdcard/Movies/Sub", vm.uiState.value.currentPath)
        assertFalse(vm.uiState.value.showInlineVideo)
    }

    @Test
    fun `switching browser mode does not resume sessions when option is disabled`() = runTest {
        settingsRepo.setResumeSessionOnModeSwitch(false)
        mediaRepo.resumeLastVideoSessionResult = true
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onToggleBrowserMode()
        advanceUntilIdle()

        assertEquals(MediaBrowserMode.VIDEO, vm.uiState.value.browserMode)
        assertEquals(0, mediaRepo.resumeLastVideoSessionCalled)
        assertFalse(vm.uiState.value.showInlineVideo)
    }

    @Test
    fun `video preview setting is reflected in browser ui state`() = runTest {
        settingsRepo.setShowVideoPreviewItems(true)
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showVideoPreviewItems)
    }

    @Test
    fun `video display mode is reflected in browser ui state`() = runTest {
        settingsRepo.setVideoDisplayMode(com.example.newaudio.domain.model.UserPreferences.VideoDisplayMode.GALLERY_FILLED)
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(
            com.example.newaudio.domain.model.UserPreferences.VideoDisplayMode.GALLERY_FILLED,
            vm.uiState.value.videoDisplayMode
        )
    }

    @Test
    fun `video gallery columns are reflected in browser ui state`() = runTest {
        settingsRepo.setVideoGalleryColumns(4)
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(4, vm.uiState.value.videoGalleryColumns)
    }

    @Test
    fun `show video names in gallery is reflected in browser ui state`() = runTest {
        settingsRepo.setShowVideoNamesInGallery(true)
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showVideoNamesInGallery)
    }

    @Test
    fun `successful paste clears clipboard and syncs current folder`() = runTest {
        val video = videoFile(
            path = "/sdcard/Movies/source/fight.mp4",
            name = "fight.mp4"
        )
        coEvery { copyMultipleFilesUseCase(any(), any()) } returns true

        val vm = buildViewModel(listOf(video))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onCopyClick(video)
        vm.onPasteClick()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.clipboardState is ClipboardState.Empty)
        coVerify { copyMultipleFilesUseCase(listOf(video), "/sdcard/Music") }
        coVerify { syncCurrentFolderUseCase("/sdcard/Music", vm.uiState.value.browserMode) }
    }

    @Test
    fun `successful move paste clears clipboard and syncs current folder`() = runTest {
        val video = videoFile(
            path = "/sdcard/Music/source/fight.mp4",
            name = "fight.mp4"
        )
        coEvery { moveMultipleFilesUseCase(any(), any(), any()) } returns true

        val vm = buildViewModel(listOf(video))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onMoveClick(video)
        vm.onPasteClick()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.clipboardState is ClipboardState.Empty)
        coVerify { moveMultipleFilesUseCase(listOf(video), "/sdcard/Music", "/sdcard/Music") }
        coVerify { syncCurrentFolderUseCase("/sdcard/Music", vm.uiState.value.browserMode) }
    }

    @Test
    fun `failed paste keeps clipboard and exposes error`() = runTest {
        val video = videoFile(
            path = "/sdcard/Movies/source/fight.mp4",
            name = "fight.mp4"
        )
        coEvery { copyMultipleFilesUseCase(any(), any()) } returns false

        val vm = buildViewModel(listOf(video))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onCopyClick(video)
        vm.onPasteClick()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.clipboardState is ClipboardState.Active)
        assertTrue(vm.uiState.value.errorRes != null)
        coVerify(exactly = 0) { syncCurrentFolderUseCase(any(), any()) }
    }

    @Test
    fun `show create folder dialog sets dialog state`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onShowCreateFolderDialog()

        assertEquals(DialogState.CreateFolder, vm.uiState.value.dialogState)
    }

    @Test
    fun `create folder confirm calls use case and syncs current folder`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onShowCreateFolderDialog()
        vm.onCreateFolderConfirmed("Training")
        advanceUntilIdle()

        coVerify { createFolderUseCase("/sdcard/Music", "Training") }
        coVerify { syncCurrentFolderUseCase("/sdcard/Music", MediaBrowserMode.MUSIC) }
        assertEquals(DialogState.None, vm.uiState.value.dialogState)
    }

    @Test
    fun `create folder invalid name exposes error and keeps dialog open`() = runTest {
        val vm = buildViewModel()
        coEvery { createFolderUseCase(any(), any()) } returns CreateFolderResult.INVALID_NAME
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onShowCreateFolderDialog()
        vm.onCreateFolderConfirmed("Bad/Name")
        advanceUntilIdle()

        assertEquals(DialogState.CreateFolder, vm.uiState.value.dialogState)
        assertTrue(vm.uiState.value.errorRes != null)
        coVerify(exactly = 0) { syncCurrentFolderUseCase(any(), any()) }
    }

    @Test
    fun `adding video to video playlist uses video playlist repository`() = runTest {
        val video = videoFile(
            path = "/sdcard/Movies/source/fight.mp4",
            name = "fight.mp4"
        )
        val playlistId = videoPlaylistRepo.createVideoPlaylist("Training")
        val playlist = com.example.newaudio.domain.model.VideoPlaylist(
            id = playlistId,
            name = "Training"
        )

        val vm = buildViewModel(listOf(video))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onAddToVideoPlaylistConfirmed(playlist, video)
        advanceUntilIdle()

        assertEquals(playlistId, videoPlaylistRepo.addedVideo?.first)
        assertEquals(video.video, videoPlaylistRepo.addedVideo?.second)
    }

    @Test
    fun `video playlists are reflected in UI state`() = runTest {
        val playlistId = videoPlaylistRepo.createVideoPlaylist("Training")
        val vm = buildViewModel(emptyList())
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(playlistId, vm.uiState.value.videoPlaylists.single().id)
        assertEquals("Training", vm.uiState.value.videoPlaylists.single().name)
    }

    @Test
    fun `adding selected videos to video playlist uses batch repository method`() = runTest {
        val first = videoFile(
            path = "/sdcard/Movies/source/first.mp4",
            name = "first.mp4"
        )
        val second = videoFile(
            path = "/sdcard/Movies/source/second.mp4",
            name = "second.mp4"
        )
        val playlistId = videoPlaylistRepo.createVideoPlaylist("Training")
        val playlist = com.example.newaudio.domain.model.VideoPlaylist(
            id = playlistId,
            name = "Training"
        )

        val vm = buildViewModel(listOf(first, second))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onAddToVideoPlaylistMultipleConfirmed(playlist, listOf(first, second))
        advanceUntilIdle()

        assertEquals(playlistId, videoPlaylistRepo.addedVideos?.first)
        assertEquals(listOf(first.video, second.video), videoPlaylistRepo.addedVideos?.second)
    }

    @Test
    fun `creating video playlist from single add dialog adds selected video`() = runTest {
        val video = videoFile(
            path = "/sdcard/Movies/source/fight.mp4",
            name = "fight.mp4"
        )
        val vm = buildViewModel(listOf(video))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onShowAddToVideoPlaylistDialog(video)
        vm.onCreateVideoPlaylistAndAdd("Training")
        advanceUntilIdle()

        val playlist = vm.uiState.value.videoPlaylists.single()
        assertEquals("Training", playlist.name)
        assertEquals(playlist.id, videoPlaylistRepo.addedVideo?.first)
        assertEquals(video.video, videoPlaylistRepo.addedVideo?.second)
    }

    @Test
    fun `creating video playlist from multi add dialog adds selected videos`() = runTest {
        val first = videoFile(
            path = "/sdcard/Movies/source/first.mp4",
            name = "first.mp4"
        )
        val second = videoFile(
            path = "/sdcard/Movies/source/second.mp4",
            name = "second.mp4"
        )
        val vm = buildViewModel(listOf(first, second))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onToggleBrowserMode()
        advanceUntilIdle()
        vm.onItemLongClicked(first)
        vm.onItemClicked(second)
        vm.onAddToPlaylistSelected()
        vm.onCreateVideoPlaylistAndAdd("Training")
        advanceUntilIdle()

        val playlist = vm.uiState.value.videoPlaylists.single()
        assertEquals("Training", playlist.name)
        assertEquals(playlist.id, videoPlaylistRepo.addedVideos?.first)
        assertEquals(listOf(first.video, second.video), videoPlaylistRepo.addedVideos?.second)
    }

    private fun videoFile(path: String, name: String): FileItem.VideoFile {
        val video = Video(
            path = path,
            contentUri = "content://media/external/video/media/${path.hashCode().toLong()}",
            title = name.substringBeforeLast('.'),
            duration = 1_000L,
            thumbnailUri = null,
            width = 1920,
            height = 1080
        )
        return FileItem.VideoFile(
            name = name,
            path = path,
            videoId = path.hashCode().toLong(),
            video = video
        )
    }

    private fun song(path: String, title: String): Song {
        return Song(
            path = path,
            contentUri = "content://media/external/audio/media/${path.hashCode().toLong()}",
            title = title,
            artist = "Artist",
            duration = 1_000L,
            albumArtPath = null
        )
    }
}
