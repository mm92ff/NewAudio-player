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
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.example.newaudio.R
import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.model.VideoPlaylist
import com.example.newaudio.feature.player.PlayerViewModel
import com.example.newaudio.feature.playlist.components.PlaylistContent
import com.example.newaudio.feature.playlist.components.PlaylistInputDialog
import com.example.newaudio.feature.playlist.components.VideoPlaylistContent
import com.example.newaudio.ui.theme.Dimens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private enum class PlaylistMediaTab {
    AUDIO,
    VIDEO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    videoViewModel: VideoPlaylistViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onPlayVideos: (List<Video>, Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val videoUiState by videoViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val videoListState = rememberLazyListState()
    var selectedTab by remember { mutableStateOf(PlaylistMediaTab.AUDIO) }
    val isEditMode = when (selectedTab) {
        PlaylistMediaTab.AUDIO -> uiState.isEditMode
        PlaylistMediaTab.VIDEO -> videoUiState.isEditMode
    }

    val activeSongPath by remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.currentSong?.path }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val activeVideoPath by remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.currentVideo?.path }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    val videoThumbnailImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var videoPlaylistToRename by remember { mutableStateOf<VideoPlaylist?>(null) }
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
        videoViewModel.events.collect { event ->
            when (event) {
                is VideoPlaylistEvent.PlayVideoPlaylist -> onPlayVideos(event.videos, 0)
                is VideoPlaylistEvent.PlayVideoInPlaylist -> {
                    val startIndex = event.allVideos.indexOf(event.video).coerceAtLeast(0)
                    onPlayVideos(event.allVideos, startIndex)
                }
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

    LaunchedEffect(Unit) {
        videoViewModel.sideEffects.collect { effect ->
            when (effect) {
                is PlaylistSideEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message.asString(context))
                }
            }
        }
    }

    BackHandler(enabled = isEditMode) {
        when (selectedTab) {
            PlaylistMediaTab.AUDIO -> viewModel.toggleEditMode()
            PlaylistMediaTab.VIDEO -> videoViewModel.toggleEditMode()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (isEditMode) {
                TopAppBar(
                    title = {
                        val totalSelected = when (selectedTab) {
                            PlaylistMediaTab.AUDIO -> uiState.selectedSongs.size + uiState.selectedPlaylistIds.size
                            PlaylistMediaTab.VIDEO -> videoUiState.selectedVideos.size + videoUiState.selectedPlaylistIds.size
                        }
                        Text(stringResource(R.string.selected_count, totalSelected))
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            when (selectedTab) {
                                PlaylistMediaTab.AUDIO -> viewModel.toggleEditMode()
                                PlaylistMediaTab.VIDEO -> videoViewModel.toggleEditMode()
                            }
                        }) {
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
                        IconButton(onClick = {
                            when (selectedTab) {
                                PlaylistMediaTab.AUDIO -> viewModel.toggleEditMode()
                                PlaylistMediaTab.VIDEO -> videoViewModel.toggleEditMode()
                            }
                        }) {
                            Icon(Icons.Default.Checklist, stringResource(R.string.edit_mode))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = when (selectedTab) {
                    PlaylistMediaTab.AUDIO -> uiState.isEditMode && (uiState.selectedSongs.isNotEmpty() || uiState.selectedPlaylistIds.isNotEmpty())
                    PlaylistMediaTab.VIDEO -> videoUiState.isEditMode && (videoUiState.selectedVideos.isNotEmpty() || videoUiState.selectedPlaylistIds.isNotEmpty())
                },
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
                        val singleSelection = when (selectedTab) {
                            PlaylistMediaTab.AUDIO -> (uiState.selectedSongs.size + uiState.selectedPlaylistIds.size) == 1
                            PlaylistMediaTab.VIDEO -> (videoUiState.selectedVideos.size + videoUiState.selectedPlaylistIds.size) == 1
                        }
                        
                        Button(
                            onClick = {
                                when (selectedTab) {
                                    PlaylistMediaTab.AUDIO -> viewModel.removeSelected()
                                    PlaylistMediaTab.VIDEO -> videoViewModel.removeSelected()
                                }
                            },
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
                                onClick = {
                                    when (selectedTab) {
                                        PlaylistMediaTab.AUDIO -> viewModel.moveSelectedUp()
                                        PlaylistMediaTab.VIDEO -> videoViewModel.moveSelectedUp()
                                    }
                                },
                                enabled = singleSelection
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = stringResource(R.string.reorder_up),
                                    tint = if (singleSelection) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.38f)
                                )
                            }
                            IconButton(
                                onClick = {
                                    when (selectedTab) {
                                        PlaylistMediaTab.AUDIO -> viewModel.moveSelectedDown()
                                        PlaylistMediaTab.VIDEO -> videoViewModel.moveSelectedDown()
                                    }
                                },
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
            if (!isEditMode && !uiState.isLoading && !videoUiState.isLoading) {
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
        val isLoading = when (selectedTab) {
            PlaylistMediaTab.AUDIO -> uiState.isLoading && uiState.playlists.isEmpty()
            PlaylistMediaTab.VIDEO -> videoUiState.isLoading && videoUiState.playlists.isEmpty()
        }
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(padding)) {
                TabRow(selectedTabIndex = if (selectedTab == PlaylistMediaTab.AUDIO) 0 else 1) {
                    Tab(
                        selected = selectedTab == PlaylistMediaTab.AUDIO,
                        onClick = { selectedTab = PlaylistMediaTab.AUDIO },
                        text = { Text(stringResource(R.string.music_mode)) }
                    )
                    Tab(
                        selected = selectedTab == PlaylistMediaTab.VIDEO,
                        onClick = { selectedTab = PlaylistMediaTab.VIDEO },
                        text = { Text(stringResource(R.string.video_mode)) }
                    )
                }
                when (selectedTab) {
                    PlaylistMediaTab.AUDIO -> PlaylistContent(
                        uiState = uiState,
                        activeSongPath = activeSongPath,
                        modifier = Modifier.fillMaxSize(),
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
                    PlaylistMediaTab.VIDEO -> VideoPlaylistContent(
                        uiState = videoUiState,
                        activeVideoPath = activeVideoPath,
                        imageLoader = videoThumbnailImageLoader,
                        modifier = Modifier.fillMaxSize(),
                        listState = videoListState,
                        onPlaylistClick = { id ->
                            if (videoUiState.isEditMode) {
                                videoViewModel.togglePlaylistSelection(id)
                            } else {
                                videoViewModel.togglePlaylistExpansion(id)
                            }
                        },
                        onPlaylistLongClick = { videoViewModel.onItemLongClicked(it) },
                        onRenameClick = { videoPlaylistToRename = it },
                        onDeleteClick = { videoViewModel.onDeletePlaylist(it) },
                        onDuplicateClick = { videoViewModel.onDuplicatePlaylist(it) },
                        onPlayPlaylistClick = { videoViewModel.onPlayPlaylist(it) },
                        onVideoClick = { video, pId ->
                            if (videoUiState.isEditMode) {
                                videoViewModel.toggleVideoSelection(pId, video.path)
                            } else {
                                videoViewModel.onPlayVideoInPlaylist(video, pId)
                            }
                        },
                        onVideoLongClick = { pId, video ->
                            videoViewModel.onVideoLongClicked(pId, video)
                        }
                    )
                }
            }
        }

        if (showCreateDialog) {
            PlaylistInputDialog(
                title = stringResource(R.string.playlist_create),
                initialName = "",
                onDismiss = { showCreateDialog = false },
                onConfirm = { name ->
                    when (selectedTab) {
                        PlaylistMediaTab.AUDIO -> viewModel.onCreatePlaylist(name)
                        PlaylistMediaTab.VIDEO -> videoViewModel.onCreatePlaylist(name)
                    }
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

        videoPlaylistToRename?.let { playlist ->
            PlaylistInputDialog(
                title = stringResource(R.string.playlist_edit),
                initialName = playlist.name,
                onDismiss = { videoPlaylistToRename = null },
                onConfirm = { newName ->
                    videoViewModel.onRenamePlaylist(playlist, newName)
                    videoPlaylistToRename = null
                }
            )
        }
    }
}
