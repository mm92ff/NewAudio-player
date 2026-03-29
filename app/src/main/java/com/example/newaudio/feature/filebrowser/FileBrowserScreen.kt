package com.example.newaudio.feature.filebrowser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.newaudio.R
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.feature.filebrowser.composables.*
import com.example.newaudio.ui.theme.Dimens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    uiState: FileBrowserUiState,
    onSettingsClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    onItemClick: (FileItem) -> Unit,
    onFolderIconClick: (FileItem) -> Unit,
    onDeleteClick: (FileItem) -> Unit,
    onRenameClick: (FileItem) -> Unit,
    onCopyClick: (FileItem) -> Unit,
    onMoveClick: (FileItem) -> Unit,
    onAddToPlaylistClick: (FileItem.AudioFile) -> Unit,
    onAddToPlaylistConfirmed: (Playlist, FileItem.AudioFile) -> Unit,
    onPasteClick: () -> Unit,
    onCancelClipboard: () -> Unit,
    onNavigateUp: () -> Unit,
    onRenameConfirmed: (FileItem, String) -> Unit,
    onDeleteConfirmed: (FileItem) -> Unit,
    onDismissDialog: () -> Unit,
    onErrorShown: () -> Unit,
    onRefresh: () -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onToggleRepeatMode: () -> Unit,
    // Multi-Select
    onToggleEditMode: () -> Unit,
    onSelectAll: () -> Unit,
    onCopySelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onAddToPlaylistSelected: () -> Unit,
    onMoveSelectedUp: () -> Unit,
    onMoveSelectedDown: () -> Unit,
    onDeleteMultipleConfirmed: (List<FileItem>) -> Unit,
    onAddToPlaylistMultipleConfirmed: (Playlist, List<FileItem.AudioFile>) -> Unit,
    onCreatePlaylistAndAdd: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var hasScrolledToContent by remember(uiState.currentPath) { mutableStateOf(false) }

    LaunchedEffect(uiState.fileItems.size, uiState.isLoading, uiState.currentPath) {
        if (!hasScrolledToContent && !uiState.isLoading && uiState.fileItems.isNotEmpty()) {
            listState.scrollToItem(1)
            hasScrolledToContent = true
        }
    }

    // Auto-scroll logic for reordering
    val selectedPath = remember(uiState.selectedPaths) { uiState.selectedPaths.firstOrNull() }
    LaunchedEffect(uiState.fileItems, selectedPath, uiState.isEditMode) {
        if (uiState.isEditMode && selectedPath != null && uiState.selectedPaths.size == 1) {
            val index = uiState.fileItems.indexOfFirst { it.path == selectedPath }
            if (index != -1) {
                val listIndex = index + 1 // +1 because of reachability_spacer
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val itemInfo = visibleItems.find { it.index == listIndex }

                if (itemInfo == null) {
                    listState.animateScrollToItem(listIndex)
                } else {
                    val viewportHeight = listState.layoutInfo.viewportSize.height
                    val itemTop = itemInfo.offset
                    val itemBottom = itemInfo.offset + itemInfo.size
                    val threshold = Dimens.FileBrowser_ItemHeight.value.toInt() * 2

                    if (itemTop < threshold || itemBottom > (viewportHeight - threshold)) {
                        listState.animateScrollToItem(
                            index = listIndex,
                            scrollOffset = -(viewportHeight / 3)
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(uiState.activeSongPath) {
        uiState.activeSongPath?.let { path ->
            val dataIndex = uiState.fileItems.indexOfFirst {
                it is FileItem.AudioFile && it.song.path == path
            }
            if (dataIndex != -1) {
                val listIndex = dataIndex + 1
                val visibleItemsInfo = listState.layoutInfo.visibleItemsInfo
                val itemInfo = visibleItemsInfo.find { it.index == listIndex }

                val isFullyVisible = itemInfo?.let {
                    val viewportHeight = listState.layoutInfo.viewportSize.height
                    it.offset >= 0 && (it.offset + it.size) <= viewportHeight
                } ?: false

                if (!isFullyVisible) {
                    listState.animateScrollToItem(listIndex, 0)
                }
            }
        }
    }

    LaunchedEffect(uiState.errorRes) {
        uiState.errorRes?.let {
            snackbarHostState.showSnackbar(it.asString(context))
            onErrorShown()
        }
    }

    BackHandler(enabled = uiState.isEditMode) { onToggleEditMode() }
    BackHandler(enabled = uiState.canNavigateBack && !uiState.isEditMode) { onNavigateUp() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!uiState.oneHandedMode) {
                FileBrowserAppBar(
                    currentPath = uiState.currentPath,
                    canNavigateBack = uiState.canNavigateBack,
                    onNavigateUp = onNavigateUp,
                    onSettingsClick = onSettingsClick,
                    onPlaylistClick = onPlaylistClick,
                    isEditMode = uiState.isEditMode,
                    selectedCount = uiState.selectedPaths.size,
                    allSelected = uiState.fileItems.isNotEmpty() && uiState.selectedPaths.size == uiState.fileItems.size,
                    onToggleEditMode = onToggleEditMode,
                    onSelectAll = onSelectAll,
                    isReversedLayout = false
                )
            }
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = uiState.isEditMode && uiState.selectedPaths.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(Dimens.PaddingSmall)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val singleSelection = uiState.selectedPaths.size == 1

                            // Button order for normal and one-handed mode
                            val actionButtons = listOf<@Composable () -> Unit>(
                                {
                                    IconButton(onClick = onAddToPlaylistSelected) {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_playlist))
                                    }
                                },
                                {
                                    IconButton(onClick = onCopySelected) {
                                        Icon(Icons.Default.ContentCopy, stringResource(R.string.copy))
                                    }
                                },
                                {
                                    IconButton(onClick = onMoveSelected) {
                                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.move))
                                    }
                                },
                                {
                                    IconButton(onClick = onMoveSelectedUp, enabled = singleSelection) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            stringResource(R.string.reorder_up),
                                            tint = if (singleSelection) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.38f)
                                        )
                                    }
                                },
                                {
                                    IconButton(onClick = onMoveSelectedDown, enabled = singleSelection) {
                                        Icon(
                                            Icons.Default.ArrowDownward,
                                            stringResource(R.string.reorder_down),
                                            tint = if (singleSelection) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.38f)
                                        )
                                    }
                                },
                                {
                                    IconButton(onClick = onDeleteSelected) {
                                        Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            )

                            // In one-handed mode (bottom) we reverse the order
                            val displayedActions = if (uiState.oneHandedMode) actionButtons.asReversed() else actionButtons

                            displayedActions.forEach { it() }
                        }
                    }
                }
                if (uiState.oneHandedMode) {
                    FileBrowserAppBar(
                        currentPath = uiState.currentPath,
                        canNavigateBack = uiState.canNavigateBack,
                        onNavigateUp = onNavigateUp,
                        onSettingsClick = onSettingsClick,
                        onPlaylistClick = onPlaylistClick,
                        isEditMode = uiState.isEditMode,
                        selectedCount = uiState.selectedPaths.size,
                        allSelected = uiState.fileItems.isNotEmpty() && uiState.selectedPaths.size == uiState.fileItems.size,
                        onToggleEditMode = onToggleEditMode,
                        onSelectAll = onSelectAll,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        isReversedLayout = true
                    )
                }
            }
        },
        floatingActionButton = {
            if (uiState.clipboardState is ClipboardState.Active) {
                val pasteText = if (uiState.clipboardState.action == ClipboardAction.COPY) {
                    stringResource(R.string.paste_copy)
                } else {
                    stringResource(R.string.paste_move)
                }

                // --- PASTE BUTTON (action) ---
                // Now uses the "Primary" (accent) color from the theme
                ExtendedFloatingActionButton(
                    onClick = onPasteClick,
                    icon = { Icon(Icons.Default.ContentPaste, stringResource(R.string.paste)) },
                    text = { Text(pasteText) },
                    containerColor = MaterialTheme.colorScheme.primary, // Accent Color
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.fileItems.isEmpty() && !uiState.isLoading) {
                    Text(
                        text = stringResource(R.string.empty_folder),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    FileBrowserList(
                        uiState = uiState,
                        listState = listState,
                        activeSongPath = uiState.activeSongPath,
                        onItemClick = onItemClick,
                        onFolderIconClick = onFolderIconClick,
                        onDeleteClick = onDeleteClick,
                        onRenameClick = onRenameClick,
                        onCopyClick = onCopyClick,
                        onMoveClick = onMoveClick,
                        onAddToPlaylistClick = onAddToPlaylistClick,
                        onItemLongClick = onItemLongClick,
                        onToggleRepeatMode = onToggleRepeatMode
                    )
                }

                if (uiState.clipboardState is ClipboardState.Active) {
                    // --- CANCEL BUTTON (Abbrechen) ---
                    // Bleibt "Standard" (SecondaryContainer), damit er nicht so wichtig wirkt
                    FloatingActionButton(
                        onClick = onCancelClipboard,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                bottom = Dimens.FileBrowser_FabBottomMarginWithExtra,
                                end = Dimens.PaddingMedium
                            ),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.Close, stringResource(R.string.cancel_clipboard))
                    }
                }
            }
        }
    }

    when (val dialogState = uiState.dialogState) {
        is DialogState.Rename -> {
            RenameDialog(
                item = dialogState.file,
                onConfirm = { newName -> onRenameConfirmed(dialogState.file, newName) },
                onDismiss = onDismissDialog
            )
        }
        is DialogState.Delete -> {
            DeleteConfirmationDialog(
                item = dialogState.file,
                onConfirm = { onDeleteConfirmed(dialogState.file) },
                onDismiss = onDismissDialog
            )
        }
        is DialogState.DeleteMultiple -> {
            DeleteMultipleConfirmationDialog(
                items = dialogState.files,
                onConfirm = { onDeleteMultipleConfirmed(dialogState.files) },
                onDismiss = onDismissDialog
            )
        }
        is DialogState.AddToPlaylist -> {
            AddToPlaylistDialog(
                playlists = uiState.playlists,
                onPlaylistSelected = { playlist -> onAddToPlaylistConfirmed(playlist, dialogState.file) },
                onCreateAndAdd = onCreatePlaylistAndAdd,
                onDismiss = onDismissDialog
            )
        }
        is DialogState.AddToPlaylistMultiple -> {
            AddToPlaylistDialog(
                playlists = uiState.playlists,
                onPlaylistSelected = { playlist -> onAddToPlaylistMultipleConfirmed(playlist, dialogState.files) },
                onCreateAndAdd = onCreatePlaylistAndAdd,
                onDismiss = onDismissDialog
            )
        }
        DialogState.None -> Unit
    }
}