package com.example.newaudio.feature.settings

import com.example.newaudio.util.UiText

sealed interface SettingsEvent {
    data class ShowMessage(val text: UiText) : SettingsEvent
}
