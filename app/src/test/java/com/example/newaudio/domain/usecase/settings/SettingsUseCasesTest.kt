package com.example.newaudio.domain.usecase.settings

import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.fake.FakeMediaRepository
import com.example.newaudio.fake.FakeMediaScannerRepository
import com.example.newaudio.fake.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUseCasesTest {

    private val repo = FakeSettingsRepository()

    // -------------------------------------------------------------------------
    // GetUserSettingsUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `GetUserSettingsUseCase returns live preferences flow`() = runTest {
        repo.setTheme(UserPreferences.Theme.LIGHT)
        val prefs = GetUserSettingsUseCase(repo)().first()
        assertEquals(UserPreferences.Theme.LIGHT, prefs.theme)
    }

    @Test
    fun `GetUserSettingsUseCase reflects subsequent changes`() = runTest {
        val uc = GetUserSettingsUseCase(repo)
        repo.setTheme(UserPreferences.Theme.DARK)
        assertEquals(UserPreferences.Theme.DARK, uc().first().theme)
        repo.setTheme(UserPreferences.Theme.SYSTEM)
        assertEquals(UserPreferences.Theme.SYSTEM, uc().first().theme)
    }

    // -------------------------------------------------------------------------
    // SetPrimaryColorUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `SetPrimaryColorUseCase stores given color`() = runTest {
        SetPrimaryColorUseCase(repo)("#FF5722")
        assertEquals("#FF5722", repo.userPreferences.first().primaryColor)
    }

    @Test
    fun `SetPrimaryColorUseCase overwrites previous color`() = runTest {
        SetPrimaryColorUseCase(repo)("#FF0000")
        SetPrimaryColorUseCase(repo)("#00FF00")
        assertEquals("#00FF00", repo.userPreferences.first().primaryColor)
    }

    // -------------------------------------------------------------------------
    // SetAutoPlayOnBluetoothUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `SetAutoPlayOnBluetoothUseCase enables autoplay on bluetooth`() = runTest {
        SetAutoPlayOnBluetoothUseCase(repo)(true)
        assertTrue(repo.userPreferences.first().isAutoPlayOnBluetooth)
    }

    @Test
    fun `SetAutoPlayOnBluetoothUseCase disables autoplay on bluetooth`() = runTest {
        SetAutoPlayOnBluetoothUseCase(repo)(true)
        SetAutoPlayOnBluetoothUseCase(repo)(false)
        assertFalse(repo.userPreferences.first().isAutoPlayOnBluetooth)
    }

    // -------------------------------------------------------------------------
    // SetOneHandedModeUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `SetOneHandedModeUseCase enables one-handed mode`() = runTest {
        SetOneHandedModeUseCase(repo)(true)
        assertTrue(repo.userPreferences.first().oneHandedMode)
    }

    @Test
    fun `SetOneHandedModeUseCase disables one-handed mode`() = runTest {
        SetOneHandedModeUseCase(repo)(true)
        SetOneHandedModeUseCase(repo)(false)
        assertFalse(repo.userPreferences.first().oneHandedMode)
    }

    // -------------------------------------------------------------------------
    // SetUseMarqueeUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `SetUseMarqueeUseCase enables marquee and keeps fields consistent`() = runTest {
        SetUseMarqueeUseCase(repo)(true)
        val prefs = repo.userPreferences.first()
        assertTrue(prefs.useMarquee)
        assertTrue(prefs.isMarqueeEnabled)
    }

    @Test
    fun `SetUseMarqueeUseCase disables marquee and keeps fields consistent`() = runTest {
        SetUseMarqueeUseCase(repo)(true)
        SetUseMarqueeUseCase(repo)(false)
        val prefs = repo.userPreferences.first()
        assertFalse(prefs.useMarquee)
        assertFalse(prefs.isMarqueeEnabled)
    }

    // -------------------------------------------------------------------------
    // SetMiniPlayerProgressBarHeightUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `SetMiniPlayerProgressBarHeightUseCase stores height`() = runTest {
        SetMiniPlayerProgressBarHeightUseCase(repo)(50f)
        assertEquals(50f, repo.userPreferences.first().miniPlayerProgressBarHeight)
    }

    @Test
    fun `SetMiniPlayerProgressBarHeightUseCase stores zero height`() = runTest {
        SetMiniPlayerProgressBarHeightUseCase(repo)(0f)
        assertEquals(0f, repo.userPreferences.first().miniPlayerProgressBarHeight)
    }

    // -------------------------------------------------------------------------
    // SetFullScreenPlayerProgressBarHeightUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `SetFullScreenPlayerProgressBarHeightUseCase stores height`() = runTest {
        SetFullScreenPlayerProgressBarHeightUseCase(repo)(75f)
        assertEquals(75f, repo.userPreferences.first().fullScreenPlayerProgressBarHeight)
    }

    @Test
    fun `SetFullScreenPlayerProgressBarHeightUseCase is independent from mini player height`() = runTest {
        SetMiniPlayerProgressBarHeightUseCase(repo)(10f)
        SetFullScreenPlayerProgressBarHeightUseCase(repo)(90f)
        val prefs = repo.userPreferences.first()
        assertEquals(10f, prefs.miniPlayerProgressBarHeight)
        assertEquals(90f, prefs.fullScreenPlayerProgressBarHeight)
    }

    // -------------------------------------------------------------------------
    // SetPlayOnFolderClickUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `SetPlayOnFolderClickUseCase enables play on folder click`() = runTest {
        SetPlayOnFolderClickUseCase(repo)(true)
        assertTrue(repo.userPreferences.first().playOnFolderClick)
    }

    @Test
    fun `SetPlayOnFolderClickUseCase disables play on folder click`() = runTest {
        SetPlayOnFolderClickUseCase(repo)(true)
        SetPlayOnFolderClickUseCase(repo)(false)
        assertFalse(repo.userPreferences.first().playOnFolderClick)
    }

    // -------------------------------------------------------------------------
    // SetShowFolderSongCountUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `SetShowFolderSongCountUseCase enables folder song count`() = runTest {
        SetShowFolderSongCountUseCase(repo)(true)
        assertTrue(repo.userPreferences.first().showFolderSongCount)
    }

    @Test
    fun `SetShowFolderSongCountUseCase disables folder song count`() = runTest {
        SetShowFolderSongCountUseCase(repo)(true)
        SetShowFolderSongCountUseCase(repo)(false)
        assertFalse(repo.userPreferences.first().showFolderSongCount)
    }

    // -------------------------------------------------------------------------
    // SetShowHiddenFilesUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `SetShowHiddenFilesUseCase enables hidden files`() = runTest {
        SetShowHiddenFilesUseCase(repo)(true)
        assertTrue(repo.userPreferences.first().showHiddenFiles)
    }

    @Test
    fun `SetShowHiddenFilesUseCase disables hidden files`() = runTest {
        SetShowHiddenFilesUseCase(repo)(true)
        SetShowHiddenFilesUseCase(repo)(false)
        assertFalse(repo.userPreferences.first().showHiddenFiles)
    }

    // -------------------------------------------------------------------------
    // SetTransparentListItemsUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `SetTransparentListItemsUseCase enables list transparency`() = runTest {
        SetTransparentListItemsUseCase(repo)(true)
        assertTrue(repo.userPreferences.first().transparentListItems)
    }

    @Test
    fun `SetTransparentListItemsUseCase disables list transparency`() = runTest {
        SetTransparentListItemsUseCase(repo)(true)
        SetTransparentListItemsUseCase(repo)(false)
        assertFalse(repo.userPreferences.first().transparentListItems)
    }

    // -------------------------------------------------------------------------
    // SetSettingsCardTransparentUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `SetSettingsCardTransparentUseCase enables card transparency`() = runTest {
        SetSettingsCardTransparentUseCase(repo)(true)
        assertTrue(repo.userPreferences.first().settingsCardTransparent)
    }

    @Test
    fun `SetSettingsCardTransparentUseCase disables card transparency`() = runTest {
        SetSettingsCardTransparentUseCase(repo)(true)
        SetSettingsCardTransparentUseCase(repo)(false)
        assertFalse(repo.userPreferences.first().settingsCardTransparent)
    }

    // -------------------------------------------------------------------------
    // SetSettingsCardBorderWidthUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `SetSettingsCardBorderWidthUseCase stores border width`() = runTest {
        SetSettingsCardBorderWidthUseCase(repo)(2f)
        assertEquals(2f, repo.userPreferences.first().settingsCardBorderWidth)
    }

    @Test
    fun `SetSettingsCardBorderWidthUseCase stores zero width`() = runTest {
        SetSettingsCardBorderWidthUseCase(repo)(2f)
        SetSettingsCardBorderWidthUseCase(repo)(0f)
        assertEquals(0f, repo.userPreferences.first().settingsCardBorderWidth)
    }

    // -------------------------------------------------------------------------
    // SetSettingsCardBorderColorUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `SetSettingsCardBorderColorUseCase stores border color`() = runTest {
        SetSettingsCardBorderColorUseCase(repo)("#FF0000")
        assertEquals("#FF0000", repo.userPreferences.first().settingsCardBorderColor)
    }

    @Test
    fun `SetSettingsCardBorderColorUseCase overwrites previous color`() = runTest {
        SetSettingsCardBorderColorUseCase(repo)("#FF0000")
        SetSettingsCardBorderColorUseCase(repo)("#0000FF")
        assertEquals("#0000FF", repo.userPreferences.first().settingsCardBorderColor)
    }

    // -------------------------------------------------------------------------
    // RestoreUserPreferencesUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `RestoreUserPreferencesUseCase restores all fields atomically`() = runTest {
        val target = UserPreferences.default().copy(
            theme = UserPreferences.Theme.LIGHT,
            primaryColor = "#AABBCC",
            backgroundTintFraction = 0.5f,
            oneHandedMode = true,
            showHiddenFiles = true
        )
        RestoreUserPreferencesUseCase(repo)(target)
        val restored = repo.userPreferences.first()
        assertEquals(UserPreferences.Theme.LIGHT, restored.theme)
        assertEquals("#AABBCC", restored.primaryColor)
        assertEquals(0.5f, restored.backgroundTintFraction)
        assertTrue(restored.oneHandedMode)
        assertTrue(restored.showHiddenFiles)
    }

    @Test
    fun `RestoreUserPreferencesUseCase overwrites existing preferences`() = runTest {
        repo.setTheme(UserPreferences.Theme.DARK)
        val target = UserPreferences.default().copy(theme = UserPreferences.Theme.LIGHT)
        RestoreUserPreferencesUseCase(repo)(target)
        assertEquals(UserPreferences.Theme.LIGHT, repo.userPreferences.first().theme)
    }

    // -------------------------------------------------------------------------
    // ResetDatabaseUseCase
    // -------------------------------------------------------------------------

    @Test
    fun `ResetDatabaseUseCase clears database and scans when folder path set`() = runTest {
        val mediaRepo = FakeMediaRepository()
        val scannerRepo = FakeMediaScannerRepository()
        repo.setMusicFolderPath("/sdcard/Music")

        ResetDatabaseUseCase(mediaRepo, repo, scannerRepo)()

        assertTrue(mediaRepo.clearDatabaseCalled)
        assertEquals("/sdcard/Music", scannerRepo.scanDirectoryCalled)
    }

    @Test
    fun `ResetDatabaseUseCase clears database but skips scan when no folder set`() = runTest {
        val mediaRepo = FakeMediaRepository()
        val scannerRepo = FakeMediaScannerRepository()
        // no music folder path (default is empty string)

        ResetDatabaseUseCase(mediaRepo, repo, scannerRepo)()

        assertTrue(mediaRepo.clearDatabaseCalled)
        assertEquals(null, scannerRepo.scanDirectoryCalled)
    }
}
