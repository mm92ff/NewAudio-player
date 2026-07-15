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
    val videoFolderPath: String,
    val miniPlayerProgressBarHeight: Float,
    val fullScreenPlayerProgressBarHeight: Float,
    val oneHandedMode: Boolean,
    val useMarquee: Boolean,
    val showHiddenFiles: Boolean,
    val playOnFolderClick: Boolean,
    val showFolderSongCount: Boolean,
    val backgroundTintFraction: Float,
    val backgroundGradientEnabled: Boolean,
    val backgroundGradientDirection: GradientDirection = GradientDirection.TOP_TO_BOTTOM,
    val transparentListItems: Boolean,
    val settingsCardTransparent: Boolean,
    val settingsCardBorderWidth: Float,
    val settingsCardBorderColor: String,
    val resumeSessionOnModeSwitch: Boolean = false,
    val showVideoPreviewItems: Boolean = false,
    val videoDisplayMode: VideoDisplayMode = VideoDisplayMode.LIST,
    val videoGalleryColumns: Int = 3,
    val showVideoNamesInGallery: Boolean = false,
    val videoMarkersEnabled: Boolean = false
) {
    enum class Theme {
        SYSTEM, LIGHT, DARK
    }

    enum class RepeatMode {
        NONE, ONE, ALL
    }

    enum class GradientDirection {
        TOP_TO_BOTTOM,
        BOTTOM_TO_TOP,
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT,
        TOP_LEFT_TO_BOTTOM_RIGHT,
        BOTTOM_RIGHT_TO_TOP_LEFT,
        TOP_RIGHT_TO_BOTTOM_LEFT,
        BOTTOM_LEFT_TO_TOP_RIGHT
    }

    enum class VideoDisplayMode {
        LIST,
        PREVIEW_LIST,
        GALLERY_SQUARE,
        GALLERY_ADAPTIVE,
        GALLERY_FILLED
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
            videoFolderPath = "",
            miniPlayerProgressBarHeight = 30f,
            fullScreenPlayerProgressBarHeight = 30f,
            oneHandedMode = false,
            useMarquee = true,
            showHiddenFiles = false,
            playOnFolderClick = false,
            showFolderSongCount = false,
            backgroundTintFraction = 0.08f,
            backgroundGradientEnabled = false,
            backgroundGradientDirection = GradientDirection.TOP_TO_BOTTOM,
            transparentListItems = false,
            settingsCardTransparent = false,
            settingsCardBorderWidth = 0f,
            settingsCardBorderColor = "#9E9E9E",
            resumeSessionOnModeSwitch = false,
            showVideoPreviewItems = false,
            videoDisplayMode = VideoDisplayMode.LIST,
            videoGalleryColumns = 3,
            showVideoNamesInGallery = false,
            videoMarkersEnabled = false
        )
    }
}
