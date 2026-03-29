package com.example.newaudio.feature.filebrowser.composables

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.feature.filebrowser.FileBrowserUiState
import com.example.newaudio.ui.theme.Dimens

@Composable
fun FileBrowserList(
    uiState: FileBrowserUiState,
    listState: LazyListState,
    activeSongPath: String?,
    onItemClick: (FileItem) -> Unit,
    onFolderIconClick: (FileItem) -> Unit,
    onDeleteClick: (FileItem) -> Unit,
    onRenameClick: (FileItem) -> Unit,
    onCopyClick: (FileItem) -> Unit,
    onMoveClick: (FileItem) -> Unit,
    onAddToPlaylistClick: (FileItem.AudioFile) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onToggleRepeatMode: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val topSpacerHeight = maxHeight - Dimens.FileBrowser_ItemHeight

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            item(key = "reachability_spacer") {
                if (topSpacerHeight > 0.dp) {
                    Spacer(modifier = Modifier.height(topSpacerHeight))
                }
            }

            items(
                items = uiState.fileItems,
                key = { it.path }
            ) { item ->
                val isActive = remember(item, activeSongPath) {
                    if (activeSongPath == null) {
                        false
                    } else {
                        when (item) {
                            is FileItem.Folder -> activeSongPath.startsWith(item.path)
                            is FileItem.AudioFile -> item.song.path == activeSongPath
                            else -> false
                        }
                    }
                }

                val isSelected = remember(uiState.selectedPaths, item.path) {
                    uiState.selectedPaths.contains(item.path)
                }

                val isRepeatingSong = remember(isActive, uiState.repeatMode) {
                    isActive && uiState.repeatMode == UserPreferences.RepeatMode.ONE
                }

                FileBrowserItem(
                    item = item,
                    isActive = isActive,
                    isEditMode = uiState.isEditMode,
                    isSelected = isSelected,
                    isRepeatingSong = isRepeatingSong,
                    transparentListItems = uiState.transparentListItems,
                    onClick = { onItemClick(item) },
                    onFolderIconClick = { onFolderIconClick(item) },
                    onLongClick = { onItemLongClick(item) },
                    onDelete = { onDeleteClick(item) },
                    onRename = { onRenameClick(item) },
                    onCopy = { onCopyClick(item) },
                    onMove = { onMoveClick(item) },
                    onAddToPlaylist = { if (item is FileItem.AudioFile) onAddToPlaylistClick(item) },
                    onToggleRepeatMode = onToggleRepeatMode
                )
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
            }
        }
    }
}
