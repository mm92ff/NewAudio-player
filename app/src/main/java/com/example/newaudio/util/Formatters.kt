package com.example.newaudio.util

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Formats a duration in milliseconds into a readable MM:SS string.
 */
fun formatDurationMMSS(millis: Long): String {
    if (millis <= 0) return "--:--"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
