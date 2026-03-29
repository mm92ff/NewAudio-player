package com.example.newaudio.data.repository

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.newaudio.di.MainDispatcher
import com.example.newaudio.domain.repository.IEqualizerRepository
import com.example.newaudio.service.MediaPlaybackService
import com.example.newaudio.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class EqualizerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher
) : IEqualizerRepository {

    companion object {
        private const val TAG = "EqualizerRepository"
    }

    private val _equalizerState = MutableStateFlow(
        IEqualizerRepository.EqualizerState(enabled = false, bands = persistentListOf())
    )
    override fun getEqualizerState() = _equalizerState.asStateFlow()

    private var mediaController: MediaController? = null
    private val repoScope = CoroutineScope(mainDispatcher + SupervisorJob())

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        repoScope.launch {
            try {
                mediaController = controllerFuture.await()
                refreshEqualizerConfig()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Controller initialization failed")
            }
        }
    }

    override suspend fun setEnabled(isEnabled: Boolean) {
        val previousState = _equalizerState.value
        // RE-ENABLED: Optimistic update for immediate UI feedback
        _equalizerState.update { it.copy(enabled = isEnabled) }
        
        withContext(mainDispatcher) {
            try {
                val args = Bundle().apply { putBoolean(Constants.Playback.EXTRA_EQ_ENABLED, isEnabled) }
                sendEqCommand(Constants.Playback.ACTION_SET_EQ_ENABLED, args)
                // We still wait a bit and refresh to ensure sync with hardware state
                delay(150) 
                refreshEqualizerConfig() 
            } catch (e: Exception) { 
                Timber.tag(TAG).e(e, "Set Enabled failed")
                _equalizerState.value = previousState // Rollback on error
                refreshEqualizerConfig() 
            }
        }
    }

    override suspend fun setBandLevel(bandId: Int, level: Float) {
        val previousState = _equalizerState.value
        
        // Optimistic update required for smooth slider interaction
        _equalizerState.update { state ->
            state.copy(bands = state.bands.map {
                if (it.id == bandId) it.copy(currentLevel = level) else it
            }.toPersistentList())
        }
        
        withContext(mainDispatcher) {
            try {
                val args = Bundle().apply {
                    putInt(Constants.Playback.EXTRA_BAND_ID, bandId)
                    putFloat(Constants.Playback.EXTRA_BAND_LEVEL, level)
                }
                sendEqCommand(Constants.Playback.ACTION_SET_EQ_BAND, args)
            } catch (e: Exception) {
                _equalizerState.value = previousState // Rollback on error
            }
        }
    }

    override suspend fun applyPreset(preset: IEqualizerRepository.EqPreset) {
        // Optimistic update removed for presets (complex change)
        withContext(mainDispatcher) {
            try {
                val args = Bundle().apply { putString(Constants.Playback.EXTRA_EQ_PRESET_NAME, preset.name) }
                sendEqCommand(Constants.Playback.ACTION_SET_EQ_PRESET, args)
                delay(300) // Wait for preset to be applied in service
                refreshEqualizerConfig()
            } catch (e: Exception) { Timber.tag(TAG).e(e, "Preset application failed") }
        }
    }

    override suspend fun refreshEqualizerConfig() {
        val controller = mediaController ?: return
        try {
            val command = SessionCommand(Constants.Playback.ACTION_GET_EQ_CONFIG, Bundle.EMPTY)
            val result = controller.sendCustomCommand(command, Bundle.EMPTY).await()
            parseEqualizerBundle(result.extras)
        } catch (e: Exception) { Timber.tag(TAG).e(e, "EQ refresh failed") }
    }

    private suspend fun sendEqCommand(action: String, args: Bundle) {
        val controller = mediaController ?: return
        val command = SessionCommand(action, Bundle.EMPTY)
        controller.sendCustomCommand(command, args).await()
    }

    private fun parseEqualizerBundle(bundle: Bundle) {
        try {
            val enabled = bundle.getBoolean(Constants.Playback.EXTRA_EQ_ENABLED)
            val numBands = bundle.getInt(Constants.Playback.EXTRA_NUM_BANDS)
            val freqs = bundle.getIntArray(Constants.Playback.EXTRA_CENTER_FREQS) ?: return
            val levels = bundle.getIntArray(Constants.Playback.EXTRA_CURRENT_LEVELS) ?: return
            val min = bundle.getInt(Constants.Playback.EXTRA_BAND_LEVEL_RANGE_MIN).toFloat()
            val max = bundle.getInt(Constants.Playback.EXTRA_BAND_LEVEL_RANGE_MAX).toFloat()
            val presetName = bundle.getString(Constants.Playback.EXTRA_EQ_PRESET_NAME) ?: "Normal"

            val currentPreset = try {
                IEqualizerRepository.EqPreset.entries.find {
                    it.name.equals(presetName, ignoreCase = true)
                } ?: IEqualizerRepository.EqPreset.CUSTOM
            } catch (e: Exception) {
                IEqualizerRepository.EqPreset.CUSTOM
            }

            val bands = List(numBands) { i ->
                IEqualizerRepository.EqualizerBand(
                    id = i,
                    centerFreq = freqs[i].toFloat() / 1000f, // From mHz to Hz
                    currentLevel = levels[i].toFloat() / 100f, // From centi-dB to dB
                    rangeMin = min / 100f,
                    rangeMax = max / 100f
                )
            }.toPersistentList()

            _equalizerState.update {
                it.copy(
                    enabled = enabled,
                    bands = bands,
                    currentPreset = currentPreset
                )
            }
        } catch (e: Exception) { Timber.tag(TAG).e(e, "Parsing EQ bundle failed") }
    }
}
