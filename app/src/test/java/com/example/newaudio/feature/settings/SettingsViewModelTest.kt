package com.example.newaudio.feature.settings

import com.example.newaudio.R
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.usecase.file.SetMusicFolderUseCase
import com.example.newaudio.domain.usecase.file.SetVideoFolderUseCase
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.domain.usecase.settings.ResetDatabaseUseCase
import com.example.newaudio.domain.usecase.settings.SetAutoPlayOnBluetoothUseCase
import com.example.newaudio.domain.usecase.settings.SetFullScreenPlayerProgressBarHeightUseCase
import com.example.newaudio.domain.usecase.settings.SetMiniPlayerProgressBarHeightUseCase
import com.example.newaudio.domain.usecase.settings.SetOneHandedModeUseCase
import com.example.newaudio.domain.usecase.settings.SetPlayOnFolderClickUseCase
import com.example.newaudio.domain.usecase.settings.SetPrimaryColorUseCase
import com.example.newaudio.domain.usecase.settings.SetResumeSessionOnModeSwitchUseCase
import com.example.newaudio.domain.usecase.settings.SetShowVideoNamesInGalleryUseCase
import com.example.newaudio.domain.usecase.settings.SetShowVideoPreviewItemsUseCase
import com.example.newaudio.domain.usecase.settings.SetVideoDisplayModeUseCase
import com.example.newaudio.domain.usecase.settings.SetVideoGalleryColumnsUseCase
import com.example.newaudio.domain.usecase.settings.SetVideoMarkersEnabledUseCase
import com.example.newaudio.domain.usecase.settings.SetBackgroundGradientEnabledUseCase
import com.example.newaudio.domain.usecase.settings.SetBackgroundGradientDirectionUseCase
import com.example.newaudio.domain.usecase.settings.SetTransparentListItemsUseCase
import com.example.newaudio.domain.usecase.settings.RestoreUserPreferencesUseCase
import com.example.newaudio.domain.usecase.settings.SetBackgroundTintFractionUseCase
import com.example.newaudio.domain.usecase.settings.SetShowFolderSongCountUseCase
import com.example.newaudio.domain.usecase.settings.SetShowHiddenFilesUseCase
import com.example.newaudio.domain.usecase.settings.SetThemeUseCase
import com.example.newaudio.domain.usecase.settings.SetUseMarqueeUseCase
import com.example.newaudio.domain.usecase.settings.SetSettingsCardTransparentUseCase
import com.example.newaudio.domain.usecase.settings.SetSettingsCardBorderWidthUseCase
import com.example.newaudio.domain.usecase.settings.SetSettingsCardBorderColorUseCase
import com.example.newaudio.fake.FakeErrorRepository
import com.example.newaudio.fake.FakeMediaRepository
import com.example.newaudio.fake.FakeMediaScannerRepository
import com.example.newaudio.fake.FakePlaylistBackupRepository
import com.example.newaudio.fake.FakeSettingsRepository
import com.example.newaudio.util.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    private val playlistRepo = FakePlaylistBackupRepository()

    private fun buildViewModel(): SettingsViewModel = SettingsViewModel(
        getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepo),
        setThemeUseCase = SetThemeUseCase(settingsRepo),
        setPrimaryColorUseCase = SetPrimaryColorUseCase(settingsRepo),
        setMusicFolderUseCase = SetMusicFolderUseCase(settingsRepo, scannerRepo),
        setVideoFolderUseCase = SetVideoFolderUseCase(settingsRepo, scannerRepo),
        setMiniPlayerProgressBarHeightUseCase = SetMiniPlayerProgressBarHeightUseCase(settingsRepo),
        setFullScreenPlayerProgressBarHeightUseCase = SetFullScreenPlayerProgressBarHeightUseCase(settingsRepo),
        setAutoPlayOnBluetoothUseCase = SetAutoPlayOnBluetoothUseCase(settingsRepo),
        setOneHandedModeUseCase = SetOneHandedModeUseCase(settingsRepo),
        setUseMarqueeUseCase = SetUseMarqueeUseCase(settingsRepo),
        setShowHiddenFilesUseCase = SetShowHiddenFilesUseCase(settingsRepo),
        setPlayOnFolderClickUseCase = SetPlayOnFolderClickUseCase(settingsRepo),
        setResumeSessionOnModeSwitchUseCase = SetResumeSessionOnModeSwitchUseCase(settingsRepo),
        setShowVideoPreviewItemsUseCase = SetShowVideoPreviewItemsUseCase(settingsRepo),
        setShowVideoNamesInGalleryUseCase = SetShowVideoNamesInGalleryUseCase(settingsRepo),
        setVideoMarkersEnabledUseCase = SetVideoMarkersEnabledUseCase(settingsRepo),
        setVideoDisplayModeUseCase = SetVideoDisplayModeUseCase(settingsRepo),
        setVideoGalleryColumnsUseCase = SetVideoGalleryColumnsUseCase(settingsRepo),
        setShowFolderSongCountUseCase = SetShowFolderSongCountUseCase(settingsRepo),
        setBackgroundTintFractionUseCase = SetBackgroundTintFractionUseCase(settingsRepo),
        setBackgroundGradientEnabledUseCase = SetBackgroundGradientEnabledUseCase(settingsRepo),
        setBackgroundGradientDirectionUseCase = SetBackgroundGradientDirectionUseCase(settingsRepo),
        setTransparentListItemsUseCase = SetTransparentListItemsUseCase(settingsRepo),
        setSettingsCardTransparentUseCase = SetSettingsCardTransparentUseCase(settingsRepo),
        setSettingsCardBorderWidthUseCase = SetSettingsCardBorderWidthUseCase(settingsRepo),
        setSettingsCardBorderColorUseCase = SetSettingsCardBorderColorUseCase(settingsRepo),
        restoreUserPreferencesUseCase = RestoreUserPreferencesUseCase(settingsRepo),
        resetDatabaseUseCase = ResetDatabaseUseCase(mediaRepo, settingsRepo, scannerRepo),
        errorRepository = errorRepo,
        playlistBackupRepository = playlistRepo,
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
    fun `onBackgroundGradientDirectionChange stores every direction`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        UserPreferences.GradientDirection.entries.forEach { direction ->
            vm.onBackgroundGradientDirectionChange(direction)
            advanceUntilIdle()
            assertEquals(direction, settingsRepo.userPreferences.first().backgroundGradientDirection)
        }
    }

    @Test
    fun `rapid gradient direction changes deterministically persist the last value`() = runTest {
        val firstWriteGate = CompletableDeferred<Unit>()
        settingsRepo.backgroundGradientDirectionGate = firstWriteGate
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onBackgroundGradientDirectionChange(UserPreferences.GradientDirection.BOTTOM_TO_TOP)
        runCurrent()
        vm.onBackgroundGradientDirectionChange(UserPreferences.GradientDirection.LEFT_TO_RIGHT)
        vm.onBackgroundGradientDirectionChange(UserPreferences.GradientDirection.RIGHT_TO_LEFT)
        vm.onBackgroundGradientDirectionChange(
            UserPreferences.GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT
        )
        firstWriteGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            UserPreferences.GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT,
            settingsRepo.userPreferences.first().backgroundGradientDirection
        )
    }

    @Test
    fun `gradient direction change is reflected by settings state`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch { vm.settingsState.collect {} }
        advanceUntilIdle()

        vm.onBackgroundGradientDirectionChange(
            UserPreferences.GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT
        )
        advanceUntilIdle()

        assertEquals(
            UserPreferences.GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT,
            vm.settingsState.value.backgroundGradientDirection
        )
    }

    @Test
    fun `gradient direction repository failure is logged and reported`() = runTest {
        settingsRepo.backgroundGradientDirectionError =
            IllegalStateException("DataStore unavailable")
        val vm = buildViewModel()
        val event = async { vm.events.first() }
        advanceUntilIdle()

        vm.onBackgroundGradientDirectionChange(UserPreferences.GradientDirection.LEFT_TO_RIGHT)
        advanceUntilIdle()

        val message = (event.await() as SettingsEvent.ShowMessage).text
        assertTrue(message is UiText.StringResource)
        assertEquals(R.string.unknown_error, (message as UiText.StringResource).resId)
        assertTrue(errorRepo.logs.any { it.message == "DataStore unavailable" })
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

    @Test
    fun `onShowVideoPreviewItemsChange enables video preview items`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onShowVideoPreviewItemsChange(true)
        advanceUntilIdle()
        assertTrue(settingsRepo.userPreferences.first().showVideoPreviewItems)
    }

    @Test
    fun `onShowVideoPreviewItemsChange disables video preview items`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onShowVideoPreviewItemsChange(true)
        advanceUntilIdle()
        vm.onShowVideoPreviewItemsChange(false)
        advanceUntilIdle()
        assertFalse(settingsRepo.userPreferences.first().showVideoPreviewItems)
    }

    @Test
    fun `onVideoDisplayModeChange stores square gallery mode`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onVideoDisplayModeChange(UserPreferences.VideoDisplayMode.GALLERY_SQUARE)
        advanceUntilIdle()
        assertEquals(
            UserPreferences.VideoDisplayMode.GALLERY_SQUARE,
            settingsRepo.userPreferences.first().videoDisplayMode
        )
    }

    @Test
    fun `onVideoDisplayModeChange stores adaptive gallery mode`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onVideoDisplayModeChange(UserPreferences.VideoDisplayMode.GALLERY_ADAPTIVE)
        advanceUntilIdle()
        assertEquals(
            UserPreferences.VideoDisplayMode.GALLERY_ADAPTIVE,
            settingsRepo.userPreferences.first().videoDisplayMode
        )
    }

    @Test
    fun `onVideoDisplayModeChange stores filled gallery mode`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onVideoDisplayModeChange(UserPreferences.VideoDisplayMode.GALLERY_FILLED)
        advanceUntilIdle()
        assertEquals(
            UserPreferences.VideoDisplayMode.GALLERY_FILLED,
            settingsRepo.userPreferences.first().videoDisplayMode
        )
    }

    @Test
    fun `onVideoGalleryColumnsChange stores selected column count`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onVideoGalleryColumnsChange(4)
        advanceUntilIdle()
        assertEquals(4, settingsRepo.userPreferences.first().videoGalleryColumns)
    }

    @Test
    fun `onShowVideoNamesInGalleryChange stores selected state`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onShowVideoNamesInGalleryChange(true)
        advanceUntilIdle()
        assertTrue(settingsRepo.userPreferences.first().showVideoNamesInGallery)

        vm.onShowVideoNamesInGalleryChange(false)
        advanceUntilIdle()
        assertFalse(settingsRepo.userPreferences.first().showVideoNamesInGallery)
    }

    @Test
    fun `onVideoMarkersEnabledChange stores selected state`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onVideoMarkersEnabledChange(true)
        advanceUntilIdle()
        assertTrue(settingsRepo.userPreferences.first().videoMarkersEnabled)

        vm.onVideoMarkersEnabledChange(false)
        advanceUntilIdle()
        assertFalse(settingsRepo.userPreferences.first().videoMarkersEnabled)
    }
}
