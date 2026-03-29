package com.example.newaudio.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.newaudio.domain.repository.AudioSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class DataStoreAudioSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : AudioSettingsRepository {

    private object Keys {
        val PRESET_NAME = stringPreferencesKey("eq_preset_name")
        val CUSTOM_BAND_LEVELS = stringPreferencesKey("eq_custom_levels")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
    }

    // --- SAVE Methods (suspend) ---

    override suspend fun savePresetName(name: String) {
        try {
            dataStore.edit { preferences ->
                preferences[Keys.PRESET_NAME] = name
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to save preset name")
        }
    }

    override suspend fun saveCustomBandLevels(levels: List<Int>) {
        try {
            val serializedLevels = levels.joinToString(",")
            dataStore.edit { preferences ->
                preferences[Keys.CUSTOM_BAND_LEVELS] = serializedLevels
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to save custom band levels")
        }
    }

    override suspend fun saveEqualizerEnabled(isEnabled: Boolean) {
        try {
            dataStore.edit { preferences ->
                preferences[Keys.EQ_ENABLED] = isEnabled
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to save equalizer enabled state")
        }
    }

    // --- GET Methods (Reactive Flows) ---

    override fun getSavedPresetName(): Flow<String> {
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { preferences ->
                preferences[Keys.PRESET_NAME] ?: "Normal"
            }
            .distinctUntilChanged()
    }

    override fun getCustomBandLevels(): Flow<List<Int>> {
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { preferences ->
                val serializedString = preferences[Keys.CUSTOM_BAND_LEVELS] ?: ""

                if (serializedString.isNotEmpty()) {
                    serializedString.split(",").mapNotNull { it.toIntOrNull() }
                } else {
                    emptyList()
                }
            }
            .distinctUntilChanged()
    }

    override fun getEqualizerEnabled(): Flow<Boolean> {
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { preferences ->
                preferences[Keys.EQ_ENABLED] ?: false
            }
            .distinctUntilChanged()
    }
}