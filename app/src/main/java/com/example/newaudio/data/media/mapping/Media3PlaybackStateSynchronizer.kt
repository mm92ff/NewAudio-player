package com.example.newaudio.data.media.mapping

import androidx.media3.common.Player
import com.example.newaudio.data.media.library.MediaLibraryRepository
import com.example.newaudio.data.media.playback.PlaybackQueueState
import com.example.newaudio.data.media.playback.PlaybackStateStore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Reconstructs app queue/state from an already active Media3 controller. */
@Singleton
class Media3PlaybackStateSynchronizer @Inject constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val itemMapper: Media3ItemMapper,
    private val stateStore: PlaybackStateStore,
    private val queueState: PlaybackQueueState
) {
    /**
     * Returns true when a current item could be classified and fully
     * published. False means the caller must publish a connected empty state.
     */
    suspend fun synchronize(player: Player): Boolean {
        val currentItem = player.currentMediaItem ?: return false
        val mediaItems = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        if (mediaItems.isEmpty()) return false

        val currentIndex = player.currentMediaItemIndex.takeIf { it in mediaItems.indices } ?: 0
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.takeIf { it > 0L } ?: 0L

        return when (mediaLibraryRepository.resolveMediaType(currentItem)) {
            Media3ItemMapper.MediaType.VIDEO -> {
                val videos = mediaLibraryRepository.mapVideos(mediaItems)
                val currentVideo = videos.getOrNull(currentIndex) ?: itemMapper.toVideo(currentItem)
                val effectiveQueue = videos.ifEmpty { listOf(currentVideo) }
                queueState.setVideos(effectiveQueue, File(currentVideo.path).parent)
                stateStore.update {
                    it.copy(
                        isRestoring = false,
                        isPlaying = player.isPlaying,
                        currentSong = null,
                        currentVideo = currentVideo,
                        currentPosition = currentPosition,
                        totalDuration = duration,
                        isShuffleEnabled = player.shuffleModeEnabled,
                        repeatMode = player.repeatMode,
                        playerError = null,
                        player = player
                    )
                }
                true
            }

            Media3ItemMapper.MediaType.AUDIO -> {
                val songs = mediaLibraryRepository.mapSongs(mediaItems)
                val currentSong = songs.getOrNull(currentIndex) ?: itemMapper.toSong(currentItem)
                val effectiveQueue = songs.ifEmpty { listOf(currentSong) }
                queueState.setMusic(effectiveQueue, File(currentSong.path).parent)
                stateStore.update {
                    it.copy(
                        isRestoring = false,
                        isPlaying = player.isPlaying,
                        currentSong = currentSong,
                        currentVideo = null,
                        currentPosition = currentPosition,
                        totalDuration = duration,
                        isShuffleEnabled = player.shuffleModeEnabled,
                        repeatMode = player.repeatMode,
                        playerError = null,
                        player = player
                    )
                }
                true
            }

            null -> false
        }
    }
}
