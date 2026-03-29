package com.example.newaudio.data.repository

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.ISettingsRepository
import com.example.newaudio.domain.repository.LastPlayedSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ISettingsRepository {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val PRIMARY_COLOR = stringPreferencesKey("primary_color")

        // Fix: Now only ONE source of truth for marquee
        val IS_MARQUEE_ENABLED = booleanPreferencesKey("is_marquee_enabled")
        // val USE_MARQUEE = booleanPreferencesKey("use_marquee") // Removed, was redundant

        val IS_SHUFFLE_ENABLED = booleanPreferencesKey("is_shuffle_enabled")

        // Fix: Added key for AutoPlay on Start
        val IS_AUTOPLAY_ON_START = booleanPreferencesKey("is_autoplay_on_start")
        val IS_AUTOPLAY_ON_BLUETOOTH = booleanPreferencesKey("is_autoplay_on_bluetooth")

        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val MUSIC_FOLDER_PATH = stringPreferencesKey("music_folder_path")
        val MINI_PLAYER_PROGRESS_BAR_HEIGHT = floatPreferencesKey("mini_player_progress_bar_height")
        val FULL_SCREEN_PLAYER_PROGRESS_BAR_HEIGHT = floatPreferencesKey("full_screen_player_progress_bar_height")
        val ONE_HANDED_MODE = booleanPreferencesKey("one_handed_mode")
        val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val PLAY_ON_FOLDER_CLICK = booleanPreferencesKey("play_on_folder_click")
        val SHOW_FOLDER_SONG_COUNT = booleanPreferencesKey("show_folder_song_count")
        val BACKGROUND_TINT_FRACTION = floatPreferencesKey("background_tint_fraction")
        val BACKGROUND_GRADIENT_ENABLED = booleanPreferencesKey("background_gradient_enabled")
        val TRANSPARENT_LIST_ITEMS = booleanPreferencesKey("transparent_list_items")
        val SETTINGS_CARD_TRANSPARENT = booleanPreferencesKey("settings_card_transparent")
        val SETTINGS_CARD_BORDER_WIDTH = floatPreferencesKey("settings_card_border_width")
        val SETTINGS_CARD_BORDER_COLOR = stringPreferencesKey("settings_card_border_color")

        // Keys for Resume Playback
        val LAST_SONG_PATH = stringPreferencesKey("last_song_path")
        val LAST_FOLDER_PATH = stringPreferencesKey("last_folder_path")

        // Fix: Save content URI for more robust playback on Android 10+
        val LAST_SONG_CONTENT_URI = stringPreferencesKey("last_song_content_uri")

        val LAST_SONG_TITLE = stringPreferencesKey("last_song_title")
        val LAST_SONG_ARTIST = stringPreferencesKey("last_song_artist")
        val LAST_SONG_DURATION = longPreferencesKey("last_song_duration")
        val LAST_POSITION = longPreferencesKey("last_position")
        val LAST_SONG_ALBUM_ART = stringPreferencesKey("last_song_album_art")
    }

    override val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val defaultValues = UserPreferences.default()

            val theme = safeEnumValueOf(
                preferences[Keys.THEME],
                defaultValues.theme
            )

            val repeatMode = safeEnumValueOf(
                preferences[Keys.REPEAT_MODE],
                defaultValues.repeatMode
            )

            // Fix: Unified value for marquee
            val marqueeEnabled = preferences[Keys.IS_MARQUEE_ENABLED] ?: defaultValues.isMarqueeEnabled

            UserPreferences(
                theme = theme,
                primaryColor = preferences[Keys.PRIMARY_COLOR] ?: defaultValues.primaryColor,
                // Both fields now use the same preference value -> no more async inconsistency
                isMarqueeEnabled = marqueeEnabled,
                useMarquee = marqueeEnabled,

                isShuffleEnabled = preferences[Keys.IS_SHUFFLE_ENABLED] ?: defaultValues.isShuffleEnabled,
                repeatMode = repeatMode,

                // Fix: Value is now actually loaded
                isAutoPlayOnStart = preferences[Keys.IS_AUTOPLAY_ON_START] ?: defaultValues.isAutoPlayOnStart,

                isAutoPlayOnBluetooth = preferences[Keys.IS_AUTOPLAY_ON_BLUETOOTH] ?: defaultValues.isAutoPlayOnBluetooth,
                musicFolderPath = preferences[Keys.MUSIC_FOLDER_PATH] ?: defaultValues.musicFolderPath,
                miniPlayerProgressBarHeight = preferences[Keys.MINI_PLAYER_PROGRESS_BAR_HEIGHT] ?: defaultValues.miniPlayerProgressBarHeight,
                fullScreenPlayerProgressBarHeight = preferences[Keys.FULL_SCREEN_PLAYER_PROGRESS_BAR_HEIGHT] ?: defaultValues.fullScreenPlayerProgressBarHeight,
                oneHandedMode = preferences[Keys.ONE_HANDED_MODE] ?: defaultValues.oneHandedMode,
                showHiddenFiles = preferences[Keys.SHOW_HIDDEN_FILES] ?: defaultValues.showHiddenFiles,
                playOnFolderClick = preferences[Keys.PLAY_ON_FOLDER_CLICK] ?: defaultValues.playOnFolderClick,
                showFolderSongCount = preferences[Keys.SHOW_FOLDER_SONG_COUNT] ?: defaultValues.showFolderSongCount,
                backgroundTintFraction = preferences[Keys.BACKGROUND_TINT_FRACTION] ?: defaultValues.backgroundTintFraction,
                backgroundGradientEnabled = preferences[Keys.BACKGROUND_GRADIENT_ENABLED] ?: defaultValues.backgroundGradientEnabled,
                transparentListItems = preferences[Keys.TRANSPARENT_LIST_ITEMS] ?: defaultValues.transparentListItems,
                settingsCardTransparent = preferences[Keys.SETTINGS_CARD_TRANSPARENT] ?: defaultValues.settingsCardTransparent,
                settingsCardBorderWidth = preferences[Keys.SETTINGS_CARD_BORDER_WIDTH] ?: defaultValues.settingsCardBorderWidth,
                settingsCardBorderColor = preferences[Keys.SETTINGS_CARD_BORDER_COLOR] ?: defaultValues.settingsCardBorderColor
            )
        }

    // --- Setter ---

    // Fix: Now also writes to IS_MARQUEE_ENABLED to maintain consistency
    override suspend fun setUseMarquee(useMarquee: Boolean) {
        dataStore.edit { it[Keys.IS_MARQUEE_ENABLED] = useMarquee }
    }

    override suspend fun setOneHandedMode(isEnabled: Boolean) {
        dataStore.edit { it[Keys.ONE_HANDED_MODE] = isEnabled }
    }

    override suspend fun setTheme(theme: UserPreferences.Theme) {
        dataStore.edit { it[Keys.THEME] = theme.name }
    }

    override suspend fun setPrimaryColor(color: String) {
        dataStore.edit { it[Keys.PRIMARY_COLOR] = color }
    }

    override suspend fun setMarqueeEnabled(isEnabled: Boolean) {
        dataStore.edit { it[Keys.IS_MARQUEE_ENABLED] = isEnabled }
    }

    override suspend fun setShuffleEnabled(isEnabled: Boolean) {
        dataStore.edit { it[Keys.IS_SHUFFLE_ENABLED] = isEnabled }
    }

    // New setter (may need to be added to the interface as well!)
    override suspend fun setAutoPlayOnStart(isEnabled: Boolean) {
        dataStore.edit { it[Keys.IS_AUTOPLAY_ON_START] = isEnabled }
    }

    override suspend fun setAutoPlayOnBluetooth(isEnabled: Boolean) {
        dataStore.edit { it[Keys.IS_AUTOPLAY_ON_BLUETOOTH] = isEnabled }
    }

    override suspend fun setRepeatMode(repeatMode: UserPreferences.RepeatMode) {
        dataStore.edit { it[Keys.REPEAT_MODE] = repeatMode.name }
    }

    override suspend fun setMusicFolderPath(path: String) {
        dataStore.edit { it[Keys.MUSIC_FOLDER_PATH] = path }
    }

    override suspend fun setMiniPlayerProgressBarHeight(height: Float) {
        dataStore.edit { it[Keys.MINI_PLAYER_PROGRESS_BAR_HEIGHT] = height }
    }

    override suspend fun setFullScreenPlayerProgressBarHeight(height: Float) {
        dataStore.edit { it[Keys.FULL_SCREEN_PLAYER_PROGRESS_BAR_HEIGHT] = height }
    }

    override suspend fun setShowHiddenFiles(show: Boolean) {
        dataStore.edit { it[Keys.SHOW_HIDDEN_FILES] = show }
    }

    override suspend fun setPlayOnFolderClick(isEnabled: Boolean) {
        dataStore.edit { it[Keys.PLAY_ON_FOLDER_CLICK] = isEnabled }
    }

    override suspend fun setShowFolderSongCount(isEnabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_FOLDER_SONG_COUNT] = isEnabled }
    }

    override suspend fun setBackgroundTintFraction(fraction: Float) {
        dataStore.edit { it[Keys.BACKGROUND_TINT_FRACTION] = fraction }
    }

    override suspend fun setBackgroundGradientEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKGROUND_GRADIENT_ENABLED] = enabled }
    }

    override suspend fun setTransparentListItems(enabled: Boolean) {
        dataStore.edit { it[Keys.TRANSPARENT_LIST_ITEMS] = enabled }
    }

    override suspend fun setSettingsCardTransparent(enabled: Boolean) {
        dataStore.edit { it[Keys.SETTINGS_CARD_TRANSPARENT] = enabled }
    }

    override suspend fun setSettingsCardBorderWidth(widthDp: Float) {
        dataStore.edit { it[Keys.SETTINGS_CARD_BORDER_WIDTH] = widthDp }
    }

    override suspend fun setSettingsCardBorderColor(color: String) {
        dataStore.edit { it[Keys.SETTINGS_CARD_BORDER_COLOR] = color }
    }

    override suspend fun restoreUserPreferences(prefs: UserPreferences) {
        dataStore.edit { p ->
            p[Keys.THEME] = prefs.theme.name
            p[Keys.PRIMARY_COLOR] = prefs.primaryColor
            p[Keys.IS_MARQUEE_ENABLED] = prefs.isMarqueeEnabled
            p[Keys.IS_SHUFFLE_ENABLED] = prefs.isShuffleEnabled
            p[Keys.IS_AUTOPLAY_ON_START] = prefs.isAutoPlayOnStart
            p[Keys.IS_AUTOPLAY_ON_BLUETOOTH] = prefs.isAutoPlayOnBluetooth
            p[Keys.REPEAT_MODE] = prefs.repeatMode.name
            p[Keys.MUSIC_FOLDER_PATH] = prefs.musicFolderPath
            p[Keys.MINI_PLAYER_PROGRESS_BAR_HEIGHT] = prefs.miniPlayerProgressBarHeight
            p[Keys.FULL_SCREEN_PLAYER_PROGRESS_BAR_HEIGHT] = prefs.fullScreenPlayerProgressBarHeight
            p[Keys.ONE_HANDED_MODE] = prefs.oneHandedMode
            p[Keys.SHOW_HIDDEN_FILES] = prefs.showHiddenFiles
            p[Keys.PLAY_ON_FOLDER_CLICK] = prefs.playOnFolderClick
            p[Keys.SHOW_FOLDER_SONG_COUNT] = prefs.showFolderSongCount
            p[Keys.BACKGROUND_TINT_FRACTION] = prefs.backgroundTintFraction
            p[Keys.BACKGROUND_GRADIENT_ENABLED] = prefs.backgroundGradientEnabled
            p[Keys.TRANSPARENT_LIST_ITEMS] = prefs.transparentListItems
            p[Keys.SETTINGS_CARD_TRANSPARENT] = prefs.settingsCardTransparent
            p[Keys.SETTINGS_CARD_BORDER_WIDTH] = prefs.settingsCardBorderWidth
            p[Keys.SETTINGS_CARD_BORDER_COLOR] = prefs.settingsCardBorderColor
        }
    }

    override fun getMusicFolderPath(): Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.MUSIC_FOLDER_PATH] ?: ""
        }

    // --- Resume Logic ---

    override suspend fun saveLastPlayedSong(song: Song, position: Long, folderPath: String?) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_SONG_PATH] = song.path

            // Fix: Content URI is saved
            if (song.contentUri.isNotEmpty()) {
                prefs[Keys.LAST_SONG_CONTENT_URI] = song.contentUri
            }

            prefs[Keys.LAST_SONG_TITLE] = song.title
            prefs[Keys.LAST_SONG_ARTIST] = song.artist
            prefs[Keys.LAST_SONG_DURATION] = song.duration
            prefs[Keys.LAST_POSITION] = position
            if (song.albumArtPath != null) {
                prefs[Keys.LAST_SONG_ALBUM_ART] = song.albumArtPath
            } else {
                prefs.remove(Keys.LAST_SONG_ALBUM_ART)
            }
            // Only write if present — never delete (to preserve older saves)
            if (folderPath != null) {
                prefs[Keys.LAST_FOLDER_PATH] = folderPath
            }
        }
    }

    override suspend fun getLastPlayedSong(): LastPlayedSong? {
        val prefs = dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()

        val path = prefs[Keys.LAST_SONG_PATH] ?: return null
        val savedContentUri = prefs[Keys.LAST_SONG_CONTENT_URI]
        val folderPath = prefs[Keys.LAST_FOLDER_PATH]

        val title = prefs[Keys.LAST_SONG_TITLE] ?: "Unknown Title"
        val artist = prefs[Keys.LAST_SONG_ARTIST] ?: "Unknown Artist"
        val duration = prefs[Keys.LAST_SONG_DURATION] ?: 0L
        val position = prefs[Keys.LAST_POSITION] ?: 0L
        val albumArtPath = prefs[Keys.LAST_SONG_ALBUM_ART]

        // Fix: More robust URI logic.
        // 1. Try to use the stored contentUri.
        // 2. Fallback: build file:// URI (for migration or error cases).
        val contentUri = if (!savedContentUri.isNullOrEmpty()) {
            savedContentUri
        } else {
            Uri.fromFile(File(path)).toString()
        }

        return LastPlayedSong(
            song = Song(
                path = path,
                contentUri = contentUri,
                title = title,
                artist = artist,
                duration = duration,
                albumArtPath = albumArtPath
            ),
            position = position,
            folderPath = folderPath
        )
    }

    private inline fun <reified T : Enum<T>> safeEnumValueOf(name: String?, default: T): T {
        if (name == null) return default
        return try {
            java.lang.Enum.valueOf(T::class.java, name)
        } catch (e: IllegalArgumentException) {
            default
        }
    }
}