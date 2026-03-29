package com.example.newaudio.feature.filebrowser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newaudio.R
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.repository.IMediaStoreObserverRepository
import com.example.newaudio.domain.repository.IPlaylistRepository
import com.example.newaudio.domain.usecase.file.CopyMultipleFilesUseCase // NEW
import com.example.newaudio.domain.usecase.file.DeleteFileUseCase
import com.example.newaudio.domain.usecase.file.DeleteMultipleFilesUseCase
import com.example.newaudio.domain.usecase.file.GetParentPathUseCase
import com.example.newaudio.domain.usecase.file.GetRootPathUseCase
import com.example.newaudio.domain.usecase.file.GetSortedFileTreeUseCase
import com.example.newaudio.domain.usecase.file.MoveMultipleFilesUseCase // NEW
import com.example.newaudio.domain.usecase.file.RenameFileUseCase
import com.example.newaudio.domain.usecase.file.SaveFolderOrderUseCase
import com.example.newaudio.domain.usecase.file.SyncCurrentFolderUseCase
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val getSortedFileTreeUseCase: GetSortedFileTreeUseCase,
    private val saveFolderOrderUseCase: SaveFolderOrderUseCase,
    private val deleteFileUseCase: DeleteFileUseCase,
    private val deleteMultipleFilesUseCase: DeleteMultipleFilesUseCase,
    private val renameFileUseCase: RenameFileUseCase,
    private val copyMultipleFilesUseCase: CopyMultipleFilesUseCase, // NEW
    private val moveMultipleFilesUseCase: MoveMultipleFilesUseCase, // NEW
    private val getRootPathUseCase: GetRootPathUseCase,
    private val getParentPathUseCase: GetParentPathUseCase,
    private val syncCurrentFolderUseCase: SyncCurrentFolderUseCase,
    private val mediaRepository: IMediaRepository,
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    private val mediaStoreObserverRepository: IMediaStoreObserverRepository,
    private val playlistRepository: IPlaylistRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        private const val DEBUG_TAG = "FileBrowserVM"
    }

    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private val _events = Channel<FileBrowserEvent>()
    val events: Flow<FileBrowserEvent> = _events.receiveAsFlow()

    private val pathFlow = MutableStateFlow("")
    private var refreshJob: Job? = null

    private val dialogController = FileBrowserDialogController(
        uiState = _uiState,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        deleteFileUseCase = deleteFileUseCase,
        deleteMultipleFilesUseCase = deleteMultipleFilesUseCase,
        renameFileUseCase = renameFileUseCase,
        playlistRepository = playlistRepository
    )

    private val clipboardController = FileBrowserClipboardController(
        uiState = _uiState,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        copyMultipleFilesUseCase = copyMultipleFilesUseCase, // NEW
        moveMultipleFilesUseCase = moveMultipleFilesUseCase  // NEW
    )

    private val reorderController = FileBrowserReorderController(
        uiState = _uiState,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        saveFolderOrderUseCase = saveFolderOrderUseCase
    )

    private val navigationController = FileBrowserNavigationController(
        uiState = _uiState,
        pathFlow = pathFlow,
        scope = viewModelScope,
        getRootPathUseCase = getRootPathUseCase,
        getParentPathUseCase = getParentPathUseCase,
        logTag = DEBUG_TAG
    )

    private val playbackController = FileBrowserPlaybackController(
        uiState = uiState,
        scope = viewModelScope,
        mediaRepository = mediaRepository,
        getSortedFileTreeUseCase = getSortedFileTreeUseCase,
        loadPath = navigationController::loadPath,
        logTag = DEBUG_TAG
    )

    private val bindings = FileBrowserBindings(
        uiState = _uiState,
        pathFlow = pathFlow,
        scope = viewModelScope,
        getUserSettingsUseCase = getUserSettingsUseCase,
        mediaRepository = mediaRepository,
        getSortedFileTreeUseCase = getSortedFileTreeUseCase,
        getRootPathUseCase = getRootPathUseCase,
        getParentPathUseCase = getParentPathUseCase,
        mediaStoreObserverRepository = mediaStoreObserverRepository,
        loadPath = navigationController::loadPath,
        onAutoRefresh = { onRefresh(isAutoRefresh = true) },
        mapRepeatMode = ::mapRepeatMode,
        logTag = DEBUG_TAG
    )

    init {
        bindings.bind()

        playlistRepository.getAllPlaylists()
            .onEach { playlists ->
                _uiState.update { it.copy(playlists = playlists.toImmutableList()) }
            }
            .launchIn(viewModelScope)
    }

    fun onRefresh(isAutoRefresh: Boolean = false) {
        val currentPath = _uiState.value.currentPath
        if (currentPath.isBlank()) return

        if (isAutoRefresh && (refreshJob?.isActive == true || _uiState.value.isRefreshing)) {
            return
        }

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (!isAutoRefresh) {
                _uiState.update { it.copy(isRefreshing = true, errorRes = null) }
            }

            try {
                withContext(ioDispatcher) {
                    syncCurrentFolderUseCase(currentPath)
                }
            } catch (e: Exception) {
                Timber.tag(DEBUG_TAG).e(e, "Error during refresh")
                if (!isAutoRefresh) {
                    _uiState.update { it.copy(errorRes = UiText.StringResource(R.string.error_loading)) }
                }
            } finally {
                if (!isAutoRefresh) {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    fun onToggleRepeatOne() = playbackController.onToggleRepeatOne()

    private fun mapRepeatMode(exoPlayerRepeatMode: Int): UserPreferences.RepeatMode {
        return when (exoPlayerRepeatMode) {
            1 -> UserPreferences.RepeatMode.ONE
            2 -> UserPreferences.RepeatMode.ALL
            else -> UserPreferences.RepeatMode.NONE
        }
    }

    fun checkPermissionsAndLoadRoot() = navigationController.checkPermissionsAndLoadRoot()
    fun loadPath(path: String, addToHistory: Boolean = true) = navigationController.loadPath(path, addToHistory)
    fun navigateUp() = navigationController.navigateUp()

    fun onFolderIconClicked(item: FileItem) {
        if (_uiState.value.isEditMode) {
            toggleSelection(item)
            return
        }
        if (item is FileItem.Folder) {
            if (_uiState.value.playOnFolderClick) {
                playbackController.playFolderWithoutNavigation(item)
            } else {
                loadPath(item.path, addToHistory = true)
            }
        }
    }

    fun onItemClicked(item: FileItem) {
        if (_uiState.value.isEditMode) {
            toggleSelection(item)
            return
        }
        when (item) {
            is FileItem.Folder -> {
                loadPath(item.path, addToHistory = true)
            }
            is FileItem.AudioFile -> playbackController.playAudioFile(item)
            is FileItem.OtherFile -> {}
        }
    }

    fun onShowDeleteDialog(file: FileItem) = dialogController.onShowDeleteDialog(file)
    fun onShowRenameDialog(file: FileItem) = dialogController.onShowRenameDialog(file)
    fun onShowAddToPlaylistDialog(file: FileItem.AudioFile) = dialogController.onShowAddToPlaylistDialog(file)
    fun onDismissDialog() = dialogController.onDismissDialog()
    fun onErrorShown() = dialogController.onErrorShown()
    fun onDeleteConfirmed(file: FileItem) = dialogController.onDeleteConfirmed(file)
    fun onDeleteMultipleConfirmed(files: List<FileItem>) = dialogController.onDeleteMultipleConfirmed(files)
    fun onRenameConfirmed(file: FileItem, newName: String) = dialogController.onRenameConfirmed(file, newName)
    fun onAddToPlaylistConfirmed(playlist: Playlist, file: FileItem.AudioFile) = dialogController.onAddToPlaylistConfirmed(playlist, file)
    fun onAddToPlaylistMultipleConfirmed(playlist: Playlist, files: List<FileItem.AudioFile>) = dialogController.onAddToPlaylistMultipleConfirmed(playlist, files)
    fun onCreatePlaylistAndAdd(name: String) = dialogController.onCreatePlaylistAndAdd(name)

    fun onCopyClick(file: FileItem) = clipboardController.onCopyClick(listOf(file))
    fun onMoveClick(file: FileItem) = clipboardController.onMoveClick(listOf(file))
    fun onPasteClick() = clipboardController.onPasteClick()
    fun onCancelClipboard() = clipboardController.onCancelClipboard()

    fun onItemLongClicked(item: FileItem) {
        if (!uiState.value.isEditMode) {
            toggleEditMode()
        }
        toggleSelection(item)
    }

    // --- Edit Mode Actions ---
    fun toggleEditMode() {
        _uiState.update {
            it.copy(
                isEditMode = !it.isEditMode,
                selectedPaths = persistentSetOf()
            )
        }
    }

    fun onSelectAll() {
        _uiState.update { state ->
            val allPaths = state.fileItems.map { it.path }.toPersistentSet()
            val isAllSelected = state.selectedPaths.containsAll(allPaths)
            state.copy(selectedPaths = if (isAllSelected) persistentSetOf() else allPaths)
        }
    }

    private fun toggleSelection(item: FileItem) {
        _uiState.update { state ->
            val currentSelection = state.selectedPaths
            val newSelection = if (currentSelection.contains(item.path)) {
                currentSelection.remove(item.path)
            } else {
                currentSelection.add(item.path)
            }
            state.copy(selectedPaths = newSelection)
        }
    }

    fun onCopySelected() {
        val selectedItems = getSelectedItems()
        clipboardController.onCopyClick(selectedItems)
    }

    fun onMoveSelected() {
        val selectedItems = getSelectedItems()
        clipboardController.onMoveClick(selectedItems)
    }

    fun onDeleteSelected() {
        val selectedItems = getSelectedItems()
        dialogController.onShowDeleteMultipleDialog(selectedItems)
    }

    fun onAddToPlaylistSelected() {
        val selectedAudioFiles = getSelectedItems().filterIsInstance<FileItem.AudioFile>()
        if (selectedAudioFiles.isNotEmpty()) {
            dialogController.onShowAddToPlaylistMultipleDialog(selectedAudioFiles)
        }
    }

    fun moveSelectedUp() {
        val selectedItems = getSelectedItems()
        if (selectedItems.size == 1) {
            reorderController.onMoveUp(selectedItems.first())
        }
    }

    fun moveSelectedDown() {
        val selectedItems = getSelectedItems()
        if (selectedItems.size == 1) {
            reorderController.onMoveDown(selectedItems.first())
        }
    }

    private fun getSelectedItems(): List<FileItem> {
        val selectedPaths = _uiState.value.selectedPaths
        return _uiState.value.fileItems.filter { it.path in selectedPaths }
    }
}