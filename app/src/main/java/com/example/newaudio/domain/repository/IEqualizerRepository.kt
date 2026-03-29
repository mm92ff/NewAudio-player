package com.example.newaudio.domain.repository

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

interface IEqualizerRepository {

    enum class EqPreset {
        CUSTOM,
        NORMAL,
        BASS,
        POP,
        CLASSIC,
        ROCK,
        JAZZ,
        VOCAL,
        FLAT,
        CLASSICAL
    }

    @Immutable
    data class EqualizerBand(
        val id: Int,
        val centerFreq: Float,
        val currentLevel: Float,
        val rangeMin: Float, // Using stable primitive
        val rangeMax: Float  // Using stable primitive
    )

    @Immutable
    data class EqualizerState(
        val enabled: Boolean,
        val bands: ImmutableList<EqualizerBand>,
        val currentPreset: EqPreset = EqPreset.NORMAL
    )

    fun getEqualizerState(): StateFlow<EqualizerState>

    suspend fun setEnabled(isEnabled: Boolean)

    suspend fun setBandLevel(bandId: Int, level: Float)

    suspend fun applyPreset(preset: EqPreset)

    suspend fun refreshEqualizerConfig()
}