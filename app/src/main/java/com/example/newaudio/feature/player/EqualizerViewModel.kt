package com.example.newaudio.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.repository.IEqualizerRepository
import com.example.newaudio.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerRepository: IEqualizerRepository, // NEW: Direct repository
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        private const val TAG = "EqualizerViewModel"
    }

    // The StateFlow comes directly from the new repository
    val equalizerState: StateFlow<IEqualizerRepository.EqualizerState> = equalizerRepository.getEqualizerState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.STATE_FLOW_SHARING_TIMEOUT_MS),
            initialValue = IEqualizerRepository.EqualizerState(enabled = false, bands = kotlinx.collections.immutable.persistentListOf(), currentPreset = IEqualizerRepository.EqPreset.NORMAL)
        )

    fun onToggleEqualizerEnabled() {
        val currentEnabled = equalizerState.value.enabled
        safeLaunch { equalizerRepository.setEnabled(!currentEnabled) }
    }

    fun onSetBandLevel(bandId: Int, level: Float) = safeLaunch {
        equalizerRepository.setBandLevel(bandId, level)
    }

    fun onApplyPreset(preset: IEqualizerRepository.EqPreset) = safeLaunch {
        equalizerRepository.applyPreset(preset)
    }

    private fun safeLaunch(block: suspend () -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            try {
                block()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "EQ Action failed: ${e.message}")
            }
        }
    }
}
