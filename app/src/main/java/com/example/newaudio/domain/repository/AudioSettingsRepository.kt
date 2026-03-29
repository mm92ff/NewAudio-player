package com.example.newaudio.domain.repository

import kotlinx.coroutines.flow.Flow

interface AudioSettingsRepository {
    // Speichern bleibt suspend (One-Shot Operation)
    suspend fun savePresetName(name: String)
    // Lesen wird zum Datenstrom (Flow)
    fun getSavedPresetName(): Flow<String>

    suspend fun saveCustomBandLevels(levels: List<Int>)
    fun getCustomBandLevels(): Flow<List<Int>>

    suspend fun saveEqualizerEnabled(isEnabled: Boolean)
    fun getEqualizerEnabled(): Flow<Boolean>
}