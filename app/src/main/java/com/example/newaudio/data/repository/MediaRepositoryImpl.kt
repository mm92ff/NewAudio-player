package com.example.newaudio.data.repository

import android.content.ComponentName
import android.content.Context
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
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.di.MainDispatcher
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
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
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: ISettingsRepository,
    private val mediaScannerRepository: IMediaScannerRepository,
    private val songDao: SongDao,
    private val appDatabase: AppDatabase,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IMediaRepository {

    private companion object {
        private const val TAG = "MediaRepository"
    }

    private val _playbackState = MutableStateFlow(IMediaRepository.PlaybackState())
    override fun getPlaybackState(): Flow<IMediaRepository.PlaybackState> = _playbackState.asStateFlow()

    private var mediaController: MediaController? = null
    private val repoScope = CoroutineScope(mainDispatcher + SupervisorJob())

    private var listenerDelegate: PlayerListenerDelegate? = null

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

            _playbackState.update { it.copy(isRestoring = false) }
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
        listenerDelegate?.currentPlaylist = songs
        listenerDelegate?.currentFolderPath = folderPath

        val mediaItems = songs.map { it.toMediaItem() }
        withContext(mainDispatcher) {
            _playbackState.update { it.copy(isRestoring = false) }
            controller.setMediaItems(mediaItems, startIndex, 0L)
            controller.prepare()
            controller.play()
        }
    }

    override suspend fun restorePlaylist(songs: List<Song>, startIndex: Int, startPosition: Long, folderPath: String?) {
        val controller = controllerOrNull() ?: return
        listenerDelegate?.currentPlaylist = songs
        listenerDelegate?.currentFolderPath = folderPath

        val mediaItems = songs.map { it.toMediaItem() }
        withContext(mainDispatcher) {
            controller.setMediaItems(mediaItems, startIndex, startPosition)
            controller.prepare()
            _playbackState.update {
                it.copy(
                    currentSong = songs.getOrNull(startIndex),
                    currentPosition = startPosition,
                    isRestoring = false
                )
            }
        }
    }

    override suspend fun getLibrarySongCount(): Int = withContext(ioDispatcher) {
        songDao.countAllSongs()
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
                    title = it.title,
                    artist = it.artist,
                    duration = it.duration,
                    albumArtPath = it.albumArtPath
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
        withContext(mainDispatcher) { controller.seekToNextMediaItem() }
    }

    override suspend fun skipPrevious() {
        val controller = controllerOrNull() ?: return
        withContext(mainDispatcher) { controller.seekToPreviousMediaItem() }
    }

    override suspend fun seekTo(position: Long) {
        val controller = controllerOrNull() ?: return
        withContext(mainDispatcher) { controller.seekTo(position) }
    }

    override suspend fun clearPlayerError() {
        _playbackState.update { it.copy(playerError = null) }
    }

    override suspend fun clearDatabase() {
        withContext(ioDispatcher) {
            appDatabase.clearAllTables()
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(this.title)
            .setArtist(this.artist)
            .build()

        return MediaItem.Builder()
            // ✅ FIX: We use contentUri (when available) instead of just the path.
            // .toUri() correctly parses strings like "content://...".
            .setUri(this.contentUri.toUri())
            .setMediaId(this.path)
            .setMediaMetadata(metadata)
            .build()
    }
}