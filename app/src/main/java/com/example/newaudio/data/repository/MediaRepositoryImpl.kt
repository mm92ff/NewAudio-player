package com.example.newaudio.data.repository

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.newaudio.data.audio.PlayerListenerDelegate
import com.example.newaudio.data.database.AppDatabase
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.SongMinimal
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.data.database.VideoMinimal
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.di.MainDispatcher
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.PlaybackSessionSnapshot
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.model.resolveMusicSessionStart
import com.example.newaudio.domain.model.resolveVideoSessionStart
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.repository.IMediaScannerRepository
import com.example.newaudio.domain.repository.ISettingsRepository
import com.example.newaudio.service.MediaPlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: ISettingsRepository,
    private val mediaScannerRepository: IMediaScannerRepository,
    private val songDao: SongDao,
    private val videoDao: VideoDao,
    private val appDatabase: AppDatabase,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IMediaRepository {

    private companion object {
        private const val TAG = "MediaRepository"
        private const val MEDIA_TYPE_KEY = "com.example.newaudio.MEDIA_TYPE"
        private const val MEDIA_TYPE_AUDIO = "audio"
        private const val MEDIA_TYPE_VIDEO = "video"

        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "oga", "opus", "wma"
        )
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "3gp", "3gpp"
        )
    }

    private val _playbackState = MutableStateFlow(IMediaRepository.PlaybackState())
    override fun getPlaybackState(): Flow<IMediaRepository.PlaybackState> = _playbackState.asStateFlow()

    private var mediaController: MediaController? = null
    private val repoScope = CoroutineScope(mainDispatcher + SupervisorJob())

    private var listenerDelegate: PlayerListenerDelegate? = null
    private var lastMusicSession: PlaybackSessionSnapshot.MusicSession? = null
    private var lastVideoSession: PlaybackSessionSnapshot.VideoSession? = null

    private val initMutex = Mutex()
    @Volatile
    private var initStarted = false
    private var initDone = CompletableDeferred<Unit>()

    override suspend fun initialize() {
        initMutex.withLock {
            if (initStarted) return
            initStarted = true
        }

        try {
            if (initDone.isCompleted) {
                initDone = CompletableDeferred()
            }

            val sessionToken = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))
            val controller = MediaController.Builder(context, sessionToken)
                .buildAsync()
                .await()

            mediaController = controller

            val delegate = PlayerListenerDelegate(
                context = context,
                playbackState = _playbackState,
                settingsRepository = settingsRepository,
                player = controller,
                coroutineScope = repoScope,
                ioDispatcher = ioDispatcher
            )
            listenerDelegate = delegate
            controller.addListener(delegate)

            if (!syncPlaybackStateFromController(controller, delegate)) {
                _playbackState.update { it.copy(isRestoring = false, player = controller) }
            }
            initDone.complete(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Controller initialization failed")
            _playbackState.update { it.copy(isRestoring = false) }
            initDone.completeExceptionally(e)

            initMutex.withLock {
                initStarted = false
            }
            throw e
        }
    }

    private suspend fun awaitInitialized() {
        if (!initStarted) {
            try {
                initialize()
            } catch (e: Exception) {
                throw e
            }
        }
        initDone.await()
    }

    private suspend fun controllerOrNull(): MediaController? {
        return try {
            awaitInitialized()
            mediaController
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Cannot get controller")
            null
        }
    }

    override suspend fun playPlaylist(songs: List<Song>, startIndex: Int, folderPath: String?) {
        val controller = controllerOrNull() ?: return
        if (songs.isEmpty()) return

        val mediaItems = songs.map { it.toMediaItem() }
        withContext(mainDispatcher) {
            captureActiveSessionBeforeMusicPlayback(controller)
            val startPosition = lastMusicSession.resolveMusicSessionStart(songs, startIndex, folderPath)

            listenerDelegate?.currentPlaylist = songs
            listenerDelegate?.currentVideoPlaylist = emptyList()
            listenerDelegate?.currentFolderPath = folderPath
            lastMusicSession = null

            _playbackState.update { it.copy(isRestoring = false, currentVideo = null) }
            controller.setMediaItems(mediaItems, startPosition.index, startPosition.positionMs)
            controller.prepare()
            controller.play()
        }
    }

    override suspend fun playVideoPlaylist(videos: List<Video>, startIndex: Int, folderPath: String?) {
        val controller = controllerOrNull() ?: return
        if (videos.isEmpty()) return

        val mediaItems = videos.map { it.toMediaItem() }
        withContext(mainDispatcher) {
            captureActiveSessionBeforeVideoPlayback(controller)
            val startPosition = lastVideoSession.resolveVideoSessionStart(videos, startIndex, folderPath)

            listenerDelegate?.currentPlaylist = emptyList()
            listenerDelegate?.currentVideoPlaylist = videos
            listenerDelegate?.currentFolderPath = folderPath
            lastVideoSession = null

            _playbackState.update { it.copy(isRestoring = false, currentSong = null) }
            controller.setMediaItems(mediaItems, startPosition.index, startPosition.positionMs)
            controller.prepare()
            controller.play()
        }
    }

    override suspend fun resumeLastMusicSession(): Boolean {
        val session = lastMusicSession ?: return false
        if (session.songs.isEmpty()) return false
        val controller = controllerOrNull() ?: return false

        return withContext(mainDispatcher) {
            captureActiveSessionBeforeMusicPlayback(controller)

            val index = session.currentIndex.coerceIn(0, session.songs.lastIndex)
            val mediaItems = session.songs.map { it.toMediaItem() }
            listenerDelegate?.currentPlaylist = session.songs
            listenerDelegate?.currentVideoPlaylist = emptyList()
            listenerDelegate?.currentFolderPath = session.folderPath
            lastMusicSession = null

            controller.setMediaItems(mediaItems, index, session.positionMs.coerceAtLeast(0L))
            controller.setPlayWhenReady(session.wasPlaying)
            controller.prepare()
            if (session.wasPlaying) {
                controller.play()
            } else {
                controller.pause()
            }

            _playbackState.update {
                it.copy(
                    isRestoring = false,
                    isPlaying = session.wasPlaying,
                    currentSong = session.songs.getOrNull(index),
                    currentVideo = null,
                    currentPosition = session.positionMs.coerceAtLeast(0L),
                    totalDuration = session.songs.getOrNull(index)?.duration ?: 0L,
                    playerError = null
                )
            }
            true
        }
    }

    override suspend fun resumeLastVideoSession(): Boolean {
        val session = lastVideoSession ?: return false
        if (session.videos.isEmpty()) return false
        val controller = controllerOrNull() ?: return false

        return withContext(mainDispatcher) {
            captureActiveSessionBeforeVideoPlayback(controller)

            val index = session.currentIndex.coerceIn(0, session.videos.lastIndex)
            val mediaItems = session.videos.map { it.toMediaItem() }
            listenerDelegate?.currentPlaylist = emptyList()
            listenerDelegate?.currentVideoPlaylist = session.videos
            listenerDelegate?.currentFolderPath = session.folderPath
            lastVideoSession = null

            controller.setMediaItems(mediaItems, index, session.positionMs.coerceAtLeast(0L))
            controller.setPlayWhenReady(session.wasPlaying)
            controller.prepare()
            if (session.wasPlaying) {
                controller.play()
            } else {
                controller.pause()
            }

            _playbackState.update {
                it.copy(
                    isRestoring = false,
                    isPlaying = session.wasPlaying,
                    currentSong = null,
                    currentVideo = session.videos.getOrNull(index),
                    currentPosition = session.positionMs.coerceAtLeast(0L),
                    totalDuration = session.videos.getOrNull(index)?.duration ?: 0L,
                    playerError = null
                )
            }
            true
        }
    }

    override suspend fun restorePlaylist(songs: List<Song>, startIndex: Int, startPosition: Long, folderPath: String?) {
        val controller = controllerOrNull() ?: return
        listenerDelegate?.currentPlaylist = songs
        listenerDelegate?.currentVideoPlaylist = emptyList()
        listenerDelegate?.currentFolderPath = folderPath

        val mediaItems = songs.map { it.toMediaItem() }
        withContext(mainDispatcher) {
            controller.setMediaItems(mediaItems, startIndex, startPosition)
            controller.prepare()
            _playbackState.update {
                it.copy(
                    currentSong = songs.getOrNull(startIndex),
                    currentVideo = null,
                    currentPosition = startPosition,
                    isRestoring = false
                )
            }
        }
    }

    override suspend fun getLibrarySongCount(): Int = withContext(ioDispatcher) {
        songDao.countAllSongs()
    }

    override suspend fun getLibraryVideoCount(): Int = withContext(ioDispatcher) {
        videoDao.countAllVideos()
    }

    override suspend fun ensureSongInLibraryAndGetParentPath(songPath: String): String? {
        return withContext(ioDispatcher) {
            var dbSong = songDao.getSongByPath(songPath)
            if (dbSong == null) {
                mediaScannerRepository.scanSingleFile(songPath)
                dbSong = songDao.getSongByPath(songPath)
            }
            dbSong?.parentPath ?: File(songPath).parent
        }
    }

    override suspend fun getSongsInFolder(parentPath: String): List<Song> {
        return withContext(ioDispatcher) {
            val songsInFolder: List<SongMinimal> = songDao.observeSongsInFolderMinimal(parentPath)
                .firstOrNull()
                .orEmpty()

            songsInFolder.map {
                Song(
                    path = it.path,
                    contentUri = it.contentUri, // ✅ FIX: contentUri is passed
                    title = it.title.takeIf { title -> title.isNotBlank() }
                        ?: File(it.path).nameWithoutExtension.ifBlank { "Unknown Title" },
                    artist = it.artist,
                    duration = it.duration,
                    albumArtPath = it.albumArtPath
                )
            }
        }
    }

    override suspend fun getVideosInFolder(parentPath: String): List<Video> {
        return withContext(ioDispatcher) {
            val videosInFolder: List<VideoMinimal> = videoDao.observeVideosInFolderMinimal(parentPath)
                .firstOrNull()
                .orEmpty()

            videosInFolder.map {
                Video(
                    path = it.path,
                    contentUri = it.contentUri,
                    title = it.title.takeIf { title -> title.isNotBlank() }
                        ?: File(it.path).nameWithoutExtension.ifBlank { "Unknown Video" },
                    duration = it.duration,
                    thumbnailUri = it.thumbnailUri,
                    width = it.width,
                    height = it.height
                )
            }
        }
    }

    override suspend fun togglePlayback() {
        val controller = controllerOrNull() ?: return
        withContext(mainDispatcher) {
            if (controller.isPlaying) controller.pause() else controller.play()
        }
    }

    override suspend fun toggleShuffle() {
        val controller = controllerOrNull() ?: return
        withContext(mainDispatcher) {
            controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        }
    }

    override suspend fun setShuffleEnabled(enabled: Boolean) {
        val controller = controllerOrNull() ?: return
        withContext(mainDispatcher) {
            controller.shuffleModeEnabled = enabled
        }
    }

    override suspend fun setRepeatMode(repeatMode: UserPreferences.RepeatMode) {
        val controller = controllerOrNull() ?: return
        withContext(mainDispatcher) {
            controller.repeatMode = when (repeatMode) {
                UserPreferences.RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                UserPreferences.RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                UserPreferences.RepeatMode.NONE -> Player.REPEAT_MODE_OFF
            }
        }
    }

    override suspend fun skipNext() {
        val controller = controllerOrNull() ?: return
        withContext(mainDispatcher) {
            if (_playbackState.value.currentVideo != null && controller.mediaItemCount > 0) {
                val currentIndex = controller.currentMediaItemIndex
                    .takeIf { it in 0 until controller.mediaItemCount }
                    ?: 0
                val nextIndex = (currentIndex + 1) % controller.mediaItemCount
                controller.seekTo(nextIndex, 0L)
            } else {
                controller.seekToNextMediaItem()
            }
        }
    }

    override suspend fun skipPrevious() {
        val controller = controllerOrNull() ?: return
        withContext(mainDispatcher) {
            if (_playbackState.value.currentVideo != null && controller.mediaItemCount > 0) {
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
        val controller = controllerOrNull() ?: return
        withContext(mainDispatcher) { controller.seekTo(position) }
    }

    override suspend fun removeDeletedMedia(paths: List<String>) {
        val controller = controllerOrNull() ?: return
        val normalizedPaths = paths
            .map { it.removeSuffix("/") }
            .filter { it.isNotBlank() }
        if (normalizedPaths.isEmpty()) return

        withContext(mainDispatcher) {
            val delegate = listenerDelegate ?: return@withContext
            val deletedCurrentSong = _playbackState.value.currentSong?.path
                ?.let { path -> normalizedPaths.any { deleted -> path.isDeletedBy(deleted) } }
                ?: false
            val deletedCurrentVideo = _playbackState.value.currentVideo?.path
                ?.let { path -> normalizedPaths.any { deleted -> path.isDeletedBy(deleted) } }
                ?: false
            val wasPlaying = controller.isPlaying
            val currentIndex = controller.currentMediaItemIndex
                .takeIf { it in 0 until controller.mediaItemCount }
                ?: 0

            delegate.currentPlaylist = delegate.currentPlaylist
                .filterNot { song -> normalizedPaths.any { deleted -> song.path.isDeletedBy(deleted) } }
            delegate.currentVideoPlaylist = delegate.currentVideoPlaylist
                .filterNot { video -> normalizedPaths.any { deleted -> video.path.isDeletedBy(deleted) } }

            val indicesToRemove = (0 until controller.mediaItemCount)
                .filter { index ->
                    normalizedPaths.any { deleted ->
                        controller.getMediaItemAt(index).mediaId.isDeletedBy(deleted)
                    }
                }

            indicesToRemove.asReversed().forEach { index ->
                controller.removeMediaItem(index)
            }

            if (controller.mediaItemCount == 0) {
                controller.stop()
                controller.clearMediaItems()
                _playbackState.update {
                    it.copy(
                        isPlaying = false,
                        currentSong = null,
                        currentVideo = null,
                        currentPosition = 0L,
                        totalDuration = 0L
                    )
                }
                return@withContext
            }

            if (deletedCurrentVideo || deletedCurrentSong) {
                val targetIndex = currentIndex.coerceAtMost(controller.mediaItemCount - 1)
                controller.seekTo(targetIndex, 0L)
                if (wasPlaying) {
                    controller.play()
                } else {
                    controller.pause()
                }

                val currentItem = controller.getMediaItemAt(targetIndex)
                if (deletedCurrentVideo) {
                    val currentVideo = delegate.currentVideoPlaylist.getOrNull(targetIndex)
                        ?: currentItem.toVideo()
                    _playbackState.update {
                        it.copy(
                            isPlaying = wasPlaying,
                            currentSong = null,
                            currentVideo = currentVideo,
                            currentPosition = 0L,
                            totalDuration = currentVideo.duration
                        )
                    }
                } else {
                    val currentSong = delegate.currentPlaylist.getOrNull(targetIndex)
                        ?: currentItem.toSong()
                    _playbackState.update {
                        it.copy(
                            isPlaying = wasPlaying,
                            currentSong = currentSong,
                            currentVideo = null,
                            currentPosition = 0L,
                            totalDuration = currentSong.duration
                        )
                    }
                }
            }
        }
    }

    override suspend fun clearPlayerError() {
        _playbackState.update { it.copy(playerError = null) }
    }

    override suspend fun clearDatabase() {
        withContext(ioDispatcher) {
            appDatabase.clearAllTables()
        }
    }

    private suspend fun syncPlaybackStateFromController(
        player: Player,
        delegate: PlayerListenerDelegate
    ): Boolean {
        val currentItem = player.currentMediaItem ?: return false
        val mediaItems = (0 until player.mediaItemCount).map { index -> player.getMediaItemAt(index) }
        if (mediaItems.isEmpty()) return false

        val mediaType = resolveMediaType(currentItem)
        val currentIndex = player.currentMediaItemIndex
            .takeIf { it in mediaItems.indices }
            ?: 0
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val duration = if (player.duration > 0L) player.duration else 0L

        return when (mediaType) {
            MEDIA_TYPE_VIDEO -> {
                val videos = mediaItems.toVideos()
                val currentVideo = videos.getOrNull(currentIndex)
                    ?: currentItem.toVideo()

                delegate.currentPlaylist = emptyList()
                delegate.currentVideoPlaylist = videos.ifEmpty { listOf(currentVideo) }
                delegate.currentFolderPath = File(currentVideo.path).parent

                _playbackState.update {
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
            MEDIA_TYPE_AUDIO -> {
                val songs = mediaItems.toSongs()
                val currentSong = songs.getOrNull(currentIndex)
                    ?: currentItem.toSong()

                delegate.currentPlaylist = songs.ifEmpty { listOf(currentSong) }
                delegate.currentVideoPlaylist = emptyList()
                delegate.currentFolderPath = File(currentSong.path).parent

                _playbackState.update {
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
            else -> false
        }
    }

    private suspend fun resolveMediaType(mediaItem: MediaItem): String? {
        mediaItem.mediaMetadata.extras?.getString(MEDIA_TYPE_KEY)?.let { return it }

        val path = mediaItem.mediaId
        return withContext(ioDispatcher) {
            when {
                videoDao.getVideoByPath(path) != null -> MEDIA_TYPE_VIDEO
                songDao.getSongByPath(path) != null -> MEDIA_TYPE_AUDIO
                path.hasExtension(VIDEO_EXTENSIONS) -> MEDIA_TYPE_VIDEO
                path.hasExtension(AUDIO_EXTENSIONS) -> MEDIA_TYPE_AUDIO
                else -> null
            }
        }
    }

    private suspend fun List<MediaItem>.toSongs(): List<Song> {
        return withContext(ioDispatcher) {
            map { mediaItem ->
                songDao.getSongByPath(mediaItem.mediaId)?.toDomainModel()
                    ?: mediaItem.toSong()
            }
        }
    }

    private suspend fun List<MediaItem>.toVideos(): List<Video> {
        return withContext(ioDispatcher) {
            map { mediaItem ->
                videoDao.getVideoByPath(mediaItem.mediaId)?.toDomainModel()
                    ?: mediaItem.toVideo()
            }
        }
    }

    private fun captureActiveSessionBeforeMusicPlayback(player: Player) {
        if (_playbackState.value.currentVideo != null) {
            captureVideoSession(player)
        }
    }

    private fun captureActiveSessionBeforeVideoPlayback(player: Player) {
        if (_playbackState.value.currentSong != null) {
            captureMusicSession(player)
        }
    }

    private fun captureMusicSession(player: Player) {
        val delegate = listenerDelegate ?: return
        val playlist = delegate.currentPlaylist
        if (playlist.isEmpty()) return

        val currentPath = _playbackState.value.currentSong?.path
        val currentIndex = resolveCurrentIndex(
            playerIndex = player.currentMediaItemIndex,
            playlistSize = playlist.size,
            currentPath = currentPath,
            pathAt = { index -> playlist[index].path }
        ) ?: return
        val currentSong = playlist.getOrNull(currentIndex) ?: return

        lastMusicSession = PlaybackSessionSnapshot.MusicSession(
            songs = playlist,
            currentIndex = currentIndex,
            currentPath = currentSong.path,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            folderPath = delegate.currentFolderPath,
            wasPlaying = player.isPlaying
        )
    }

    private fun captureVideoSession(player: Player) {
        val delegate = listenerDelegate ?: return
        val playlist = delegate.currentVideoPlaylist
        if (playlist.isEmpty()) return

        val currentPath = _playbackState.value.currentVideo?.path
        val currentIndex = resolveCurrentIndex(
            playerIndex = player.currentMediaItemIndex,
            playlistSize = playlist.size,
            currentPath = currentPath,
            pathAt = { index -> playlist[index].path }
        ) ?: return
        val currentVideo = playlist.getOrNull(currentIndex) ?: return

        lastVideoSession = PlaybackSessionSnapshot.VideoSession(
            videos = playlist,
            currentIndex = currentIndex,
            currentPath = currentVideo.path,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            folderPath = delegate.currentFolderPath,
            wasPlaying = player.isPlaying
        )
    }

    private fun resolveCurrentIndex(
        playerIndex: Int,
        playlistSize: Int,
        currentPath: String?,
        pathAt: (Int) -> String
    ): Int? {
        if (playlistSize <= 0) return null
        if (playerIndex in 0 until playlistSize) return playerIndex

        return currentPath
            ?.let { path ->
                (0 until playlistSize).firstOrNull { index -> pathAt(index) == path }
            }
    }

    private fun Song.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(this.title.takeIf { it.isNotBlank() } ?: File(this.path).nameWithoutExtension.ifBlank { "Unknown Title" })
            .setArtist(this.artist)
            .setExtras(Bundle().apply { putString(MEDIA_TYPE_KEY, MEDIA_TYPE_AUDIO) })
            .build()

        return MediaItem.Builder()
            // ✅ FIX: We use contentUri (when available) instead of just the path.
            // .toUri() correctly parses strings like "content://...".
            .setUri(this.contentUri.toPlayableUri(this.path).toUri())
            .setMediaId(this.path)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun Video.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(this.title)
            .setExtras(Bundle().apply { putString(MEDIA_TYPE_KEY, MEDIA_TYPE_VIDEO) })
            .build()

        return MediaItem.Builder()
            .setUri(this.contentUri.toPlayableUri(this.path).toUri())
            .setMediaId(this.path)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun MediaItem.toSong(): Song {
        return Song(
            path = mediaId,
            contentUri = localConfiguration?.uri?.toString() ?: mediaId,
            title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
                ?: File(mediaId).nameWithoutExtension.ifBlank { "Unknown Title" },
            artist = mediaMetadata.artist?.toString() ?: "Unknown Artist",
            duration = 0L,
            albumArtPath = null
        )
    }

    private fun MediaItem.toVideo(): Video {
        return Video(
            path = mediaId,
            contentUri = localConfiguration?.uri?.toString() ?: mediaId,
            title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
                ?: File(mediaId).nameWithoutExtension.ifBlank { "Unknown Video" },
            duration = 0L,
            thumbnailUri = null
        )
    }

    private fun String.hasExtension(extensions: Set<String>): Boolean {
        val extension = substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
        return extension in extensions
    }

    private fun String.toPlayableUri(mediaPath: String): String {
        val contentUri = trim()
        val mediaFile = File(mediaPath)
        if (contentUri.isBlank()) return mediaPath
        if (!contentUri.startsWith("/")) return contentUri

        val contentFile = File(contentUri)
        return if (mediaFile.exists() && (!contentFile.exists() || contentFile.absolutePath != mediaFile.absolutePath)) {
            mediaPath
        } else {
            contentUri
        }
    }

    private fun String.isDeletedBy(deletedPath: String): Boolean {
        val path = removeSuffix("/")
        val deleted = deletedPath.removeSuffix("/")
        return path == deleted || path.startsWith("$deleted/")
    }
}
