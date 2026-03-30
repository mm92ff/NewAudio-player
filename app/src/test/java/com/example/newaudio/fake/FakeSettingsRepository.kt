package com.example.newaudio.fake

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.ISettingsRepository
import com.example.newaudio.domain.repository.LastPlayedSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSettingsRepository : ISettingsRepository {

    private val _prefs = MutableStateFlow(UserPreferences.default())
    override val userPreferences: Flow<UserPreferences> = _prefs.asStateFlow()

    private val _musicFolderPath = MutableStateFlow("")

    var savedLastPlayedSong: LastPlayedSong? = null
    var setThemeCalled: UserPreferences.Theme? = null

    override suspend fun setTheme(theme: UserPreferences.Theme) {
        setThemeCalled = theme
        _prefs.value = _prefs.value.copy(theme = theme)
    }

    override suspend fun setPrimaryColor(color: String) {
        _prefs.value = _prefs.value.copy(primaryColor = color)
    }

    override suspend fun setMarqueeEnabled(isEnabled: Boolean) {}

    override suspend fun setShuffleEnabled(isEnabled: Boolean) {
        _prefs.value = _prefs.value.copy(isShuffleEnabled = isEnabled)
    }

    override suspend fun setRepeatMode(repeatMode: UserPreferences.RepeatMode) {
        _prefs.value = _prefs.value.copy(repeatMode = repeatMode)
    }

    override suspend fun setOneHandedMode(isEnabled: Boolean) {}

    override suspend fun setUseMarquee(useMarquee: Boolean) {}

    override suspend fun setShowHiddenFiles(show: Boolean) {}

    override suspend fun setPlayOnFolderClick(isEnabled: Boolean) {}

    override suspend fun setShowFolderSongCount(isEnabled: Boolean) {}

    override suspend fun setAutoPlayOnBluetooth(isEnabled: Boolean) {}

    override suspend fun setAutoPlayOnStart(isEnabled: Boolean) {}

    override suspend fun setMusicFolderPath(path: String) {
        _musicFolderPath.value = path
        _prefs.value = _prefs.value.copy(musicFolderPath = path)
    }

    override fun getMusicFolderPath(): Flow<String> = _musicFolderPath.asStateFlow()

    override suspend fun setMiniPlayerProgressBarHeight(height: Float) {}

    override suspend fun setFullScreenPlayerProgressBarHeight(height: Float) {}

    override suspend fun setBackgroundTintFraction(fraction: Float) {
        _prefs.value = _prefs.value.copy(backgroundTintFraction = fraction)
    }

    override suspend fun setBackgroundGradientEnabled(enabled: Boolean) {
        _prefs.value = _prefs.value.copy(backgroundGradientEnabled = enabled)
    }

    override suspend fun setTransparentListItems(enabled: Boolean) {
        _prefs.value = _prefs.value.copy(transparentListItems = enabled)
    }

    override suspend fun setSettingsCardTransparent(enabled: Boolean) {
        _prefs.value = _prefs.value.copy(settingsCardTransparent = enabled)
    }

    override suspend fun setSettingsCardBorderWidth(widthDp: Float) {
        _prefs.value = _prefs.value.copy(settingsCardBorderWidth = widthDp)
    }

    override suspend fun setSettingsCardBorderColor(color: String) {
        _prefs.value = _prefs.value.copy(settingsCardBorderColor = color)
    }

    override suspend fun restoreUserPreferences(prefs: UserPreferences) {
        _prefs.value = prefs
    }

    override suspend fun saveLastPlayedSong(song: Song, position: Long, folderPath: String?) {
        savedLastPlayedSong = LastPlayedSong(song, position, folderPath)
    }

    override suspend fun getLastPlayedSong() = savedLastPlayedSong
}
