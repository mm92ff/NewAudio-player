package com.example.newaudio.feature.settings

import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.usecase.file.GetRootPathUseCase
import com.example.newaudio.domain.usecase.file.SetMusicFolderUseCase
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.domain.usecase.settings.ResetDatabaseUseCase
import com.example.newaudio.domain.usecase.settings.SetAutoPlayOnBluetoothUseCase
import com.example.newaudio.domain.usecase.settings.SetFullScreenPlayerProgressBarHeightUseCase
import com.example.newaudio.domain.usecase.settings.SetMiniPlayerProgressBarHeightUseCase
import com.example.newaudio.domain.usecase.settings.SetOneHandedModeUseCase
import com.example.newaudio.domain.usecase.settings.SetPlayOnFolderClickUseCase
import com.example.newaudio.domain.usecase.settings.SetPrimaryColorUseCase
import com.example.newaudio.domain.usecase.settings.SetBackgroundGradientEnabledUseCase
import com.example.newaudio.domain.usecase.settings.SetTransparentListItemsUseCase
import com.example.newaudio.domain.usecase.settings.RestoreUserPreferencesUseCase
import com.example.newaudio.domain.usecase.settings.SetBackgroundTintFractionUseCase
import com.example.newaudio.domain.usecase.settings.SetShowFolderSongCountUseCase
import com.example.newaudio.domain.usecase.settings.SetShowHiddenFilesUseCase
import com.example.newaudio.domain.usecase.settings.SetThemeUseCase
import com.example.newaudio.domain.usecase.settings.SetUseMarqueeUseCase
import com.example.newaudio.fake.FakeErrorRepository
import com.example.newaudio.fake.FakeMediaRepository
import com.example.newaudio.fake.FakeMediaScannerRepository
import com.example.newaudio.fake.FakePlaylistRepository
import com.example.newaudio.fake.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settingsRepo = FakeSettingsRepository()
    private val mediaRepo = FakeMediaRepository()
    private val scannerRepo = FakeMediaScannerRepository()
    private val errorRepo = FakeErrorRepository()
    private val playlistRepo = FakePlaylistRepository()

    private fun buildViewModel(): SettingsViewModel = SettingsViewModel(
        getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepo),
        setThemeUseCase = SetThemeUseCase(settingsRepo),
        setPrimaryColorUseCase = SetPrimaryColorUseCase(settingsRepo),
        setMusicFolderUseCase = SetMusicFolderUseCase(settingsRepo, scannerRepo),
        setMiniPlayerProgressBarHeightUseCase = SetMiniPlayerProgressBarHeightUseCase(settingsRepo),
        setFullScreenPlayerProgressBarHeightUseCase = SetFullScreenPlayerProgressBarHeightUseCase(settingsRepo),
        setAutoPlayOnBluetoothUseCase = SetAutoPlayOnBluetoothUseCase(settingsRepo),
        setOneHandedModeUseCase = SetOneHandedModeUseCase(settingsRepo),
        setUseMarqueeUseCase = SetUseMarqueeUseCase(settingsRepo),
        setShowHiddenFilesUseCase = SetShowHiddenFilesUseCase(settingsRepo),
        setPlayOnFolderClickUseCase = SetPlayOnFolderClickUseCase(settingsRepo),
        setShowFolderSongCountUseCase = SetShowFolderSongCountUseCase(settingsRepo),
        setBackgroundTintFractionUseCase = SetBackgroundTintFractionUseCase(settingsRepo),
        setBackgroundGradientEnabledUseCase = SetBackgroundGradientEnabledUseCase(settingsRepo),
        setTransparentListItemsUseCase = SetTransparentListItemsUseCase(settingsRepo),
        restoreUserPreferencesUseCase = RestoreUserPreferencesUseCase(settingsRepo),
        resetDatabaseUseCase = ResetDatabaseUseCase(mediaRepo, settingsRepo, scannerRepo),
        errorRepository = errorRepo,
        playlistRepository = playlistRepo,
        ioDispatcher = testDispatcher
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
    fun `onThemeChange updates theme in repository`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onThemeChange(UserPreferences.Theme.LIGHT)
        advanceUntilIdle()
        assertEquals(UserPreferences.Theme.LIGHT, settingsRepo.setThemeCalled)
    }

    @Test
    fun `onResetDatabaseClicked shows dialog`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        assertFalse(vm.showResetDialog.value)
        vm.onResetDatabaseClicked()
        assertTrue(vm.showResetDialog.value)
    }

    @Test
    fun `onDismissResetDialog hides dialog`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onResetDatabaseClicked()
        vm.onDismissResetDialog()
        assertFalse(vm.showResetDialog.value)
    }

    @Test
    fun `onConfirmResetDatabase clears database and hides dialog`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onResetDatabaseClicked()
        vm.onConfirmResetDatabase()
        advanceUntilIdle()
        assertFalse(vm.showResetDialog.value)
        assertTrue(mediaRepo.clearDatabaseCalled)
    }

    @Test
    fun `onMusicFolderChange triggers scan when path is non-empty`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onMusicFolderChange("/sdcard/Music")
        advanceUntilIdle()
        assertEquals("/sdcard/Music", scannerRepo.scanDirectoryCalled)
    }

    @Test
    fun `onMusicFolderChange does not trigger scan for empty path`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onMusicFolderChange("")
        advanceUntilIdle()
        assertNull(scannerRepo.scanDirectoryCalled)
    }

    @Test
    fun `onBackgroundTintFractionChange updates repository`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onBackgroundTintFractionChange(0.15f)
        advanceUntilIdle()
        assertEquals(0.15f, settingsRepo.userPreferences.first().backgroundTintFraction)
    }

    @Test
    fun `onBackgroundGradientEnabledChange enables gradient in repository`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onBackgroundGradientEnabledChange(true)
        advanceUntilIdle()
        assertTrue(settingsRepo.userPreferences.first().backgroundGradientEnabled)
    }

    @Test
    fun `onBackgroundGradientEnabledChange disables gradient in repository`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onBackgroundGradientEnabledChange(true)
        advanceUntilIdle()
        vm.onBackgroundGradientEnabledChange(false)
        advanceUntilIdle()
        assertFalse(settingsRepo.userPreferences.first().backgroundGradientEnabled)
    }

    @Test
    fun `onTransparentListItemsChange enables transparent list items`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onTransparentListItemsChange(true)
        advanceUntilIdle()
        assertTrue(settingsRepo.userPreferences.first().transparentListItems)
    }

    @Test
    fun `onTransparentListItemsChange disables transparent list items`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onTransparentListItemsChange(true)
        advanceUntilIdle()
        vm.onTransparentListItemsChange(false)
        advanceUntilIdle()
        assertFalse(settingsRepo.userPreferences.first().transparentListItems)
    }
}
