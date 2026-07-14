package com.example.newaudio.data.repository

import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.example.newaudio.data.media.controller.MediaControllerGateway
import com.example.newaudio.data.media.controller.MediaControllerUnavailableException
import com.example.newaudio.data.media.deletion.DeletedMediaReconciler
import com.example.newaudio.data.media.library.MediaLibraryRepository
import com.example.newaudio.data.media.mapping.Media3ItemMapper
import com.example.newaudio.data.media.playback.ControllerPlaybackSnapshot
import com.example.newaudio.data.media.playback.PlaybackQueueState
import com.example.newaudio.data.media.playback.PlaybackSessionCoordinator
import com.example.newaudio.data.media.playback.PlaybackStateStore
import com.example.newaudio.data.media.playback.PlaybackTransitionCoordinator
import com.example.newaudio.domain.model.PlaybackSessionSnapshot
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.repository.ISettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

/**
 * Stable IMediaRepository facade. Media3 lifecycle, library access, session
 * snapshots, queue reconciliation and mapping are owned by dedicated classes.
 */
@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val settingsRepository: ISettingsRepository,
    private val stateStore: PlaybackStateStore,
    private val queueState: PlaybackQueueState,
    private val itemMapper: Media3ItemMapper,
    private val controllerGateway: MediaControllerGateway,
    private val sessionCoordinator: PlaybackSessionCoordinator,
    private val transitionCoordinator: PlaybackTransitionCoordinator,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val deletedMediaReconciler: DeletedMediaReconciler
) : IMediaRepository {

    private companion object {
        private const val TAG = "MediaRepository"
    }

    override fun getPlaybackState(): Flow<IMediaRepository.PlaybackState> = stateStore.state

    override suspend fun initialize() {
        runRequiredControllerAction { Unit }
    }

    override suspend fun playPlaylist(songs: List<Song>, startIndex: Int, folderPath: String?) {
        if (songs.isEmpty()) return
        val preferences = settingsRepository.userPreferences.firstOrNull()
        runRequiredControllerAction { controller ->
            val transition = transitionCoordinator.capture(controller)
            val pendingMusicSession = transition.musicSession
            sessionCoordinator.captureBeforeMusicPlayback(
                stateStore.value,
                controller.toPlaybackSnapshot()
            )
            val start = sessionCoordinator.previewMusicStart(songs, startIndex, folderPath)
            try {
                applyPlaybackPreferences(controller, preferences)
                controller.setMediaItems(
                    songs.map(itemMapper::toMediaItem),
                    start.index,
                    start.positionMs
                )
                controller.setPlayWhenReady(true)
                controller.prepare()
                controller.play()
            } catch (error: Throwable) {
                transitionCoordinator.rollback(controller, transition, error)
                throw error
            }
            queueState.setMusic(songs, folderPath)
            stateStore.update {
                it.copy(
                    isRestoring = false,
                    isPlaying = controller.isPlaying,
                    currentSong = songs[start.index],
                    currentVideo = null,
                    currentPosition = start.positionMs,
                    totalDuration = songs[start.index].duration,
                    playerError = null
                )
            }
            sessionCoordinator.consumeMusicSession(pendingMusicSession)
        }
    }

    override suspend fun playVideoPlaylist(videos: List<Video>, startIndex: Int, folderPath: String?) {
        if (videos.isEmpty()) return
        val preferences = settingsRepository.userPreferences.firstOrNull()
        runRequiredControllerAction { controller ->
            val transition = transitionCoordinator.capture(controller)
            val pendingVideoSession = transition.videoSession
            sessionCoordinator.captureBeforeVideoPlayback(
                stateStore.value,
                controller.toPlaybackSnapshot()
            )
            val start = sessionCoordinator.previewVideoStart(videos, startIndex, folderPath)
            try {
                applyPlaybackPreferences(controller, preferences)
                controller.setMediaItems(
                    videos.map(itemMapper::toMediaItem),
                    start.index,
                    start.positionMs
                )
                controller.setPlayWhenReady(true)
                controller.prepare()
                controller.play()
            } catch (error: Throwable) {
                transitionCoordinator.rollback(controller, transition, error)
                throw error
            }
            queueState.setVideos(videos, folderPath)
            stateStore.update {
                it.copy(
                    isRestoring = false,
                    isPlaying = controller.isPlaying,
                    currentSong = null,
                    currentVideo = videos[start.index],
                    currentPosition = start.positionMs,
                    totalDuration = videos[start.index].duration,
                    playerError = null
                )
            }
            sessionCoordinator.consumeVideoSession(pendingVideoSession)
        }
    }

    override suspend fun resumeLastMusicSession(): Boolean {
        val pending = sessionCoordinator.peekMusicSession() ?: return false
        if (pending.songs.isEmpty()) return false

        return runRequiredControllerAction { controller ->
            val transition = transitionCoordinator.capture(controller)
            sessionCoordinator.captureBeforeMusicPlayback(
                stateStore.value,
                controller.toPlaybackSnapshot()
            )
            try {
                applyMusicSessionToController(controller, pending)
            } catch (error: Throwable) {
                transitionCoordinator.rollback(controller, transition, error)
                throw error
            }
            publishMusicSession(pending)
            sessionCoordinator.consumeMusicSession(pending)
            true
        } == true
    }

    override suspend fun resumeLastVideoSession(): Boolean {
        val pending = sessionCoordinator.peekVideoSession() ?: return false
        if (pending.videos.isEmpty()) return false

        return runRequiredControllerAction { controller ->
            val transition = transitionCoordinator.capture(controller)
            sessionCoordinator.captureBeforeVideoPlayback(
                stateStore.value,
                controller.toPlaybackSnapshot()
            )
            try {
                applyVideoSessionToController(controller, pending)
            } catch (error: Throwable) {
                transitionCoordinator.rollback(controller, transition, error)
                throw error
            }
            publishVideoSession(pending)
            sessionCoordinator.consumeVideoSession(pending)
            true
        } == true
    }

    override suspend fun restorePlaylist(
        songs: List<Song>,
        startIndex: Int,
        startPosition: Long,
        folderPath: String?
    ) {
        if (songs.isEmpty()) return
        runRequiredControllerAction { controller ->
            val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
            val safePosition = startPosition.coerceAtLeast(0L)
            val transition = transitionCoordinator.capture(controller)
            try {
                controller.setMediaItems(songs.map(itemMapper::toMediaItem), safeIndex, safePosition)
                controller.prepare()
            } catch (error: Throwable) {
                transitionCoordinator.rollback(controller, transition, error)
                throw error
            }
            queueState.setMusic(songs, folderPath)
            stateStore.update {
                it.copy(
                    currentSong = songs[safeIndex],
                    currentVideo = null,
                    currentPosition = safePosition,
                    isRestoring = false
                )
            }
        }
    }

    override suspend fun getLibrarySongCount(): Int = mediaLibraryRepository.getSongCount()

    override suspend fun getLibraryVideoCount(): Int = mediaLibraryRepository.getVideoCount()

    override suspend fun ensureSongInLibraryAndGetParentPath(songPath: String): String? {
        return mediaLibraryRepository.ensureSongAndGetParentPath(songPath)
    }

    override suspend fun getSongsInFolder(parentPath: String): List<Song> {
        return mediaLibraryRepository.getSongsInFolder(parentPath)
    }

    override suspend fun getVideosInFolder(parentPath: String): List<Video> {
        return mediaLibraryRepository.getVideosInFolder(parentPath)
    }

    override suspend fun togglePlayback() {
        controllerGateway.withControllerOrNull { controller ->
            if (controller.isPlaying) controller.pause() else controller.play()
        }
    }

    override suspend fun toggleShuffle() {
        controllerGateway.withControllerOrNull { controller ->
            controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        }
    }

    override suspend fun setShuffleEnabled(enabled: Boolean) {
        controllerGateway.withControllerOrNull { it.shuffleModeEnabled = enabled }
    }

    override suspend fun setRepeatMode(repeatMode: UserPreferences.RepeatMode) {
        controllerGateway.withControllerOrNull { it.repeatMode = repeatMode.toPlayerRepeatMode() }
    }

    override suspend fun skipNext() {
        controllerGateway.withControllerOrNull { controller ->
            if (stateStore.value.currentVideo != null && controller.mediaItemCount > 0) {
                val currentIndex = controller.currentMediaItemIndex
                    .takeIf { it in 0 until controller.mediaItemCount }
                    ?: 0
                controller.seekTo((currentIndex + 1) % controller.mediaItemCount, 0L)
            } else {
                controller.seekToNextMediaItem()
            }
        }
    }

    override suspend fun skipPrevious() {
        controllerGateway.withControllerOrNull { controller ->
            if (stateStore.value.currentVideo != null && controller.mediaItemCount > 0) {
                val currentIndex = controller.currentMediaItemIndex
                    .takeIf { it in 0 until controller.mediaItemCount }
                    ?: 0
                val previousIndex = if (currentIndex <= 0) {
                    controller.mediaItemCount - 1
                } else {
                    currentIndex - 1
                }
                controller.seekTo(previousIndex, 0L)
            } else {
                controller.seekToPreviousMediaItem()
            }
        }
    }

    override suspend fun seekTo(position: Long) {
        controllerGateway.withControllerOrNull { it.seekTo(position) }
    }

    override suspend fun removeDeletedMedia(paths: List<String>) {
        if (paths.none { it.isNotBlank() }) return
        controllerGateway.withControllerOrNull { deletedMediaReconciler.reconcile(paths, it) }
    }

    override suspend fun clearPlayerError() {
        stateStore.clearPlayerError()
    }

    override suspend fun clearDatabase() {
        mediaLibraryRepository.clearDatabase()
    }

    private suspend fun <T> runRequiredControllerAction(
        block: suspend (MediaController) -> T
    ): T? {
        return try {
            controllerGateway.requireController(block)
        } catch (error: MediaControllerUnavailableException) {
            // The gateway already published a user-visible playback error.
            // Expected service outages remain recoverable; all other failures
            // continue to propagate to the caller.
            Timber.tag(TAG).w(error, "Required controller operation was skipped")
            null
        }
    }

    private fun applyMusicSessionToController(
        controller: Player,
        session: PlaybackSessionSnapshot.MusicSession
    ) {
        val index = session.currentIndex.coerceIn(0, session.songs.lastIndex)
        val position = session.positionMs.coerceAtLeast(0L)
        controller.setMediaItems(session.songs.map(itemMapper::toMediaItem), index, position)
        controller.setPlayWhenReady(session.wasPlaying)
        controller.prepare()
        if (session.wasPlaying) controller.play() else controller.pause()
    }

    private fun publishMusicSession(session: PlaybackSessionSnapshot.MusicSession) {
        val index = session.currentIndex.coerceIn(0, session.songs.lastIndex)
        val position = session.positionMs.coerceAtLeast(0L)
        queueState.setMusic(session.songs, session.folderPath)
        stateStore.update {
            it.copy(
                isRestoring = false,
                isPlaying = session.wasPlaying,
                currentSong = session.songs[index],
                currentVideo = null,
                currentPosition = position,
                totalDuration = session.songs[index].duration,
                playerError = null
            )
        }
    }

    private fun applyVideoSessionToController(
        controller: Player,
        session: PlaybackSessionSnapshot.VideoSession
    ) {
        val index = session.currentIndex.coerceIn(0, session.videos.lastIndex)
        val position = session.positionMs.coerceAtLeast(0L)
        controller.setMediaItems(session.videos.map(itemMapper::toMediaItem), index, position)
        controller.setPlayWhenReady(session.wasPlaying)
        controller.prepare()
        if (session.wasPlaying) controller.play() else controller.pause()
    }

    private fun publishVideoSession(session: PlaybackSessionSnapshot.VideoSession) {
        val index = session.currentIndex.coerceIn(0, session.videos.lastIndex)
        val position = session.positionMs.coerceAtLeast(0L)
        queueState.setVideos(session.videos, session.folderPath)
        stateStore.update {
            it.copy(
                isRestoring = false,
                isPlaying = session.wasPlaying,
                currentSong = null,
                currentVideo = session.videos[index],
                currentPosition = position,
                totalDuration = session.videos[index].duration,
                playerError = null
            )
        }
    }

    private fun applyPlaybackPreferences(player: Player, preferences: UserPreferences?) {
        if (preferences == null) return
        player.shuffleModeEnabled = preferences.isShuffleEnabled
        player.repeatMode = preferences.repeatMode.toPlayerRepeatMode()
    }

    private fun UserPreferences.RepeatMode.toPlayerRepeatMode(): Int {
        return when (this) {
            UserPreferences.RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            UserPreferences.RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            UserPreferences.RepeatMode.NONE -> Player.REPEAT_MODE_OFF
        }
    }

    private fun Player.toPlaybackSnapshot(): ControllerPlaybackSnapshot {
        return ControllerPlaybackSnapshot(
            currentIndex = currentMediaItemIndex,
            currentPosition = currentPosition,
            playWhenReady = playWhenReady
        )
    }

}
