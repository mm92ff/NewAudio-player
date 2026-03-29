package com.example.newaudio.domain.repository

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface ISettingsRepository {
    val userPreferences: Flow<UserPreferences>

    suspend fun setTheme(theme: UserPreferences.Theme)
    suspend fun setPrimaryColor(color: String)
    suspend fun setMarqueeEnabled(isEnabled: Boolean)
    suspend fun setShuffleEnabled(isEnabled: Boolean)
    suspend fun setRepeatMode(repeatMode: UserPreferences.RepeatMode)
    suspend fun setOneHandedMode(isEnabled: Boolean)
    suspend fun setUseMarquee(useMarquee: Boolean)
    suspend fun setShowHiddenFiles(show: Boolean)
    suspend fun setPlayOnFolderClick(isEnabled: Boolean)
    suspend fun setShowFolderSongCount(isEnabled: Boolean)

    // Autoplay Settings
    suspend fun setAutoPlayOnBluetooth(isEnabled: Boolean)
    // NEW: Was missing before, now needed for the implementation
    suspend fun setAutoPlayOnStart(isEnabled: Boolean)

    // Musikordner Methoden
    suspend fun setMusicFolderPath(path: String)
    fun getMusicFolderPath(): Flow<String>

    // Progress bar heights
    suspend fun setMiniPlayerProgressBarHeight(height: Float)
    suspend fun setFullScreenPlayerProgressBarHeight(height: Float)

    // Background tint
    suspend fun setBackgroundTintFraction(fraction: Float)
    suspend fun setBackgroundGradientEnabled(enabled: Boolean)
    suspend fun setTransparentListItems(enabled: Boolean)
    suspend fun setSettingsCardTransparent(enabled: Boolean)
    suspend fun setSettingsCardBorderWidth(widthDp: Float)
    suspend fun setSettingsCardBorderColor(color: String)
    suspend fun restoreUserPreferences(prefs: UserPreferences)

    // NEW: Resume playback methods
    suspend fun saveLastPlayedSong(song: Song, position: Long, folderPath: String? = null)
    suspend fun getLastPlayedSong(): LastPlayedSong?
}

// Helper class for saving playback state
data class LastPlayedSong(
    val song: Song,
    val position: Long,
    val folderPath: String? = null
)