package com.example.newaudio.domain.audio

// IMPORTANT: There must NO LONGER be a "data class EqualizerConfig" here!
// It now lives in its own file EqualizerConfig.kt.

interface AudioEffectController {
    fun initialize(sessionId: Int)
    fun setEnabled(isEnabled: Boolean)
    fun setBandLevel(bandId: Int, level: Float)
    fun setPreset(presetName: String): Boolean
    fun getConfig(): EqualizerConfig? // It will find the class from the other file
    fun release()
}