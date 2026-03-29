package com.example.newaudio.feature.filebrowser

import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.usecase.file.GetSortedFileTreeUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

internal class FileBrowserPlaybackController(
    private val uiState: StateFlow<FileBrowserUiState>,
    private val scope: CoroutineScope,
    private val mediaRepository: IMediaRepository,
    private val getSortedFileTreeUseCase: GetSortedFileTreeUseCase,
    private val loadPath: (String, Boolean) -> Unit,
    private val logTag: String
) {

    fun onToggleRepeatOne() {
        scope.launch {
            val currentMode = uiState.value.repeatMode
            val newMode = if (currentMode == UserPreferences.RepeatMode.ONE) {
                UserPreferences.RepeatMode.NONE
            } else {
                UserPreferences.RepeatMode.ONE
            }
            mediaRepository.setRepeatMode(newMode)
        }
    }

    /**
     * Plays the contents of the folder WITHOUT navigating into it.
     */
    fun playFolderWithoutNavigation(folder: FileItem.Folder) {
        scope.launch {
            try {
                // 1. Query the flow and wait for the first non-empty result
                // We use the predicate in first { ... } explicitly here
                val files = getSortedFileTreeUseCase(folder.path).first { list ->
                    list.isNotEmpty()
                }

                // 2. Now we have a regular List<FileItem>, no flow issues anymore
                val playlist = files
                    .filterIsInstance<FileItem.AudioFile>()
                    .map { it.song }

                if (playlist.isNotEmpty()) {
                    mediaRepository.playPlaylist(playlist, 0, folder.path)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.tag(logTag).e(e, "Error starting playback for folder: ${folder.path}")
            }
        }
    }

    /**
     * Legacy logic: navigates first, then starts playback.
     */
    fun playFolderAndNavigate(folder: FileItem.Folder) {
        scope.launch {
            loadPath(folder.path, true)

            try {
                // Here we wait on the StateFlow
                val playlist = uiState
                    .filter { state ->
                        state.currentPath == folder.path && !state.isLoading
                    }
                    .map { state ->
                        state.fileItems
                            .filterIsInstance<FileItem.AudioFile>()
                            .map { it.song }
                    }
                    .first() // Gets the first completed list from the flow

                if (playlist.isNotEmpty()) {
                    mediaRepository.playPlaylist(playlist, 0, folder.path)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.tag(logTag).e(e, "Error starting playback for folder: ${folder.path}")
            }
        }
    }

    fun playAudioFile(file: FileItem.AudioFile) {
        scope.launch {
            // uiState.value is NOT a Flow, but the object directly.
            val currentItems = uiState.value.fileItems

            val playlist = currentItems
                .filterIsInstance<FileItem.AudioFile>()
                .map { it.song }

            val folderPath = java.io.File(file.song.path).parent
            if (playlist.isNotEmpty()) {
                val startIndex = playlist.indexOfFirst { it.path == file.song.path }.coerceAtLeast(0)
                mediaRepository.playPlaylist(playlist, startIndex, folderPath)
            } else {
                mediaRepository.playPlaylist(listOf(file.song), 0, folderPath)
            }
        }
    }
}