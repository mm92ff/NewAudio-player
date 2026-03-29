package com.example.newaudio.feature.filebrowser

import com.example.newaudio.R
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.usecase.file.CopyMultipleFilesUseCase // NEW
import com.example.newaudio.domain.usecase.file.MoveMultipleFilesUseCase // NEW
import com.example.newaudio.util.UiText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class FileBrowserClipboardController(
    private val uiState: MutableStateFlow<FileBrowserUiState>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val copyMultipleFilesUseCase: CopyMultipleFilesUseCase, // NEW
    private val moveMultipleFilesUseCase: MoveMultipleFilesUseCase  // NEW
) {

    fun onCopyClick(files: List<FileItem>) {
        val currentPath = uiState.value.currentPath
        uiState.update {
            it.copy(
                clipboardState = ClipboardState.Active(files, ClipboardAction.COPY, currentPath),
                isEditMode = false,
                selectedPaths = kotlinx.collections.immutable.persistentSetOf()
            )
        }
    }

    fun onMoveClick(files: List<FileItem>) {
        val currentPath = uiState.value.currentPath
        uiState.update {
            it.copy(
                clipboardState = ClipboardState.Active(files, ClipboardAction.MOVE, currentPath),
                isEditMode = false,
                selectedPaths = kotlinx.collections.immutable.persistentSetOf()
            )
        }
    }

    fun onPasteClick() {
        val clipboard = uiState.value.clipboardState
        if (clipboard !is ClipboardState.Active) return

        val targetPath = uiState.value.currentPath

        scope.launch(ioDispatcher) {
            uiState.update { it.copy(isLoading = true) }

            // ✅ NEW: Batch operations
            val success = when (clipboard.action) {
                ClipboardAction.COPY -> copyMultipleFilesUseCase(clipboard.files, targetPath)
                ClipboardAction.MOVE -> moveMultipleFilesUseCase(clipboard.files, clipboard.sourceParentPath, targetPath)
            }

            if (success) {
                uiState.update { it.copy(clipboardState = ClipboardState.Empty, isLoading = false) }
            } else {
                uiState.update { it.copy(isLoading = false, errorRes = UiText.StringResource(R.string.error_loading)) }
            }
        }
    }

    fun onCancelClipboard() {
        uiState.update { it.copy(clipboardState = ClipboardState.Empty) }
    }
}