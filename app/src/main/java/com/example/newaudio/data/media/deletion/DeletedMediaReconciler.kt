package com.example.newaudio.data.media.deletion

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.newaudio.data.media.mapping.Media3ItemMapper
import com.example.newaudio.data.media.playback.PlaybackQueueState
import com.example.newaudio.data.media.playback.PlaybackStateStore
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Applies a pure delete decision to Media3 and publishes app state only after
 * all controller commands succeed. Controller restoration is best effort; a
 * rollback failure is logged and attached to the primary failure.
 */
@Singleton
class DeletedMediaReconciler @Inject constructor(
    private val stateStore: PlaybackStateStore,
    private val queueState: PlaybackQueueState,
    private val itemMapper: Media3ItemMapper,
    private val decisionCalculator: DeletedMediaDecisionCalculator
) {
    private companion object {
        private const val TAG = "DeletedMediaReconciler"
    }

    private data class ControllerStateBeforeDeletion(
        val originalItems: List<MediaItem>,
        val currentIndex: Int,
        val currentPosition: Long,
        val playWhenReady: Boolean
    )

    private data class ActiveReplacement(
        val kind: ActiveMediaKind,
        val item: MediaItem,
        val targetIndex: Int
    )

    fun reconcile(paths: List<String>, player: Player) {
        val originalItems = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        val stateBefore = stateStore.value
        val queueBefore = queueState.snapshot()
        val decision = decisionCalculator.calculate(
            deletedPaths = paths,
            snapshot = DeletedMediaSnapshot(
                controllerPaths = originalItems.map(MediaItem::mediaId),
                songs = queueBefore.songs,
                videos = queueBefore.videos,
                folderPath = queueBefore.folderPath,
                originalCurrentIndex = player.currentMediaItemIndex,
                currentSongPath = stateBefore.currentSong?.path,
                currentVideoPath = stateBefore.currentVideo?.path
            )
        ) ?: return
        val controllerState = ControllerStateBeforeDeletion(
            originalItems = originalItems,
            currentIndex = decision.originalCurrentIndex,
            currentPosition = player.currentPosition.coerceAtLeast(0L),
            playWhenReady = player.playWhenReady
        )
        val replacement = applyControllerMutation(decision, controllerState, player)

        if (player.mediaItemCount == 0) {
            queueState.clear()
            stateStore.update {
                it.copy(
                    isPlaying = false,
                    currentSong = null,
                    currentVideo = null,
                    currentPosition = 0L,
                    totalDuration = 0L
                )
            }
            return
        }

        queueState.replace(
            decision.remainingSongs,
            decision.remainingVideos,
            decision.folderPath
        )

        replacement ?: return
        when (replacement.kind) {
            ActiveMediaKind.VIDEO -> {
                val video = decision.remainingVideos.getOrNull(replacement.targetIndex)
                    ?: itemMapper.toVideo(replacement.item)
                stateStore.update {
                    it.copy(
                        isPlaying = controllerState.playWhenReady,
                        currentSong = null,
                        currentVideo = video,
                        currentPosition = 0L,
                        totalDuration = video.duration
                    )
                }
            }

            ActiveMediaKind.AUDIO -> {
                val song = decision.remainingSongs.getOrNull(replacement.targetIndex)
                    ?: itemMapper.toSong(replacement.item)
                stateStore.update {
                    it.copy(
                        isPlaying = controllerState.playWhenReady,
                        currentSong = song,
                        currentVideo = null,
                        currentPosition = 0L,
                        totalDuration = song.duration
                    )
                }
            }
        }
    }

    private fun applyControllerMutation(
        decision: DeletedMediaDecision,
        controllerState: ControllerStateBeforeDeletion,
        player: Player
    ): ActiveReplacement? {
        try {
            decision.indicesToRemove.asReversed().forEach(player::removeMediaItem)
            if (player.mediaItemCount == 0) {
                player.stop()
                player.clearMediaItems()
                return null
            }
            val deletedKind = decision.deletedActiveMedia ?: return null
            val targetIndex = checkNotNull(decision.targetIndex)
                .coerceIn(0, player.mediaItemCount - 1)
            val item = player.getMediaItemAt(targetIndex)
            player.seekTo(targetIndex, 0L)
            player.setPlayWhenReady(controllerState.playWhenReady)
            return ActiveReplacement(deletedKind, item, targetIndex)
        } catch (error: Throwable) {
            val rollbackError = restoreOriginalControllerState(controllerState, player)
            if (rollbackError != null) {
                error.addSuppressed(rollbackError)
                Timber.tag(TAG).e(rollbackError, "Delete rollback failed")
            }
            throw error
        }
    }

    private fun restoreOriginalControllerState(
        controllerState: ControllerStateBeforeDeletion,
        player: Player
    ): Throwable? {
        return runCatching {
            if (controllerState.originalItems.isEmpty()) {
                player.clearMediaItems()
                return@runCatching
            }
            val restoredIndex = controllerState.currentIndex
                .coerceIn(0, controllerState.originalItems.lastIndex)
            player.setMediaItems(
                controllerState.originalItems,
                restoredIndex,
                controllerState.currentPosition
            )
            player.setPlayWhenReady(controllerState.playWhenReady)
            player.prepare()
        }.exceptionOrNull()
    }
}
