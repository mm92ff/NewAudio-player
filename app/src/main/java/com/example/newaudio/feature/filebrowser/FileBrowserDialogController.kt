package com.example.newaudio.feature.filebrowser

import com.example.newaudio.R
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.repository.IPlaylistRepository
import com.example.newaudio.domain.usecase.file.DeleteFileUseCase
import com.example.newaudio.domain.usecase.file.DeleteMultipleFilesUseCase // NEW: Import
import com.example.newaudio.domain.usecase.file.RenameFileUseCase
import com.example.newaudio.util.UiText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class FileBrowserDialogController(
    private val uiState: MutableStateFlow<FileBrowserUiState>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val deleteFileUseCase: DeleteFileUseCase,
    private val deleteMultipleFilesUseCase: DeleteMultipleFilesUseCase, // NEW: Injected
    private val renameFileUseCase: RenameFileUseCase,
    private val playlistRepository: IPlaylistRepository
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

    fun onShowAddToPlaylistDialog(file: FileItem.AudioFile) {
        uiState.update { it.copy(dialogState = DialogState.AddToPlaylist(file)) }
    }

    fun onShowAddToPlaylistMultipleDialog(files: List<FileItem.AudioFile>) {
        uiState.update { it.copy(dialogState = DialogState.AddToPlaylistMultiple(files)) }
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
            if (!deleteFileUseCase(parentPath, file)) {
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

            if (!success) {
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
}