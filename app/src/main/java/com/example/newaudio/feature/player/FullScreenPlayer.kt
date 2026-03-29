package com.example.newaudio.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.newaudio.R
import com.example.newaudio.domain.repository.IEqualizerRepository
import com.example.newaudio.feature.player.composables.PlayerAlbumArt
import com.example.newaudio.feature.player.composables.PlayerControls
import com.example.newaudio.feature.player.composables.PlayerSeekBar
import com.example.newaudio.feature.player.composables.PlayerTopAppBar
import com.example.newaudio.feature.player.composables.SongDetails
import com.example.newaudio.feature.player.composables.SongMetadataDialog
import com.example.newaudio.ui.theme.Dimens
import kotlinx.coroutines.flow.Flow
import kotlin.math.min

@Composable
private fun PlayerSeekBarHost(
    currentPositionFlow: Flow<Long>,
    totalDuration: Long,
    progressBarHeight: Float,
    onSeek: (Float) -> Unit
) {
    // ✅ Position ticks ONLY HERE → only the SeekBar subtree recomposes
    val currentPosition by currentPositionFlow.collectAsStateWithLifecycle(initialValue = 0L)

    PlayerSeekBar(
        currentPosition = currentPosition,
        totalDuration = totalDuration,
        onSeek = onSeek,
        progressBarHeight = progressBarHeight
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPlayer(
    uiState: PlayerUiState,                 // ⚠️ arrives WITHOUT currentPosition ticks
    currentPositionFlow: Flow<Long>,         // ✅ ticks only inside the SeekBar
    onBackClicked: () -> Unit,
    onPlayPauseClicked: () -> Unit,
    onSkipPreviousClicked: () -> Unit,
    onSkipNextClicked: () -> Unit,
    onShuffleClicked: () -> Unit,
    onRepeatClicked: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleEqualizer: () -> Unit,
    onSetBandLevel: (Int, Float) -> Unit,
    onApplyPreset: (IEqualizerRepository.EqPreset) -> Unit,
    onShowSongMetadata: () -> Unit,
    onDismissSongMetadataDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEqualizerSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showEqualizerSheet) {
        ModalBottomSheet(onDismissRequest = { showEqualizerSheet = false }) {
            EqualizerSheet(
                equalizerState = uiState.equalizerState,
                onEnabledChange = { onToggleEqualizer() },
                onBandLevelChange = onSetBandLevel,
                onPresetSelected = onApplyPreset
            )
        }
    }

    uiState.songMetadata?.let {
        SongMetadataDialog(
            metadata = it,
            onDismiss = onDismissSongMetadataDialog
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            PlayerTopAppBar(
                onBackClicked = onBackClicked,
                onEqualizerClicked = { showEqualizerSheet = true }
            )
        }
    ) { innerPadding ->
        val song = uiState.currentSong

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Dimens.PaddingLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val artSize = min(maxWidth.value, maxHeight.value).dp * 0.9f

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    PlayerAlbumArt(
                        songPath = song?.path,
                        modifier = Modifier
                            .size(artSize)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onShowSongMetadata() }
                    )

                    Spacer(modifier = Modifier.height(Dimens.PaddingLarge))

                    SongDetails(
                        title = song?.title,
                        artist = song?.artist,
                        useMarquee = uiState.useMarquee,
                        modifier = Modifier.clickable { onBackClicked() }
                    )

                    Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

                    uiState.errorRes?.let {
                        Text(
                            text = stringResource(R.string.error_prefix, it.asString(context)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ✅ SeekBar reads the ticking position exclusively in the host
            PlayerSeekBarHost(
                currentPositionFlow = currentPositionFlow,
                totalDuration = uiState.totalDuration,
                progressBarHeight = uiState.fullScreenPlayerProgressBarHeight,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(Dimens.PaddingLarge))

            PlayerControls(
                isPlaying = uiState.isPlaying,
                isShuffleEnabled = uiState.isShuffleEnabled,
                repeatMode = uiState.repeatMode,
                onPlayPauseClicked = onPlayPauseClicked,
                onSkipPreviousClicked = onSkipPreviousClicked,
                onSkipNextClicked = onSkipNextClicked,
                onShuffleClicked = onShuffleClicked,
                onRepeatClicked = onRepeatClicked
            )
        }
    }
}
