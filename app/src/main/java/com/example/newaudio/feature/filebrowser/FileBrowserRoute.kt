package com.example.newaudio.feature.filebrowser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.newaudio.feature.player.PlayerViewModel
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun FileBrowserRoute(
    fileBrowserViewModel: FileBrowserViewModel,
    playerViewModel: PlayerViewModel,
    onSettingsClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    onToggleVideoFullscreen: () -> Unit,
    isVideoFullscreen: Boolean,
    onInlinePlayerViewChanged: (PlayerView?) -> Unit
) {
    val uiState by fileBrowserViewModel.uiState.collectAsStateWithLifecycle()
    val playerFlow = remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.player }
            .distinctUntilChanged()
    }
    val player by playerFlow.collectAsStateWithLifecycle(initialValue = null)

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
        player = player,
        onSettingsClick = onSettingsClick,
        onPlaylistClick = onPlaylistClick,
        onToggleBrowserMode = fileBrowserViewModel::onToggleBrowserMode,
        onItemClick = fileBrowserViewModel::onItemClicked,
        onFolderIconClick = fileBrowserViewModel::onFolderIconClicked,
        onDeleteClick = fileBrowserViewModel::onShowDeleteDialog,
        onRenameClick = fileBrowserViewModel::onShowRenameDialog,
        onCopyClick = fileBrowserViewModel::onCopyClick,
        onMoveClick = fileBrowserViewModel::onMoveClick,
        onAddToPlaylistClick = fileBrowserViewModel::onShowAddToPlaylistDialog,
        onAddToVideoPlaylistClick = fileBrowserViewModel::onShowAddToVideoPlaylistDialog,
        onAddToPlaylistConfirmed = fileBrowserViewModel::onAddToPlaylistConfirmed,
        onAddToVideoPlaylistConfirmed = fileBrowserViewModel::onAddToVideoPlaylistConfirmed,
        onPasteClick = fileBrowserViewModel::onPasteClick,
        onCancelClipboard = fileBrowserViewModel::onCancelClipboard,
        onNavigateUp = fileBrowserViewModel::navigateUp,
        onExitInlineVideo = fileBrowserViewModel::onExitInlineVideo,
        onRenameConfirmed = fileBrowserViewModel::onRenameConfirmed,
        onCreateFolderConfirmed = fileBrowserViewModel::onCreateFolderConfirmed,
        onDeleteConfirmed = fileBrowserViewModel::onDeleteConfirmed,
        onDismissDialog = fileBrowserViewModel::onDismissDialog,
        onErrorShown = fileBrowserViewModel::onErrorShown,
        onRefresh = { fileBrowserViewModel.onRefresh(isAutoRefresh = false) },
        onItemLongClick = fileBrowserViewModel::onItemLongClicked,
        onEmptyAreaLongClick = fileBrowserViewModel::onShowCreateFolderDialog,
        onMoveSelectedUp = fileBrowserViewModel::moveSelectedUp,
        onMoveSelectedDown = fileBrowserViewModel::moveSelectedDown,
        onToggleRepeatMode = playerViewModel::onToggleRepeatOne,
        onInlineVideoSwipeNext = playerViewModel::onSkipNext,
        onInlineVideoSwipePrevious = playerViewModel::onSkipPrevious,
        onToggleVideoFullscreen = onToggleVideoFullscreen,
        isVideoFullscreen = isVideoFullscreen,
        onInlinePlayerViewChanged = onInlinePlayerViewChanged,
        // Multi-Select
        onToggleEditMode = fileBrowserViewModel::toggleEditMode,
        onSelectAll = fileBrowserViewModel::onSelectAll, // ✅ NEW: Added
        onCopySelected = fileBrowserViewModel::onCopySelected,
        onMoveSelected = fileBrowserViewModel::onMoveSelected,
        onDeleteSelected = fileBrowserViewModel::onDeleteSelected,
        onAddToPlaylistSelected = fileBrowserViewModel::onAddToPlaylistSelected,
        onDeleteMultipleConfirmed = fileBrowserViewModel::onDeleteMultipleConfirmed,
        onAddToPlaylistMultipleConfirmed = fileBrowserViewModel::onAddToPlaylistMultipleConfirmed,
        onAddToVideoPlaylistMultipleConfirmed = fileBrowserViewModel::onAddToVideoPlaylistMultipleConfirmed,
        onCreatePlaylistAndAdd = fileBrowserViewModel::onCreatePlaylistAndAdd,
        onCreateVideoPlaylistAndAdd = fileBrowserViewModel::onCreateVideoPlaylistAndAdd
    )
}
