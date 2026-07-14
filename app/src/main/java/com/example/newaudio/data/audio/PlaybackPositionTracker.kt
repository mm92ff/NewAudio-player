package com.example.newaudio.data.audio

import android.os.SystemClock
import androidx.media3.common.Player
import com.example.newaudio.data.media.playback.PlaybackQueueState
import com.example.newaudio.data.media.playback.PlaybackStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

fun interface MonotonicClock {
    fun nowMs(): Long
}

/**
 * Owns the one position ticker for a listener generation. The supplied scope
 * owns the job; pausing stops it and repeated start notifications are
 * idempotent. Auto-save decisions use a monotonic clock.
 */
class PlaybackPositionTracker(
    private val player: Player,
    private val stateStore: PlaybackStateStore,
    private val queueState: PlaybackQueueState,
    private val snapshotWriter: PlaybackSnapshotWriter,
    private val coroutineScope: CoroutineScope,
    private val clock: MonotonicClock = MonotonicClock(SystemClock::elapsedRealtime),
    private val positionUpdateIntervalMs: Long = POSITION_UPDATE_INTERVAL_MS,
    private val autoSaveIntervalMs: Long = AUTO_SAVE_INTERVAL_MS,
    private val autoSavePositionDeltaMs: Long = AUTO_SAVE_POSITION_DELTA_MS
) {
    private companion object {
        private const val POSITION_UPDATE_INTERVAL_MS = 1_000L
        private const val AUTO_SAVE_INTERVAL_MS = 5_000L
        private const val AUTO_SAVE_POSITION_DELTA_MS = 3_000L
    }

    private var positionJob: Job? = null
    private var lastSaveTimeMs = 0L
    private var lastSavedPositionMs = 0L

    fun synchronize() {
        if (!player.isPlaying) {
            positionJob?.cancel()
            positionJob = null
            return
        }
        if (positionJob?.isActive == true) return
        positionJob = coroutineScope.launch {
            while (isActive) {
                val position = player.currentPosition.coerceAtLeast(0L)
                stateStore.update { it.copy(currentPosition = position) }
                val now = clock.nowMs()
                if (now - lastSaveTimeMs >= autoSaveIntervalMs &&
                    abs(position - lastSavedPositionMs) >= autoSavePositionDeltaMs
                ) {
                    saveCurrentState(now)
                }
                delay(positionUpdateIntervalMs)
            }
        }
    }

    fun saveCurrentState(nowMs: Long = clock.nowMs()): Boolean {
        val state = stateStore.value
        val song = state.currentSong ?: return false
        lastSaveTimeMs = nowMs
        lastSavedPositionMs = state.currentPosition
        return snapshotWriter.request(
            song,
            state.currentPosition,
            queueState.snapshot().folderPath
        )
    }
}
