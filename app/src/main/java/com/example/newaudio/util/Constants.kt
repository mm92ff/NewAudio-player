package com.example.newaudio.util

object Constants {

    const val STATE_FLOW_SHARING_TIMEOUT_MS = 5000L
    const val REORDER_DEBOUNCE_MS = 500L

    object UI {
        const val DISABLED_ALPHA = 0.5f
        const val VERTICAL_ROTATION_DEGREES = 270f
    }

    object ThemeColors {
        val primaryColorOptions = listOf(
            "#F44336", // Red
            "#6750A4", // Purple
            "#9C27B0", // Deep Purple
            "#3F51B5", // Indigo
            "#00BCD4", // Cyan
            "#4CAF50", // Green
            "#FF9800", // Orange
            "#E91E63"  // Pink
        )

        val extendedColorOptions = listOf(
            "#9E9E9E",  // Gray
            "#795548",  // Brown
            "#607D8B",  // Blue-gray
            "#FFEB3B",  // Yellow
            "#009688",  // Teal
            "#FF5722",  // Deep orange
        ) + primaryColorOptions
    }

    object Playback {
        const val ACTION_PLAY_PAUSE = "PLAY_PAUSE"
        const val ACTION_NEXT = "NEXT"
        const val ACTION_PREVIOUS = "PREVIOUS"

        const val ACTION_SET_EQ_ENABLED = "ACTION_SET_EQ_ENABLED"
        const val ACTION_SET_EQ_BAND = "ACTION_SET_EQ_BAND"
        const val ACTION_GET_EQ_CONFIG = "ACTION_GET_EQ_CONFIG"
        const val ACTION_SET_EQ_PRESET = "ACTION_SET_EQ_PRESET"

        const val EXTRA_EQ_ENABLED = "EXTRA_EQ_ENABLED"
        const val EXTRA_BAND_ID = "EXTRA_BAND_ID"
        const val EXTRA_BAND_LEVEL = "EXTRA_BAND_LEVEL"
        const val EXTRA_EQ_PRESET_NAME = "EXTRA_EQ_PRESET_NAME"

        const val EXTRA_NUM_BANDS = "EXTRA_NUM_BANDS"
        const val EXTRA_BAND_LEVEL_RANGE_MIN = "EXTRA_BAND_LEVEL_RANGE_MIN"
        const val EXTRA_BAND_LEVEL_RANGE_MAX = "EXTRA_BAND_LEVEL_RANGE_MAX"
        const val EXTRA_CENTER_FREQS = "EXTRA_CENTER_FREQS"
        const val EXTRA_CURRENT_LEVELS = "EXTRA_CURRENT_LEVELS"

        const val DB_TO_MB_FACTOR = 100
        const val HW_PRESET_NAME_MATCH_LENGTH = 4

        enum class EqPreset {
            CUSTOM,
            NORMAL,
            BASS,
            VOCAL,
            ROCK,
            POP,
            FLAT,
            CLASSIC,
            JAZZ,
            CLASSICAL;

            companion object {
                fun fromString(name: String): EqPreset? {
                    return entries.find { it.name.equals(name, ignoreCase = true) }
                }
            }
        }
    }
}
