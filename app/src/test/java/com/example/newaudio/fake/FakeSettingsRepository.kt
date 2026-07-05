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
    private val _videoFolderPath = MutableStateFlow("")

    var savedLastPlayedSong: LastPlayedSong? = null
    var setThemeCalled: UserPreferences.Theme? = null

    override suspend fun setTheme(theme: UserPreferences.Theme) {
        setThemeCalled = theme
        _prefs.value = _prefs.value.copy(theme = theme)
    }

    override suspend fun setPrimaryColor(color: String) {
        _prefs.value = _prefs.value.copy(primaryColor = color)
    }

    override suspend fun setMarqueeEnabled(isEnabled: Boolean) {
        _prefs.value = _prefs.value.copy(isMarqueeEnabled = isEnabled)
    }

    override suspend fun setShuffleEnabled(isEnabled: Boolean) {
        _prefs.value = _prefs.value.copy(isShuffleEnabled = isEnabled)
    }

    override suspend fun setRepeatMode(repeatMode: UserPreferences.RepeatMode) {
        _prefs.value = _prefs.value.copy(repeatMode = repeatMode)
    }

    override suspend fun setOneHandedMode(isEnabled: Boolean) {
        _prefs.value = _prefs.value.copy(oneHandedMode = isEnabled)
    }

    override suspend fun setUseMarquee(useMarquee: Boolean) {
        _prefs.value = _prefs.value.copy(useMarquee = useMarquee, isMarqueeEnabled = useMarquee)
    }

    override suspend fun setShowHiddenFiles(show: Boolean) {
        _prefs.value = _prefs.value.copy(showHiddenFiles = show)
    }

    override suspend fun setPlayOnFolderClick(isEnabled: Boolean) {
        _prefs.value = _prefs.value.copy(playOnFolderClick = isEnabled)
    }

    override suspend fun setResumeSessionOnModeSwitch(isEnabled: Boolean) {
        _prefs.value = _prefs.value.copy(resumeSessionOnModeSwitch = isEnabled)
    }

    override suspend fun setShowVideoPreviewItems(isEnabled: Boolean) {
        _prefs.value = _prefs.value.copy(showVideoPreviewItems = isEnabled)
    }

    override suspend fun setVideoDisplayMode(mode: UserPreferences.VideoDisplayMode) {
        _prefs.value = _prefs.value.copy(
            videoDisplayMode = mode,
            showVideoPreviewItems = mode == UserPreferences.VideoDisplayMode.PREVIEW_LIST
        )
    }

    override suspend fun setVideoGalleryColumns(columns: Int) {
        _prefs.value = _prefs.value.copy(videoGalleryColumns = columns.coerceIn(2, 4))
    }

    override suspend fun setShowVideoNamesInGallery(isEnabled: Boolean) {
        _prefs.value = _prefs.value.copy(showVideoNamesInGallery = isEnabled)
    }

    override suspend fun setVideoMarkersEnabled(isEnabled: Boolean) {
        _prefs.value = _prefs.value.copy(videoMarkersEnabled = isEnabled)
    }

    override suspend fun setShowFolderSongCount(isEnabled: Boolean) {
        _prefs.value = _prefs.value.copy(showFolderSongCount = isEnabled)
    }

    override suspend fun setAutoPlayOnBluetooth(isEnabled: Boolean) {
        _prefs.value = _prefs.value.copy(isAutoPlayOnBluetooth = isEnabled)
    }

    override suspend fun setAutoPlayOnStart(isEnabled: Boolean) {
        _prefs.value = _prefs.value.copy(isAutoPlayOnStart = isEnabled)
    }

    override suspend fun setMusicFolderPath(path: String) {
        _musicFolderPath.value = path
        _prefs.value = _prefs.value.copy(musicFolderPath = path)
    }

    override fun getMusicFolderPath(): Flow<String> = _musicFolderPath.asStateFlow()

    override suspend fun setVideoFolderPath(path: String) {
        _videoFolderPath.value = path
        _prefs.value = _prefs.value.copy(videoFolderPath = path)
    }

    override fun getVideoFolderPath(): Flow<String> = _videoFolderPath.asStateFlow()

    override suspend fun setMiniPlayerProgressBarHeight(height: Float) {
        _prefs.value = _prefs.value.copy(miniPlayerProgressBarHeight = height)
    }

    override suspend fun setFullScreenPlayerProgressBarHeight(height: Float) {
        _prefs.value = _prefs.value.copy(fullScreenPlayerProgressBarHeight = height)
    }

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
