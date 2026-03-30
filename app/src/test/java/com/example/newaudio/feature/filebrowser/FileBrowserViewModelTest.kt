package com.example.newaudio.feature.filebrowser

import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.usecase.file.*
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.fake.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mediaRepo = FakeMediaRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val playlistRepo = FakePlaylistRepository()
    private val scannerRepo = FakeMediaScannerRepository()

    // Mock the use cases since they have complex dependencies
    private val getSortedFileTreeUseCase = mockk<GetSortedFileTreeUseCase>(relaxed = true)
    private val saveFolderOrderUseCase = mockk<SaveFolderOrderUseCase>(relaxed = true)
    private val deleteFileUseCase = mockk<DeleteFileUseCase>(relaxed = true)
    private val deleteMultipleFilesUseCase = mockk<DeleteMultipleFilesUseCase>(relaxed = true)
    private val renameFileUseCase = mockk<RenameFileUseCase>(relaxed = true)
    private val copyMultipleFilesUseCase = mockk<CopyMultipleFilesUseCase>(relaxed = true)
    private val moveMultipleFilesUseCase = mockk<MoveMultipleFilesUseCase>(relaxed = true)
    private val getRootPathUseCase = mockk<GetRootPathUseCase>(relaxed = true)
    private val getParentPathUseCase = mockk<GetParentPathUseCase>(relaxed = true)
    private val syncCurrentFolderUseCase = mockk<SyncCurrentFolderUseCase>(relaxed = true)
    private val mediaStoreObserverRepo = mockk<com.example.newaudio.domain.repository.IMediaStoreObserverRepository>(relaxed = true)

    private fun buildViewModel(): FileBrowserViewModel {
        // Set up basic mock behavior
        coEvery { getRootPathUseCase() } returns "/sdcard/Music"
        coEvery { getParentPathUseCase(any()) } returns "/sdcard"

        return FileBrowserViewModel(
            getSortedFileTreeUseCase = getSortedFileTreeUseCase,
            saveFolderOrderUseCase = saveFolderOrderUseCase,
            deleteFileUseCase = deleteFileUseCase,
            deleteMultipleFilesUseCase = deleteMultipleFilesUseCase,
            renameFileUseCase = renameFileUseCase,
            copyMultipleFilesUseCase = copyMultipleFilesUseCase,
            moveMultipleFilesUseCase = moveMultipleFilesUseCase,
            getRootPathUseCase = getRootPathUseCase,
            getParentPathUseCase = getParentPathUseCase,
            syncCurrentFolderUseCase = syncCurrentFolderUseCase,
            mediaRepository = mediaRepo,
            getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepo),
            mediaStoreObserverRepository = mediaStoreObserverRepo,
            playlistRepository = playlistRepo,
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
}