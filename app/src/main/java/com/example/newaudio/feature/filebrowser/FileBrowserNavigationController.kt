package com.example.newaudio.feature.filebrowser

import com.example.newaudio.R
import com.example.newaudio.domain.usecase.file.GetParentPathUseCase
import com.example.newaudio.domain.usecase.file.GetRootPathUseCase
import com.example.newaudio.util.UiText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal class FileBrowserNavigationController(
    private val uiState: MutableStateFlow<FileBrowserUiState>,
    private val pathFlow: MutableStateFlow<String>,
    private val scope: CoroutineScope,
    private val getRootPathUseCase: GetRootPathUseCase,
    private val getParentPathUseCase: GetParentPathUseCase,
    private val logTag: String
) {

    fun checkPermissionsAndLoadRoot() {
        scope.launch {
            val root = getRootPathUseCase()
            Timber.tag(logTag).d("Manual Reload Root: %s", root)

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
                uiState.update { it.copy(errorRes = UiText.StringResource(R.string.select_other_folder)) }
            }
        }
    }

    fun loadPath(path: String, addToHistory: Boolean = true) {
        val normalizedPath = path.removeSuffix("/")

        if (normalizedPath.isEmpty()) {
            uiState.update { it.copy(isLoading = false, errorRes = UiText.StringResource(R.string.error_loading)) }
            return
        }

        val current = uiState.value
        if (normalizedPath == current.currentPath) {
            Timber.tag(logTag).d("Ignoring loadPath for same path: %s", normalizedPath)
            return
        }

        Timber.tag(logTag).d("Loading path: %s (History: %b)", normalizedPath, addToHistory)

        uiState.update { currentState ->
            val newHistory = if (addToHistory && currentState.currentPath.isNotEmpty()) {
                currentState.pathHistory.toMutableList().apply {
                    add(currentState.currentPath)
                }.toImmutableList()
            } else {
                currentState.pathHistory
            }

            currentState.copy(
                isLoading = true,
                currentPath = normalizedPath,
                pathHistory = newHistory,
                canNavigateBack = newHistory.isNotEmpty(),
                errorRes = null
            )
        }

        // Trigger the reactive stream.
        pathFlow.value = normalizedPath
    }

    fun navigateUp() {
        val history = uiState.value.pathHistory
        if (history.isNotEmpty()) {
            val previousPath = history.last()
            val newHistory = history.toMutableList().apply {
                removeAt(history.lastIndex)
            }.toImmutableList()

            uiState.update {
                it.copy(
                    pathHistory = newHistory,
                    canNavigateBack = newHistory.isNotEmpty()
                )
            }

            loadPath(previousPath, false)
        } else {
            val current = uiState.value.currentPath
            val root = uiState.value.rootPath
            if (current == root || current.isEmpty()) return

            val parent = getParentPathUseCase(current)
            if (parent != null) {
                loadPath(parent, false)
            }
        }
    }
}
