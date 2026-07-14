package com.example.newaudio.data.audio

import androidx.media3.common.Player
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.ISettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Persists repeat and shuffle changes in two independent serial workers.
 * Conflation keeps only the newest pending value while an older write runs,
 * preventing completion order from restoring stale preferences.
 */
class PlaybackPreferenceWriter(
    private val settingsRepository: ISettingsRepository,
    coroutineScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher
) {
    private companion object {
        private const val TAG = "PlaybackPreferenceWriter"
    }

    private val repeatRequests = Channel<UserPreferences.RepeatMode>(Channel.CONFLATED)
    private val shuffleRequests = Channel<Boolean>(Channel.CONFLATED)
    private val repeatWorker: Job = coroutineScope.launch(ioDispatcher) {
        for (mode in repeatRequests) persist("repeat mode") {
            settingsRepository.setRepeatMode(mode)
        }
    }
    private val shuffleWorker: Job = coroutineScope.launch(ioDispatcher) {
        for (enabled in shuffleRequests) persist("shuffle mode") {
            settingsRepository.setShuffleEnabled(enabled)
        }
    }

    fun requestRepeatMode(playerRepeatMode: Int): Boolean {
        if (!repeatWorker.isActive) return false
        val mode = when (playerRepeatMode) {
            Player.REPEAT_MODE_ONE -> UserPreferences.RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> UserPreferences.RepeatMode.ALL
            else -> UserPreferences.RepeatMode.NONE
        }
        return repeatRequests.trySend(mode).isSuccess
    }

    fun requestShuffle(enabled: Boolean): Boolean {
        if (!shuffleWorker.isActive) return false
        return shuffleRequests.trySend(enabled).isSuccess
    }

    private suspend fun persist(label: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Could not persist %s", label)
        }
    }
}
