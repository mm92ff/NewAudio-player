package com.example.newaudio.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player as MediaPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.newaudio.feature.console.ConsoleOverlay
import com.example.newaudio.feature.filebrowser.FileBrowserRoute
import com.example.newaudio.feature.filebrowser.FileBrowserViewModel
import com.example.newaudio.feature.player.EqualizerViewModel
import com.example.newaudio.feature.player.FullScreenPlayer
import com.example.newaudio.feature.player.MiniPlayer
import com.example.newaudio.feature.player.PlayerUiState
import com.example.newaudio.feature.player.PlayerViewModel
import com.example.newaudio.feature.player.VideoFullscreenOverlay
import com.example.newaudio.domain.model.VideoMarker
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import com.example.newaudio.feature.playlist.PlaylistScreen
import com.example.newaudio.feature.playlist.PlaylistViewModel
import com.example.newaudio.feature.settings.SettingsScreen
import com.example.newaudio.navigation.Browser
import com.example.newaudio.navigation.Player
import com.example.newaudio.navigation.Playlist
import com.example.newaudio.navigation.Settings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private data class MiniBarStaticState(
    val hasSong: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val isVideo: Boolean = false,
    val isPlaying: Boolean = false,
    val totalDuration: Long = 0L,
    val progressBarHeight: Float = 0f,
    val useMarquee: Boolean = false
)

private data class VideoFullscreenStaticState(
    val player: MediaPlayer? = null,
    val hasVideo: Boolean = false,
    val markersEnabled: Boolean = false,
    val markers: ImmutableList<VideoMarker> = persistentListOf()
)

private enum class VideoPlayerTarget {
    INLINE,
    FULLSCREEN
}

@Composable
@OptIn(UnstableApi::class)
fun MainAppScreen(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    equalizerViewModel: EqualizerViewModel = hiltViewModel(),
    fileBrowserViewModel: FileBrowserViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    var showConsole by remember { mutableStateOf(false) }
    var isVideoFullscreen by remember { mutableStateOf(false) }
    var inlineVideoPlayerView by remember { mutableStateOf<PlayerView?>(null) }
    var fullscreenVideoPlayerView by remember { mutableStateOf<PlayerView?>(null) }
    var activeVideoPlayerTarget by remember { mutableStateOf(VideoPlayerTarget.INLINE) }

    val videoFullscreenFlow = remember(playerViewModel) {
        playerViewModel.uiState
            .map { state ->
                VideoFullscreenStaticState(
                    player = state.player,
                    hasVideo = state.currentVideo != null,
                    markersEnabled = state.videoMarkersEnabled,
                    markers = state.videoMarkers
                )
            }
            .distinctUntilChanged()
    }
    val videoFullscreenState by videoFullscreenFlow.collectAsStateWithLifecycle(
        initialValue = VideoFullscreenStaticState()
    )

    fun switchVideoTargetToInline() {
        val player = videoFullscreenState.player
        val inlineView = inlineVideoPlayerView
        val fullscreenView = fullscreenVideoPlayerView

        if (player != null && inlineView != null) {
            if (fullscreenView != null && activeVideoPlayerTarget == VideoPlayerTarget.FULLSCREEN) {
                PlayerView.switchTargetView(player, fullscreenView, inlineView)
            } else {
                inlineView.player = player
            }
            activeVideoPlayerTarget = VideoPlayerTarget.INLINE
        }

        isVideoFullscreen = false
    }

    LaunchedEffect(videoFullscreenState.hasVideo) {
        if (!videoFullscreenState.hasVideo) {
            switchVideoTargetToInline()
        }
    }

    LaunchedEffect(
        isVideoFullscreen,
        videoFullscreenState.player,
        inlineVideoPlayerView,
        fullscreenVideoPlayerView
    ) {
        val player = videoFullscreenState.player
        val fullscreenView = fullscreenVideoPlayerView
        if (!isVideoFullscreen || player == null || fullscreenView == null) return@LaunchedEffect
        if (activeVideoPlayerTarget == VideoPlayerTarget.FULLSCREEN) return@LaunchedEffect

        val inlineView = inlineVideoPlayerView
        if (inlineView != null) {
            PlayerView.switchTargetView(player, inlineView, fullscreenView)
        } else {
            fullscreenView.player = player
        }
        activeVideoPlayerTarget = VideoPlayerTarget.FULLSCREEN
    }

    // Do NOT show MiniPlayer on the player screen
    val showMiniPlayer by remember(navBackStackEntry) {
        derivedStateOf {
            navBackStackEntry?.destination?.hierarchy?.any { it.hasRoute<Player>() } == false
        }
    }

    Scaffold(
        bottomBar = {
            if (showMiniPlayer && !isVideoFullscreen) {
                MiniPlayerBottomBar(
                    playerViewModel = playerViewModel,
                    fileBrowserViewModel = fileBrowserViewModel,
                    onOpenPlayer = { navController.navigate(Player) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Browser,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable<Browser> {
                    FileBrowserRoute(
                        fileBrowserViewModel = fileBrowserViewModel,
                        playerViewModel = playerViewModel,
                        onSettingsClick = { navController.navigate(Settings) },
                        onPlaylistClick = { navController.navigate(Playlist) },
                        onToggleVideoFullscreen = {
                            if (videoFullscreenState.hasVideo && videoFullscreenState.player != null) {
                                if (isVideoFullscreen) {
                                    switchVideoTargetToInline()
                                } else {
                                    isVideoFullscreen = true
                                }
                            }
                        },
                        isVideoFullscreen = isVideoFullscreen,
                        onInlinePlayerViewChanged = { playerView ->
                            inlineVideoPlayerView = playerView
                        }
                    )
                }

                composable<Player> {
                    // Static state (without currentPosition ticks)
                    val staticUiFlow = remember(playerViewModel) {
                        playerViewModel.uiState
                            .map { it.copy(currentPosition = 0L) }
                            .distinctUntilChanged()
                    }
                    val staticUiState by staticUiFlow.collectAsStateWithLifecycle(
                        initialValue = PlayerUiState()
                    )

                    // Position separately (ticks), only for SeekBar
                    val positionFlow = remember(playerViewModel) {
                        playerViewModel.uiState
                            .map { it.currentPosition }
                            .distinctUntilChanged()
                    }

                    FullScreenPlayer(
                        uiState = staticUiState,
                        currentPositionFlow = positionFlow,
                        errorEvents = playerViewModel.errorEvents,
                        onBackClicked = { navController.popBackStack() },
                        onPlayPauseClicked = playerViewModel::onPlayPauseToggle,
                        onSkipPreviousClicked = playerViewModel::onSkipPrevious,
                        onSkipNextClicked = playerViewModel::onSkipNext,
                        onShuffleClicked = playerViewModel::onToggleShuffle,
                        onRepeatClicked = playerViewModel::onCycleRepeatMode,
                        onSeek = playerViewModel::onSeek,
                        onToggleEqualizer = equalizerViewModel::onToggleEqualizerEnabled,
                        onSetBandLevel = equalizerViewModel::onSetBandLevel,
                        onApplyPreset = equalizerViewModel::onApplyPreset,
                        onShowSongMetadata = playerViewModel::onShowSongMetadata,
                        onDismissSongMetadataDialog = playerViewModel::onDismissSongMetadataDialog
                    )
                }

                composable<Settings> {
                    SettingsScreen(onShowConsole = { showConsole = true })
                }

                composable<Playlist> {
                    val playlistViewModel: PlaylistViewModel = hiltViewModel()
                    PlaylistScreen(
                        viewModel = playlistViewModel,
                        onBackClick = { navController.popBackStack() },
                        onPlaySongs = { songs, startIndex ->
                            playerViewModel.onPlayPlaylist(songs, startIndex)
                        },
                        onPlayVideos = { videos, startIndex ->
                            playerViewModel.onPlayVideoPlaylist(videos, startIndex)
                            fileBrowserViewModel.onShowInlineVideo()
                            navController.navigate(Browser)
                        }
                    )
                }
            }

            if (showConsole) {
                ConsoleOverlay(onClose = { showConsole = false })
            }

            val fullscreenPlayer = videoFullscreenState.player
            if (isVideoFullscreen && videoFullscreenState.hasVideo && fullscreenPlayer != null) {
                VideoFullscreenOverlay(
                    player = fullscreenPlayer,
                    onToggleFullscreen = ::switchVideoTargetToInline,
                    onExitFullscreen = ::switchVideoTargetToInline,
                    onSwipeNext = playerViewModel::onSkipNext,
                    onSwipePrevious = playerViewModel::onSkipPrevious,
                    markersEnabled = videoFullscreenState.markersEnabled,
                    markers = videoFullscreenState.markers,
                    onAddMarker = playerViewModel::onAddVideoMarker,
                    onMoveMarker = playerViewModel::onMoveVideoMarker,
                    onDeleteMarker = playerViewModel::onDeleteVideoMarker,
                    onPlayerViewChanged = { playerView ->
                        fullscreenVideoPlayerView = playerView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerBottomBar(
    playerViewModel: PlayerViewModel,
    fileBrowserViewModel: FileBrowserViewModel,
    onOpenPlayer: () -> Unit
) {
    // Static part: filters out position ticks
    val staticFlow = remember(playerViewModel) {
        playerViewModel.uiState
            .map { s ->
                val song = s.currentSong
                val video = s.currentVideo
                MiniBarStaticState(
                    hasSong = song != null || video != null,
                    title = song?.title ?: video?.title.orEmpty(),
                    artist = song?.artist.orEmpty(),
                    isVideo = video != null,
                    isPlaying = s.isPlaying,
                    totalDuration = s.totalDuration,
                    progressBarHeight = s.miniPlayerProgressBarHeight,
                    useMarquee = s.useMarquee
                )
            }
            .distinctUntilChanged()
    }
    val staticState by staticFlow.collectAsStateWithLifecycle(
        initialValue = MiniBarStaticState()
    )

    // Position separately (ticks), only for progress bar
    val positionFlow = remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.currentPosition }
            .distinctUntilChanged()
    }

    if (!staticState.hasSong) return

    val onPlayPause = remember(playerViewModel) { { playerViewModel.onPlayPauseToggle() } }
    val onSkipNext = remember(playerViewModel) { { playerViewModel.onSkipNext() } }
    val onSeek = remember(playerViewModel) { { pos: Float -> playerViewModel.onSeek(pos) } }
    val onTitleClick = remember(staticState.isVideo, fileBrowserViewModel, onOpenPlayer) {
        {
            if (staticState.isVideo) {
                fileBrowserViewModel.onShowMiniPlayerVideoInline()
            } else {
                onOpenPlayer()
            }
        }
    }
    MiniPlayer(
        title = staticState.title,
        artist = staticState.artist,
        isPlaying = staticState.isPlaying,
        totalDuration = staticState.totalDuration,
        progressBarHeight = staticState.progressBarHeight,
        useMarquee = staticState.useMarquee,
        currentPositionFlow = positionFlow,
        onPlayPauseClicked = onPlayPause,
        onSkipNextClicked = onSkipNext,
        onSeek = onSeek,
        onTitleClicked = onTitleClick
    )
}
