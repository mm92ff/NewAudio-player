package com.example.newaudio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val isPlaying: Boolean = false,
    val totalDuration: Long = 0L,
    val progressBarHeight: Float = 0f
)

@Composable
fun MainAppScreen(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    equalizerViewModel: EqualizerViewModel = hiltViewModel(),
    fileBrowserViewModel: FileBrowserViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    var showConsole by remember { mutableStateOf(false) }

    // Do NOT show MiniPlayer on the player screen
    val showMiniPlayer by remember(navBackStackEntry) {
        derivedStateOf {
            navBackStackEntry?.destination?.hierarchy?.any { it.hasRoute<Player>() } == false
        }
    }

    Scaffold(
        bottomBar = {
            if (showMiniPlayer) {
                MiniPlayerBottomBar(
                    playerViewModel = playerViewModel,
                    onOpenPlayer = { navController.navigate(Player) }
                )
            }
        }
    ) { innerPadding ->
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
                    onPlaylistClick = { navController.navigate(Playlist) }
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
                    }
                )
            }
        }

        if (showConsole) {
            ConsoleOverlay(onClose = { showConsole = false })
        }
    }
}

@Composable
private fun MiniPlayerBottomBar(
    playerViewModel: PlayerViewModel,
    onOpenPlayer: () -> Unit
) {
    // Static part: filters out position ticks
    val staticFlow = remember(playerViewModel) {
        playerViewModel.uiState
            .map { s ->
                val song = s.currentSong
                MiniBarStaticState(
                    hasSong = song != null,
                    title = song?.title.orEmpty(),
                    artist = song?.artist.orEmpty(),
                    isPlaying = s.isPlaying,
                    totalDuration = s.totalDuration,
                    progressBarHeight = s.miniPlayerProgressBarHeight
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

    MiniPlayer(
        title = staticState.title,
        artist = staticState.artist,
        isPlaying = staticState.isPlaying,
        totalDuration = staticState.totalDuration,
        progressBarHeight = staticState.progressBarHeight,
        currentPositionFlow = positionFlow,
        onPlayPauseClicked = onPlayPause,
        onSkipNextClicked = onSkipNext,
        onSeek = onSeek,
        modifier = Modifier.clickable { onOpenPlayer() }
    )
}
