package com.example.newaudio.feature.filebrowser

import com.example.newaudio.R
import com.example.newaudio.domain.model.MediaBrowserMode
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.repository.IMediaStoreObserverRepository
import com.example.newaudio.domain.usecase.file.GetParentPathUseCase
import com.example.newaudio.domain.usecase.file.GetRootPathUseCase
import com.example.newaudio.domain.usecase.file.GetSortedFileTreeUseCase
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.util.UiText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal class FileBrowserBindings(
    private val uiState: MutableStateFlow<FileBrowserUiState>,
    private val pathFlow: MutableStateFlow<String>,
    private val scope: CoroutineScope,

    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    private val mediaRepository: IMediaRepository,
    private val getSortedFileTreeUseCase: GetSortedFileTreeUseCase,
    private val getRootPathUseCase: GetRootPathUseCase,
    private val getParentPathUseCase: GetParentPathUseCase,
    private val mediaStoreObserverRepository: IMediaStoreObserverRepository,

    private val loadPath: (String, Boolean) -> Unit,
    private val onAutoRefresh: () -> Unit,
    private val mapRepeatMode: (Int) -> UserPreferences.RepeatMode,
    private val logTag: String
) {
    private var lastSongPath: String? = null
    private var lastVideoPath: String? = null
    private var lastRepeatModeRaw: Int = Int.MIN_VALUE

    private var pendingRestoreFolder: String? = null
    private var restoreApplied: Boolean = false

    fun bind() {
        bindSettings()
        bindRoot()
        bindPlaybackStateFiltered()
        bindFileTreeCollector()
        bindMediaStoreObserver()
    }

    private fun bindSettings() {
        getUserSettingsUseCase()
            .onEach { settings ->
                uiState.update {
                    it.copy(
                        oneHandedMode = settings.oneHandedMode,
                        playOnFolderClick = settings.playOnFolderClick,
                        resumeSessionOnModeSwitch = settings.resumeSessionOnModeSwitch,
                        showVideoPreviewItems = settings.showVideoPreviewItems,
                        videoDisplayMode = settings.videoDisplayMode,
                        videoGalleryColumns = settings.videoGalleryColumns,
                        showVideoNamesInGallery = settings.showVideoNamesInGallery,
                        transparentListItems = settings.transparentListItems
                    )
                }
            }
            .launchIn(scope)
    }

    private fun bindRoot() {
        scope.launch {
            val root = getRootPathUseCase(uiState.value.browserMode)
            Timber.tag(logTag).d("Initializing with root: %s", root)

            uiState.update {
                it.copy(
                    rootPath = root,
                    pathHistory = persistentListOf(),
                    canNavigateBack = false
                )
            }

            if (root.isNotEmpty()) {
                loadPath(root, false)
            } else {
                uiState.update { it.copy(errorRes = UiText.StringResource(R.string.select_other_folder), isLoading = false) }
            }

            tryApplyPendingRestore()
        }
    }

    private fun bindPlaybackStateFiltered() {
        mediaRepository.getPlaybackState()
            .distinctUntilChanged { old, new ->
                old.currentSong?.path == new.currentSong?.path &&
                        old.currentVideo?.path == new.currentVideo?.path &&
                        old.repeatMode == new.repeatMode &&
                        old.isRestoring == new.isRestoring
            }
            .onEach { ps ->
                val songPath = ps.currentSong?.path
                val videoPath = ps.currentVideo?.path
                val repeatRaw = ps.repeatMode

                val songChanged = songPath != lastSongPath
                val videoChanged = videoPath != lastVideoPath
                val repeatChanged = repeatRaw != lastRepeatModeRaw

                if (songChanged || videoChanged || repeatChanged) {
                    if (songChanged) lastSongPath = songPath
                    if (videoChanged) lastVideoPath = videoPath
                    if (repeatChanged) lastRepeatModeRaw = repeatRaw

                    uiState.update { state ->
                        state.copy(
                            activeSongPath = if (songChanged) songPath else state.activeSongPath,
                            activeVideoPath = if (videoChanged) videoPath else state.activeVideoPath,
                            repeatMode = if (repeatChanged) mapRepeatMode(repeatRaw) else state.repeatMode
                        )
                    }
                }

                if (ps.isRestoring && !restoreApplied && pendingRestoreFolder == null && songPath != null) {
                    val parent = getParentPathUseCase(songPath)
                    if (parent != null) {
                        Timber.tag(logTag).d("Captured restore folder: %s", parent)
                        pendingRestoreFolder = parent
                        tryApplyPendingRestore()
                    }
                }
            }
            .launchIn(scope)
    }

    private fun tryApplyPendingRestore() {
        if (restoreApplied) return

        val pending = pendingRestoreFolder ?: return
        val root = uiState.value.rootPath
        if (root.isBlank()) return

        if (pending.isNotEmpty() && pending != root) {
            Timber.tag(logTag).d("Applying restore navigation: %s", pending)
            loadPath(pending, false)
        }

        restoreApplied = true
        pendingRestoreFolder = null
    }

    private fun bindFileTreeCollector() {
        pathFlow
            .filter { it.isNotBlank() }
            .distinctUntilChanged()
            .flatMapLatest { path ->
                Timber.tag(logTag).d("New path observed: %s. Starting new file tree collection.", path)
                getSortedFileTreeUseCase(path, uiState.value.browserMode)
                    .map { Result.success(it) }
                    .catch { e ->
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Timber.tag(logTag).e(e, "Error in getSortedFileTreeUseCase flow for path: %s", path)
                        emit(Result.failure(e))
                    }
            }
            .onEach { result ->
                result.fold(
                    onSuccess = { fileItems ->
                        uiState.update {
                            it.copy(
                                isLoading = false,
                                fileItems = fileItems.toImmutableList()
                            )
                        }
                    },
                    onFailure = { e ->
                        Timber.tag(logTag).e(e, "Error collecting file tree")
                        uiState.update { it.copy(isLoading = false, errorRes = UiText.StringResource(R.string.error_loading)) }
                    }
                )
            }
            .launchIn(scope)
    }

    private fun bindMediaStoreObserver() {
        mediaStoreObserverRepository.observeAudioChanges()
            .onEach {
                if (uiState.value.browserMode == MediaBrowserMode.MUSIC) {
                    Timber.tag(logTag).d("Audio MediaStore change detected, refreshing current folder.")
                    onAutoRefresh()
                }
            }
            .launchIn(scope)

        mediaStoreObserverRepository.observeVideoChanges()
            .onEach {
                if (uiState.value.browserMode == MediaBrowserMode.VIDEO) {
                    Timber.tag(logTag).d("Video MediaStore change detected, refreshing current folder.")
                    onAutoRefresh()
                }
            }
            .launchIn(scope)
    }
}
