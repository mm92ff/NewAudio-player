package com.example.newaudio.feature.settings

import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.usecase.file.GetRootPathUseCase
import com.example.newaudio.domain.usecase.file.SetMusicFolderUseCase
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.domain.usecase.settings.ResetDatabaseUseCase
import com.example.newaudio.domain.usecase.settings.RestoreUserPreferencesUseCase
import com.example.newaudio.domain.usecase.settings.SetAutoPlayOnBluetoothUseCase
import com.example.newaudio.domain.usecase.settings.SetBackgroundGradientEnabledUseCase
import com.example.newaudio.domain.usecase.settings.SetBackgroundTintFractionUseCase
import com.example.newaudio.domain.usecase.settings.SetFullScreenPlayerProgressBarHeightUseCase
import com.example.newaudio.domain.usecase.settings.SetMiniPlayerProgressBarHeightUseCase
import com.example.newaudio.domain.usecase.settings.SetOneHandedModeUseCase
import com.example.newaudio.domain.usecase.settings.SetPlayOnFolderClickUseCase
import com.example.newaudio.domain.usecase.settings.SetPrimaryColorUseCase
import com.example.newaudio.domain.usecase.settings.SetShowFolderSongCountUseCase
import com.example.newaudio.domain.usecase.settings.SetShowHiddenFilesUseCase
import com.example.newaudio.domain.usecase.settings.SetThemeUseCase
import com.example.newaudio.domain.usecase.settings.SetTransparentListItemsUseCase
import com.example.newaudio.domain.usecase.settings.SetUseMarqueeUseCase
import com.example.newaudio.fake.FakeErrorRepository
import com.example.newaudio.fake.FakeMediaRepository
import com.example.newaudio.fake.FakeMediaScannerRepository
import com.example.newaudio.fake.FakePlaylistRepository
import com.example.newaudio.fake.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupExportImportTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settingsRepo = FakeSettingsRepository()
    private val mediaRepo = FakeMediaRepository()
    private val scannerRepo = FakeMediaScannerRepository()
    private val errorRepo = FakeErrorRepository()
    private val playlistRepo = FakePlaylistRepository()

    private fun buildViewModel() = SettingsViewModel(
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

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `export passes current settings to repository`() = runTest {
        settingsRepo.setTheme(UserPreferences.Theme.LIGHT)
        settingsRepo.setPrimaryColor("#00FF00")
        val vm = buildViewModel()
        backgroundScope.launch { vm.settingsState.collect {} }
        advanceUntilIdle()

        vm.onExportPlaylists("file://test.json")
        advanceUntilIdle()

        assertNotNull(playlistRepo.exportedPreferences)
        assertEquals(UserPreferences.Theme.LIGHT, playlistRepo.exportedPreferences?.theme)
        assertEquals("#00FF00", playlistRepo.exportedPreferences?.primaryColor)
    }

    @Test
    fun `import restores settings when backup contains settings`() = runTest {
        val backedUpPrefs = UserPreferences.default().copy(
            theme = UserPreferences.Theme.LIGHT,
            primaryColor = "#123456",
            backgroundTintFraction = 0.15f
        )
        playlistRepo.importReturnPreferences = backedUpPrefs
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onImportPlaylists("file://test.json")
        advanceUntilIdle()

        val restored = settingsRepo.userPreferences.first()
        assertEquals(UserPreferences.Theme.LIGHT, restored.theme)
        assertEquals("#123456", restored.primaryColor)
        assertEquals(0.15f, restored.backgroundTintFraction)
    }

    @Test
    fun `import does not crash when backup has no settings (old format)`() = runTest {
        playlistRepo.importReturnPreferences = null
        val originalPrefs = settingsRepo.userPreferences.first()
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onImportPlaylists("file://old_format.json")
        advanceUntilIdle()

        // Settings unchanged — no restore without settings in the backup
        val afterImport = settingsRepo.userPreferences.first()
        assertEquals(originalPrefs.theme, afterImport.theme)
        assertEquals(originalPrefs.primaryColor, afterImport.primaryColor)
    }
}
