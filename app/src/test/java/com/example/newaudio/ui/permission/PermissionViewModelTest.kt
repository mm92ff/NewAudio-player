package com.example.newaudio.ui.permission

import com.example.newaudio.domain.usecase.file.SetMusicFolderUseCase
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.fake.FakeMediaScannerRepository
import com.example.newaudio.fake.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
class PermissionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settingsRepo = FakeSettingsRepository()
    private val scannerRepo = FakeMediaScannerRepository()

    private fun buildViewModel(): PermissionViewModel = PermissionViewModel(
        getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepo),
        setMusicFolderUseCase = SetMusicFolderUseCase(settingsRepo, scannerRepo)
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
    fun `initial state shows loading then success with music folder unset`() = runTest {
        val vm = buildViewModel()

        // Initially loading
        assertEquals(PermissionUiState.Loading, vm.uiState.value)

        advanceUntilIdle()

        // Should show success with isMusicFolderSet = false (empty path)
        val finalState = vm.uiState.value
        assertTrue(finalState is PermissionUiState.Success)
        assertFalse((finalState as PermissionUiState.Success).isMusicFolderSet)
    }

    @Test
    fun `initial state shows music folder as set when path is not blank`() = runTest {
        // Pre-set a music folder path
        settingsRepo.setMusicFolderPath("/sdcard/Music")

        val vm = buildViewModel()
        advanceUntilIdle()

        val finalState = vm.uiState.value
        assertTrue(finalState is PermissionUiState.Success)
        assertTrue((finalState as PermissionUiState.Success).isMusicFolderSet)
    }

    @Test
    fun `onMusicFolderSelected sets folder path and triggers scan`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        val testPath = "/sdcard/Music/Downloads"
        vm.onMusicFolderSelected(testPath)
        advanceUntilIdle()

        // Should trigger scan
        assertEquals(testPath, scannerRepo.scanDirectoryCalled)
    }

    @Test
    fun `onMusicFolderSelected completes successfully`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        val testPath = "/test/path"
        vm.onMusicFolderSelected(testPath)
        advanceUntilIdle()

        // Should complete successfully and update state
        val finalState = vm.uiState.value
        assertTrue(finalState is PermissionUiState.Success)
        assertTrue((finalState as PermissionUiState.Success).isMusicFolderSet)

        // Should trigger scan
        assertEquals(testPath, scannerRepo.scanDirectoryCalled)
    }
}