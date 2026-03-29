package com.example.newaudio.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val theme: Theme,
    val primaryColor: String,
    val isMarqueeEnabled: Boolean,
    val isShuffleEnabled: Boolean,
    val repeatMode: RepeatMode,
    val isAutoPlayOnStart: Boolean,
    val isAutoPlayOnBluetooth: Boolean,
    val musicFolderPath: String,
    val miniPlayerProgressBarHeight: Float,
    val fullScreenPlayerProgressBarHeight: Float,
    val oneHandedMode: Boolean,
    val useMarquee: Boolean,
    val showHiddenFiles: Boolean,
    val playOnFolderClick: Boolean,
    val showFolderSongCount: Boolean,
    val backgroundTintFraction: Float,
    val backgroundGradientEnabled: Boolean,
    val transparentListItems: Boolean,
    val settingsCardTransparent: Boolean,
    val settingsCardBorderWidth: Float,
    val settingsCardBorderColor: String
) {
    enum class Theme {
        SYSTEM, LIGHT, DARK
    }

    enum class RepeatMode {
        NONE, ONE, ALL
    }

    companion object {
        fun default(): UserPreferences = UserPreferences(
            theme = Theme.DARK, // Changed to DARK
            primaryColor = "#F44336",
            isMarqueeEnabled = true,
            isShuffleEnabled = false,
            repeatMode = RepeatMode.ALL, // Changed to ALL
            isAutoPlayOnStart = false,
            isAutoPlayOnBluetooth = false,
            musicFolderPath = "",
            miniPlayerProgressBarHeight = 30f,
            fullScreenPlayerProgressBarHeight = 30f,
            oneHandedMode = false,
            useMarquee = false,
            showHiddenFiles = false,
            playOnFolderClick = false,
            showFolderSongCount = false,
            backgroundTintFraction = 0.08f,
            backgroundGradientEnabled = false,
            transparentListItems = false,
            settingsCardTransparent = false,
            settingsCardBorderWidth = 0f,
            settingsCardBorderColor = "#9E9E9E"
        )
    }
}
