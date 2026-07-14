package com.example.newaudio.feature.filebrowser

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import com.example.newaudio.BuildConfig
import com.example.newaudio.feature.player.PlayerViewModel
import com.example.newaudio.ui.NewAudioTestTags
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

    var browserRootPositioned by remember(uiState.currentPath) { mutableStateOf(false) }
    var firstInteractiveContentPositioned by remember(uiState.currentPath) { mutableStateOf(false) }
    LaunchedEffect(uiState.currentPath, uiState.isLoading, uiState.fileItems.size) {
        if (uiState.isLoading || uiState.fileItems.isEmpty()) {
            firstInteractiveContentPositioned = false
        }
    }

    val isBrowserReady = isBrowserContentReady(
        isLoading = uiState.isLoading,
        itemCount = uiState.fileItems.size,
        requireFixtureContent = BuildConfig.BENCHMARK,
        browserRootPositioned = browserRootPositioned,
        firstInteractiveContentPositioned = firstInteractiveContentPositioned
    )

    ReportDrawnWhen { isBrowserReady }

    LaunchedEffect(Unit) {
        fileBrowserViewModel.events.collect { event ->
            when (event) {
                is FileBrowserEvent.PlayPlaylist -> {
                    playerViewModel.onPlayPlaylist(event.songs, event.startIndex)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            onCreateVideoPlaylistAndAdd = fileBrowserViewModel::onCreateVideoPlaylistAndAdd,
            onFirstInteractiveContentPositioned = { firstInteractiveContentPositioned = true },
            modifier = Modifier.onGloballyPositioned { coordinates ->
                browserRootPositioned = coordinates.size.width > 0 && coordinates.size.height > 0
            }
        )
        if (isBrowserReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(NewAudioTestTags.BROWSER_READY)
            )
        }
    }
}

internal fun isBrowserContentReady(
    isLoading: Boolean,
    itemCount: Int,
    requireFixtureContent: Boolean,
    browserRootPositioned: Boolean,
    firstInteractiveContentPositioned: Boolean
): Boolean = !isLoading &&
    browserRootPositioned &&
    (!requireFixtureContent || (itemCount > 0 && firstInteractiveContentPositioned))
