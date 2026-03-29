package com.example.newaudio.feature.filebrowser

import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.usecase.file.SaveFolderOrderUseCase
import com.example.newaudio.util.Constants
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Collections

internal class FileBrowserReorderController(
    private val uiState: MutableStateFlow<FileBrowserUiState>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val saveFolderOrderUseCase: SaveFolderOrderUseCase
) {

    private var saveOrderJob: Job? = null

    fun onMoveUp(item: FileItem) {
        val oneHanded = uiState.value.oneHandedMode
        if (oneHanded) performMoveDown(item) else performMoveUp(item)
    }

    fun onMoveDown(item: FileItem) {
        val oneHanded = uiState.value.oneHandedMode
        if (oneHanded) performMoveUp(item) else performMoveDown(item)
    }

    private fun performMoveUp(item: FileItem) {
        val currentList = uiState.value.fileItems
        val index = currentList.indexOf(item)
        if (index > 0) {
            val newList = currentList.toMutableList()
            Collections.swap(newList, index, index - 1)
            val newImmutableList = newList.toImmutableList()
            uiState.update { it.copy(fileItems = newImmutableList) }
            saveCurrentOrder(newImmutableList)
        }
    }

    private fun performMoveDown(item: FileItem) {
        val currentList = uiState.value.fileItems
        val index = currentList.indexOf(item)
        if (index < currentList.size - 1) {
            val newList = currentList.toMutableList()
            Collections.swap(newList, index, index + 1)
            val newImmutableList = newList.toImmutableList()
            uiState.update { it.copy(fileItems = newImmutableList) }
            saveCurrentOrder(newImmutableList)
        }
    }

    private fun saveCurrentOrder(items: List<FileItem>) {
        val currentPath = uiState.value.currentPath
        val fileNames = items.map { it.name }

        saveOrderJob?.cancel()
        saveOrderJob = scope.launch(ioDispatcher) {
            delay(Constants.REORDER_DEBOUNCE_MS)
            try {
                saveFolderOrderUseCase(currentPath, fileNames)
            } catch (e: Exception) {
                Timber.w(e, "Failed to save folder order for $currentPath")
            }
        }
    }
}