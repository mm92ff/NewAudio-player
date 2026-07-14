package com.example.newaudio.data.audio

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.repository.ISettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Serializes playback snapshot persistence. Its worker is owned by the scope
 * supplied by the controller gateway and ends when that listener scope ends.
 */
class PlaybackSnapshotWriter(
    settingsRepository: ISettingsRepository,
    coroutineScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher
) {
    private companion object {
        private const val TAG = "PlaybackSnapshotWriter"
    }

    private val requests = Channel<Request>(Channel.CONFLATED)
    private val worker: Job = coroutineScope.launch(ioDispatcher) {
        for (request in requests) {
            try {
                settingsRepository.saveLastPlayedSong(
                    request.song,
                    request.position,
                    request.folderPath
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.tag(TAG).e(error, "Could not persist playback snapshot")
            }
        }
    }

    fun request(song: Song, position: Long, folderPath: String?): Boolean {
        if (!worker.isActive) return false
        return requests.trySend(Request(song, position, folderPath)).isSuccess
    }

    private data class Request(
        val song: Song,
        val position: Long,
        val folderPath: String?
    )
}
