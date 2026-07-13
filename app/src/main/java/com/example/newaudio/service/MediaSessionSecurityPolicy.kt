package com.example.newaudio.service

import com.example.newaudio.domain.audio.EqualizerConfig
import com.example.newaudio.domain.repository.IEqualizerRepository

internal object MediaSessionSecurityPolicy {
    fun isControllerAllowed(appUid: Int, controllerUid: Int, isTrusted: Boolean): Boolean =
        controllerUid == appUid || isTrusted

    fun isBandValueValid(config: EqualizerConfig, bandId: Int, levelDb: Float): Boolean {
        val minLevelDb = config.minLevel / 100f
        val maxLevelDb = config.maxLevel / 100f
        return bandId in 0 until config.numBands && levelDb.isFinite() && levelDb in minLevelDb..maxLevelDb
    }

    fun isPresetValid(name: String): Boolean =
        name.length <= MAX_PRESET_LENGTH && IEqualizerRepository.EqPreset.entries.any {
            it.name.equals(name, ignoreCase = true)
        }

    private const val MAX_PRESET_LENGTH = 64
}
