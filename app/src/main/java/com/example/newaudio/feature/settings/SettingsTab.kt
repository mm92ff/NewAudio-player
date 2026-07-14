package com.example.newaudio.feature.settings

import androidx.annotation.StringRes
import com.example.newaudio.R
import com.example.newaudio.ui.NewAudioTestTags

enum class SettingsTab(
    @StringRes val titleRes: Int,
    val tabTestTag: String,
    val contentTestTag: String
) {
    GENERAL(
        titleRes = R.string.settings_tab_general,
        tabTestTag = NewAudioTestTags.SETTINGS_TAB_GENERAL,
        contentTestTag = NewAudioTestTags.SETTINGS_CONTENT_GENERAL
    ),
    MEDIA(
        titleRes = R.string.settings_tab_media,
        tabTestTag = NewAudioTestTags.SETTINGS_TAB_MEDIA,
        contentTestTag = NewAudioTestTags.SETTINGS_CONTENT_MEDIA
    ),
    DESIGN(
        titleRes = R.string.settings_tab_design,
        tabTestTag = NewAudioTestTags.SETTINGS_TAB_DESIGN,
        contentTestTag = NewAudioTestTags.SETTINGS_CONTENT_DESIGN
    ),
    SYSTEM(
        titleRes = R.string.settings_tab_system,
        tabTestTag = NewAudioTestTags.SETTINGS_TAB_SYSTEM,
        contentTestTag = NewAudioTestTags.SETTINGS_CONTENT_SYSTEM
    )
}
