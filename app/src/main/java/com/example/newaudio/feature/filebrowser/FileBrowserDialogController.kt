package com.example.newaudio.feature.filebrowser

import com.example.newaudio.R
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.VideoPlaylist
import com.example.newaudio.domain.repository.IPlaylistRepository
import com.example.newaudio.domain.repository.IVideoPlaylistRepository
import com.example.newaudio.domain.usecase.file.CreateFolderResult
import com.example.newaudio.domain.usecase.file.CreateFolderUseCase
import com.example.newaudio.domain.usecase.file.DeleteFileUseCase
import com.example.newaudio.domain.usecase.file.DeleteMultipleFilesUseCase // NEW: Import
import com.example.newaudio.domain.usecase.file.RenameFileUseCase
import com.example.newaudio.util.UiText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet

internal class FileBrowserDialogController(
    private val uiState: MutableStateFlow<FileBrowserUiState>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val deleteFileUseCase: DeleteFileUseCase,
    private val deleteMultipleFilesUseCase: DeleteMultipleFilesUseCase, // NEW: Injected
    private val renameFileUseCase: RenameFileUseCase,
    private val createFolderUseCase: CreateFolderUseCase,
    private val playlistRepository: IPlaylistRepository,
    private val videoPlaylistRepository: IVideoPlaylistRepository,
    private val onFilesDeleted: suspend (List<String>) -> Unit,
    private val onFolderCreated: suspend () -> Unit
) {

    fun onShowDeleteDialog(file: FileItem) {
        uiState.update { it.copy(dialogState = DialogState.Delete(file)) }
    }

    fun onShowDeleteMultipleDialog(files: List<FileItem>) {
        uiState.update { it.copy(dialogState = DialogState.DeleteMultiple(files)) }
    }

    fun onShowRenameDialog(file: FileItem) {
        uiState.update { it.copy(dialogState = DialogState.Rename(file)) }
    }

    fun onShowCreateFolderDialog() {
        if (!uiState.value.isEditMode) {
            uiState.update { it.copy(dialogState = DialogState.CreateFolder) }
        }
    }

    fun onShowAddToPlaylistDialog(file: FileItem.AudioFile) {
        uiState.update { it.copy(dialogState = DialogState.AddToPlaylist(file)) }
    }

    fun onShowAddToVideoPlaylistDialog(file: FileItem.VideoFile) {
        uiState.update { it.copy(dialogState = DialogState.AddToVideoPlaylist(file)) }
    }

    fun onShowAddToPlaylistMultipleDialog(files: List<FileItem.AudioFile>) {
        uiState.update { it.copy(dialogState = DialogState.AddToPlaylistMultiple(files)) }
    }

    fun onShowAddToVideoPlaylistMultipleDialog(files: List<FileItem.VideoFile>) {
        uiState.update { it.copy(dialogState = DialogState.AddToVideoPlaylistMultiple(files)) }
    }

    fun onDismissDialog() {
        uiState.update { it.copy(dialogState = DialogState.None) }
    }

    fun onErrorShown() {
        uiState.update { it.copy(errorRes = null) }
    }

    fun onDeleteConfirmed(file: FileItem) {
        scope.launch(ioDispatcher) {
            val parentPath = uiState.value.currentPath
            val success = deleteFileUseCase(parentPath, file)
            if (success) {
                val deletedPaths = listOf(file.path)
                removeDeletedItemsFromUi(deletedPaths)
                onFilesDeleted(deletedPaths)
            } else {
                uiState.update { it.copy(errorRes = UiText.StringResource(R.string.error_loading)) }
            }
            onDismissDialog()
        }
    }

    // ✅ OPTIMIZED: Now uses the batch use case
    fun onDeleteMultipleConfirmed(files: List<FileItem>) {
        scope.launch(ioDispatcher) {
            val parentPath = uiState.value.currentPath

            // One call instead of a loop -> 1 DB transaction
            val success = deleteMultipleFilesUseCase(parentPath, files)

            if (success) {
                val deletedPaths = files.map { it.path }
                removeDeletedItemsFromUi(deletedPaths)
                onFilesDeleted(deletedPaths)
            } else {
                uiState.update { it.copy(errorRes = UiText.StringResource(R.string.error_loading)) }
            }
            onDismissDialog()
        }
    }

    fun onRenameConfirmed(file: FileItem, newName: String) {
        scope.launch(ioDispatcher) {
            if (!renameFileUseCase(file, newName)) {
                uiState.update { it.copy(errorRes = UiText.StringResource(R.string.error_loading)) }
            }
            onDismissDialog()
        }
    }

    fun onCreateFolderConfirmed(folderName: String) {
        scope.launch(ioDispatcher) {
            val currentPath = uiState.value.currentPath
            val result = createFolderUseCase(currentPath, folderName)
            when (result) {
                CreateFolderResult.SUCCESS -> {
                    onDismissDialog()
                    onFolderCreated()
                    uiState.update { it.copy(errorRes = UiText.StringResource(R.string.folder_created)) }
                }
                CreateFolderResult.INVALID_NAME -> {
                    uiState.update { it.copy(errorRes = UiText.StringResource(R.string.folder_name_invalid)) }
                }
                CreateFolderResult.ALREADY_EXISTS -> {
                    uiState.update { it.copy(errorRes = UiText.StringResource(R.string.folder_already_exists)) }
                }
                CreateFolderResult.FAILED -> {
                    uiState.update { it.copy(errorRes = UiText.StringResource(R.string.folder_create_failed)) }
                }
            }
        }
    }

    fun onAddToPlaylistConfirmed(playlist: Playlist, file: FileItem.AudioFile) {
        scope.launch(ioDispatcher) {
            playlistRepository.addSongToPlaylist(playlist.id, file.song)
            onDismissDialog()
        }
    }

    // ✅ OPTIMIZED: Now uses the batch repo method (addSongsToPlaylist)
    fun onAddToPlaylistMultipleConfirmed(playlist: Playlist, files: List<FileItem.AudioFile>) {
        scope.launch(ioDispatcher) {
            val songs = files.map { it.song }
            // One DB insert instead of N inserts
            playlistRepository.addSongsToPlaylist(playlist.id, songs)
            onDismissDialog()
        }
    }

    fun onAddToVideoPlaylistConfirmed(playlist: VideoPlaylist, file: FileItem.VideoFile) {
        scope.launch(ioDispatcher) {
            videoPlaylistRepository.addVideoToPlaylist(playlist.id, file.video)
            onDismissDialog()
        }
    }

    fun onAddToVideoPlaylistMultipleConfirmed(playlist: VideoPlaylist, files: List<FileItem.VideoFile>) {
        scope.launch(ioDispatcher) {
            val videos = files.map { it.video }
            videoPlaylistRepository.addVideosToPlaylist(playlist.id, videos)
            onDismissDialog()
        }
    }

    fun onCreatePlaylistAndAdd(name: String) {
        scope.launch(ioDispatcher) {
            val dialogState = uiState.value.dialogState
            val playlistId = playlistRepository.createPlaylist(name.trim())
            when (dialogState) {
                is DialogState.AddToPlaylist ->
                    playlistRepository.addSongToPlaylist(playlistId, dialogState.file.song)
                is DialogState.AddToPlaylistMultiple ->
                    playlistRepository.addSongsToPlaylist(playlistId, dialogState.files.map { it.song })
                else -> {}
            }
            onDismissDialog()
        }
    }

    fun onCreateVideoPlaylistAndAdd(name: String) {
        scope.launch(ioDispatcher) {
            val dialogState = uiState.value.dialogState
            val playlistId = videoPlaylistRepository.createVideoPlaylist(name.trim())
            when (dialogState) {
                is DialogState.AddToVideoPlaylist ->
                    videoPlaylistRepository.addVideoToPlaylist(playlistId, dialogState.file.video)
                is DialogState.AddToVideoPlaylistMultiple ->
                    videoPlaylistRepository.addVideosToPlaylist(playlistId, dialogState.files.map { it.video })
                else -> {}
            }
            onDismissDialog()
        }
    }

    private fun removeDeletedItemsFromUi(deletedPaths: List<String>) {
        val normalized = deletedPaths.map { it.removeSuffix("/") }
        uiState.update { state ->
            state.copy(
                fileItems = state.fileItems
                    .filterNot { item ->
                        normalized.any { deleted -> item.path.isDeletedBy(deleted) }
                    }
                    .toImmutableList(),
                selectedPaths = state.selectedPaths
                    .filterNot { path -> normalized.any { deleted -> path.isDeletedBy(deleted) } }
                    .toPersistentSet(),
                showInlineVideo = if (
                    state.activeVideoPath != null &&
                    normalized.any { deleted -> state.activeVideoPath.isDeletedBy(deleted) }
                ) {
                    false
                } else {
                    state.showInlineVideo
                }
            )
        }
    }

    private fun String?.isDeletedBy(deletedPath: String): Boolean {
        val path = this?.removeSuffix("/") ?: return false
        val deleted = deletedPath.removeSuffix("/")
        return path == deleted || path.startsWith("$deleted/")
    }
}
