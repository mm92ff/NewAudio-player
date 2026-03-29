package com.example.newaudio.feature.filebrowser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.newaudio.feature.player.PlayerViewModel

@Composable
fun FileBrowserRoute(
    fileBrowserViewModel: FileBrowserViewModel,
    playerViewModel: PlayerViewModel,
    onSettingsClick: () -> Unit,
    onPlaylistClick: () -> Unit,
) {
    val uiState by fileBrowserViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        fileBrowserViewModel.events.collect { event ->
            when (event) {
                is FileBrowserEvent.PlayPlaylist -> {
                    playerViewModel.onPlayPlaylist(event.songs, event.startIndex)
                }
            }
        }
    }

    FileBrowserScreen(
        uiState = uiState,
        onSettingsClick = onSettingsClick,
        onPlaylistClick = onPlaylistClick,
        onItemClick = fileBrowserViewModel::onItemClicked,
        onFolderIconClick = fileBrowserViewModel::onFolderIconClicked,
        onDeleteClick = fileBrowserViewModel::onShowDeleteDialog,
        onRenameClick = fileBrowserViewModel::onShowRenameDialog,
        onCopyClick = fileBrowserViewModel::onCopyClick,
        onMoveClick = fileBrowserViewModel::onMoveClick,
        onAddToPlaylistClick = fileBrowserViewModel::onShowAddToPlaylistDialog,
        onAddToPlaylistConfirmed = fileBrowserViewModel::onAddToPlaylistConfirmed,
        onPasteClick = fileBrowserViewModel::onPasteClick,
        onCancelClipboard = fileBrowserViewModel::onCancelClipboard,
        onNavigateUp = fileBrowserViewModel::navigateUp,
        onRenameConfirmed = fileBrowserViewModel::onRenameConfirmed,
        onDeleteConfirmed = fileBrowserViewModel::onDeleteConfirmed,
        onDismissDialog = fileBrowserViewModel::onDismissDialog,
        onErrorShown = fileBrowserViewModel::onErrorShown,
        onRefresh = { fileBrowserViewModel.onRefresh(isAutoRefresh = false) },
        onItemLongClick = fileBrowserViewModel::onItemLongClicked,
        onMoveSelectedUp = fileBrowserViewModel::moveSelectedUp,
        onMoveSelectedDown = fileBrowserViewModel::moveSelectedDown,
        onToggleRepeatMode = playerViewModel::onToggleRepeatOne,
        // Multi-Select
        onToggleEditMode = fileBrowserViewModel::toggleEditMode,
        onSelectAll = fileBrowserViewModel::onSelectAll, // ✅ NEW: Added
        onCopySelected = fileBrowserViewModel::onCopySelected,
        onMoveSelected = fileBrowserViewModel::onMoveSelected,
        onDeleteSelected = fileBrowserViewModel::onDeleteSelected,
        onAddToPlaylistSelected = fileBrowserViewModel::onAddToPlaylistSelected,
        onDeleteMultipleConfirmed = fileBrowserViewModel::onDeleteMultipleConfirmed,
        onAddToPlaylistMultipleConfirmed = fileBrowserViewModel::onAddToPlaylistMultipleConfirmed,
        onCreatePlaylistAndAdd = fileBrowserViewModel::onCreatePlaylistAndAdd
    )
}