package com.example.newaudio.domain.audio

data class EqualizerConfig(
    val isEnabled: Boolean,
    val numBands: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val centerFreqs: IntArray,
    val currentLevels: IntArray,
    // This was missing or was in the wrong file:
    val currentPresetName: String = "Normal"
)