package com.example.newaudio.feature.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.newaudio.R
import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.Song
import com.example.newaudio.feature.player.PlayerViewModel
import com.example.newaudio.feature.playlist.components.PlaylistContent
import com.example.newaudio.feature.playlist.components.PlaylistInputDialog
import com.example.newaudio.ui.theme.Dimens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val activeSongPath by remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.currentSong?.path }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-scroll logic for reordering
    val selectedPlaylistId = remember(uiState.selectedPlaylistIds) { uiState.selectedPlaylistIds.firstOrNull() }
    val selectedSong = remember(uiState.selectedSongs) { uiState.selectedSongs.firstOrNull() }
    
    LaunchedEffect(uiState.playlists, uiState.playlistSongs, selectedPlaylistId, selectedSong, uiState.isEditMode) {
        if (uiState.isEditMode) {
            val totalSelected = uiState.selectedPlaylistIds.size + uiState.selectedSongs.size
            if (totalSelected == 1) {
                val targetKey = when {
                    selectedPlaylistId != null -> "playlist_$selectedPlaylistId"
                    selectedSong != null -> "playlist_${selectedSong.playlistId}_song_${selectedSong.songPath}"
                    else -> null
                }

                targetKey?.let { key ->
                    val visibleItems = listState.layoutInfo.visibleItemsInfo
                    val itemInfo = visibleItems.find { it.key == key }

                    if (itemInfo != null) {
                        val viewportHeight = listState.layoutInfo.viewportSize.height
                        val itemTop = itemInfo.offset
                        val itemBottom = itemInfo.offset + itemInfo.size
                        val threshold = 150 

                        if (itemTop < threshold || itemBottom > (viewportHeight - threshold)) {
                            listState.animateScrollToItem(
                                index = itemInfo.index,
                                scrollOffset = -(viewportHeight / 3)
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaylistEvent.PlayPlaylist -> onPlaySongs(event.songs, 0)
                is PlaylistEvent.PlaySongInPlaylist -> onPlaySongs(event.allSongs, event.allSongs.indexOf(event.song))
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is PlaylistSideEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message.asString(context))
                }
            }
        }
    }

    BackHandler(enabled = uiState.isEditMode) {
        viewModel.toggleEditMode()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (uiState.isEditMode) {
                TopAppBar(
                    title = {
                        val totalSelected = uiState.selectedSongs.size + uiState.selectedPlaylistIds.size
                        Text(stringResource(R.string.selected_count, totalSelected))
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.toggleEditMode() }) {
                            Icon(Icons.Default.Close, stringResource(R.string.cancel))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.playlists)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleEditMode() }) {
                            Icon(Icons.Default.Checklist, stringResource(R.string.edit_mode))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isEditMode && (uiState.selectedSongs.isNotEmpty() || uiState.selectedPlaylistIds.isNotEmpty()),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(
                    tonalElevation = Dimens.ElevationMedium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(Dimens.PaddingSmall)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val singleSelection = (uiState.selectedSongs.size + uiState.selectedPlaylistIds.size) == 1
                        
                        Button(
                            onClick = { viewModel.removeSelected() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.delete))
                        }

                        Spacer(Modifier.width(Dimens.PaddingLarge))

                        Row {
                            IconButton(
                                onClick = { viewModel.moveSelectedUp() },
                                enabled = singleSelection
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = stringResource(R.string.reorder_up),
                                    tint = if (singleSelection) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.38f)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.moveSelectedDown() },
                                enabled = singleSelection
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = stringResource(R.string.reorder_down),
                                    tint = if (singleSelection) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!uiState.isEditMode && !uiState.isLoading) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.playlist_create))
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading && uiState.playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            PlaylistContent(
                uiState = uiState,
                activeSongPath = activeSongPath,
                modifier = Modifier.padding(padding),
                listState = listState,
                onPlaylistClick = { id ->
                    if (uiState.isEditMode) {
                        viewModel.togglePlaylistSelection(id)
                    } else {
                        viewModel.togglePlaylistExpansion(id)
                    }
                },
                onPlaylistLongClick = { viewModel.onItemLongClicked(it) },
                onRenameClick = { playlistToRename = it },
                onDeleteClick = { viewModel.onDeletePlaylist(it) },
                onDuplicateClick = { viewModel.onDuplicatePlaylist(it) },
                onPlayPlaylistClick = { viewModel.onPlayPlaylist(it) },
                onSongClick = { song, pId ->
                    if (uiState.isEditMode) {
                        viewModel.toggleSongSelection(pId, song.path)
                    } else {
                        viewModel.onPlaySongInPlaylist(song, pId)
                    }
                },
                onSongLongClick = { pId, song -> 
                    viewModel.onSongLongClicked(pId, song)
                }
            )
        }

        if (showCreateDialog) {
            PlaylistInputDialog(
                title = stringResource(R.string.playlist_create),
                initialName = "",
                onDismiss = { showCreateDialog = false },
                onConfirm = { name ->
                    viewModel.onCreatePlaylist(name)
                    showCreateDialog = false
                }
            )
        }

        playlistToRename?.let { playlist ->
            PlaylistInputDialog(
                title = stringResource(R.string.playlist_edit),
                initialName = playlist.name,
                onDismiss = { playlistToRename = null },
                onConfirm = { newName ->
                    viewModel.onRenamePlaylist(playlist, newName)
                    playlistToRename = null
                }
            )
        }
    }
}
