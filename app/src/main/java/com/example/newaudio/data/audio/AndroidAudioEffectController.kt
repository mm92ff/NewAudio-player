package com.example.newaudio.data.audio

import android.media.audiofx.Equalizer
import com.example.newaudio.domain.audio.AudioEffectController
import com.example.newaudio.domain.audio.EqualizerConfig
import com.example.newaudio.domain.repository.AudioSettingsRepository
import com.example.newaudio.util.Constants.Playback
import com.example.newaudio.util.Constants.Playback.DB_TO_MB_FACTOR
import com.example.newaudio.util.Constants.Playback.EqPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAudioEffectController @Inject constructor(
    private val audioSettingsRepository: AudioSettingsRepository,
    private val appScope: CoroutineScope
) : AudioEffectController {

    private var equalizer: Equalizer? = null

    // Source of Truth for state to avoid hardware sync issues
    private var isEnabledInternal: Boolean = false
    private var currentPresetName: String = EqPreset.NORMAL.name

    companion object {
        private const val TAG = "AudioEffectController"

        private val PRESET_GAINS = mapOf(
            EqPreset.NORMAL to floatArrayOf(0f, 0f, 0f, 0f, 0f),
            EqPreset.BASS to floatArrayOf(6f, 4f, 0f, 0f, 0f),
            EqPreset.VOCAL to floatArrayOf(0f, 2f, 5f, 3f, 1f),
            EqPreset.ROCK to floatArrayOf(4f, 2f, -1f, 2f, 5f),
            EqPreset.POP to floatArrayOf(-1f, 1f, 4f, 1f, -1f)
        )
    }

    override fun initialize(sessionId: Int) {
        try {
            release()
            equalizer = Equalizer(0, sessionId)
            Timber.tag(TAG).d("Equalizer initialized for session ID $sessionId")

            // Restore State (Enabled status & Preset)
            appScope.launch {
                // FIX: Collect flows with .first()
                val savedEnabled = audioSettingsRepository.getEqualizerEnabled().first()
                val savedPreset = audioSettingsRepository.getSavedPresetName().first()

                withContext(Dispatchers.Main) {
                    isEnabledInternal = savedEnabled
                    currentPresetName = if (savedPreset.isNotEmpty()) savedPreset else EqPreset.NORMAL.name

                    val eq = equalizer ?: return@withContext

                    // 1. Restore Enabled Status
                    try {
                        eq.enabled = isEnabledInternal
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Failed to restore enabled state: ${e.message}")
                    }

                    // 2. Restore Preset
                    applyPresetInternal(currentPresetName)
                    Timber.tag(TAG).d("State restored: Preset=$currentPresetName, Enabled=$isEnabledInternal")
                }
            }

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Equalizer init failed: ${e.message}")
        }
    }

    override fun setEnabled(isEnabled: Boolean) {
        isEnabledInternal = isEnabled
        try {
            equalizer?.enabled = isEnabled
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set enabled state on hardware: ${e.message}")
        }

        appScope.launch {
            audioSettingsRepository.saveEqualizerEnabled(isEnabled)
        }
    }

    override fun setBandLevel(bandId: Int, level: Float) {
        val eq = equalizer ?: return
        if (bandId in 0 until eq.numberOfBands) {
            try {
                val levelMb = (level * DB_TO_MB_FACTOR).toInt().toShort()
                eq.setBandLevel(bandId.toShort(), levelMb)

                currentPresetName = EqPreset.CUSTOM.name

                appScope.launch {
                    audioSettingsRepository.savePresetName(currentPresetName)
                    saveCurrentLevelsToRepo()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error setting band level: ${e.message}")
            }
        }
    }

    override fun setPreset(presetName: String): Boolean {
        appScope.launch {
            val success = applyPresetInternalAsyncWrapper(presetName)
            if (success) {
                withContext(Dispatchers.Main) {
                    currentPresetName = presetName
                }
                audioSettingsRepository.savePresetName(presetName)
            }
        }
        return true
    }

    private suspend fun applyPresetInternalAsyncWrapper(presetName: String): Boolean {
        return if (presetName.equals(EqPreset.CUSTOM.name, ignoreCase = true)) {
            // FIX: Collect flow with .first()
            val customLevels = audioSettingsRepository.getCustomBandLevels().first()
            withContext(Dispatchers.Main) {
                applyCustomLevels(customLevels)
            }
            true
        } else {
            withContext(Dispatchers.Main) {
                applyPresetInternal(presetName)
            }
        }
    }

    private fun applyPresetInternal(presetName: String): Boolean {
        val eq = equalizer ?: return false

        if (presetName.equals(EqPreset.CUSTOM.name, ignoreCase = true)) {
            appScope.launch {
                // FIX: Collect flow with .first()
                val customLevels = audioSettingsRepository.getCustomBandLevels().first()
                withContext(Dispatchers.Main) {
                    applyCustomLevels(customLevels)
                }
            }
            return true
        }

        val preset = EqPreset.fromString(presetName) ?: return false

        return try {
            val hardwareIndex = (0 until eq.numberOfPresets.toInt()).firstOrNull { i ->
                val hwName = eq.getPresetName(i.toShort()).lowercase()
                hwName.contains(preset.name.lowercase().take(Playback.HW_PRESET_NAME_MATCH_LENGTH))
            }

            if (hardwareIndex != null) {
                eq.usePreset(hardwareIndex.toShort())
            } else {
                val gains = PRESET_GAINS[preset]
                if (gains != null) {
                    for (i in 0 until eq.numberOfBands.toInt()) {
                        val gain = if (i < gains.size) gains[i] else 0f
                        eq.setBandLevel(i.toShort(), (gain * DB_TO_MB_FACTOR).toInt().toShort())
                    }
                }
            }
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Preset error: ${e.message}")
            false
        }
    }

    private fun applyCustomLevels(levels: List<Int>) {
        val eq = equalizer ?: return
        try {
            if (levels.isNotEmpty() && levels.size == eq.numberOfBands.toInt()) {
                for (i in levels.indices) {
                    eq.setBandLevel(i.toShort(), levels[i].toShort())
                }
            } else {
                for (i in 0 until eq.numberOfBands.toInt()) {
                    eq.setBandLevel(i.toShort(), 0.toShort())
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error applying custom levels: ${e.message}")
        }
    }

    private suspend fun saveCurrentLevelsToRepo() {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val levels = ArrayList<Int>()
            for (i in 0 until numBands) {
                levels.add(eq.getBandLevel(i.toShort()).toInt())
            }
            audioSettingsRepository.saveCustomBandLevels(levels)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error saving levels: ${e.message}")
        }
    }

    override fun getConfig(): EqualizerConfig? {
        val eq = equalizer ?: return null
        return try {
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val centerFreqs = IntArray(numBands) { i -> eq.getCenterFreq(i.toShort()) }
            val currentLevels = IntArray(numBands) { i -> eq.getBandLevel(i.toShort()).toInt() }

            EqualizerConfig(
                isEnabled = isEnabledInternal,
                numBands = numBands,
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
                centerFreqs = centerFreqs,
                currentLevels = currentLevels,
                currentPresetName = currentPresetName
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Config retrieval failed: ${e.message}")
            null
        }
    }

    override fun release() {
        equalizer?.release()
        equalizer = null
    }
}