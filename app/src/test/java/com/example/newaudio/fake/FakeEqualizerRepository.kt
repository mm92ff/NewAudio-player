package com.example.newaudio.fake

import com.example.newaudio.domain.repository.IEqualizerRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeEqualizerRepository : IEqualizerRepository {

    private val _state = MutableStateFlow(
        IEqualizerRepository.EqualizerState(
            enabled = false,
            bands = persistentListOf()
        )
    )

    override fun getEqualizerState(): StateFlow<IEqualizerRepository.EqualizerState> = _state.asStateFlow()

    override suspend fun setEnabled(isEnabled: Boolean) {
        _state.value = _state.value.copy(enabled = isEnabled)
    }

    override suspend fun setBandLevel(bandId: Int, level: Float) {}

    override suspend fun applyPreset(preset: IEqualizerRepository.EqPreset) {
        _state.value = _state.value.copy(currentPreset = preset)
    }

    override suspend fun refreshEqualizerConfig() {}
}
