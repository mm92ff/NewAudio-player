package com.example.newaudio.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.newaudio.R
import com.example.newaudio.ui.theme.Dimens
import kotlinx.coroutines.flow.Flow

@Composable
private fun MiniPlayerProgressBar(
    currentPosition: Long,
    totalDuration: Long,
    height: Float,
    onSeek: (Float) -> Unit // position in ms
) {
    val progress = if (totalDuration > 0) {
        (currentPosition.toFloat() / totalDuration).coerceIn(0f, 1f)
    } else 0f

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .drawWithContent {
                // 1. Draw background (track)
                drawRect(color = trackColor)

                // 2. Draw progress on top
                drawRect(
                    color = progressColor,
                    size = size.copy(width = size.width * progress)
                )
            }
            .pointerInput(totalDuration) {
                if (totalDuration > 0) {
                    detectTapGestures { tapOffset ->
                        val ratio = (tapOffset.x / size.width).coerceIn(0f, 1f)
                        val seekPositionMs = ratio * totalDuration.toFloat()
                        onSeek(seekPositionMs)
                    }
                }
            }
    )
}

@Composable
private fun MiniPlayerProgressBarHost(
    currentPositionFlow: Flow<Long>,
    totalDuration: Long,
    height: Float,
    onSeek: (Float) -> Unit
) {
    val currentPosition by currentPositionFlow.collectAsStateWithLifecycle(initialValue = 0L)

    MiniPlayerProgressBar(
        currentPosition = currentPosition,
        totalDuration = totalDuration,
        height = height,
        onSeek = onSeek
    )
}

@Composable
private fun PlayerControls(
    title: String,
    artist: String,
    isPlaying: Boolean,
    enabled: Boolean, // CHANGE: New parameter
    onPlayPauseClicked: () -> Unit,
    onSkipNextClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(Dimens.MiniPlayerHeight)
            .padding(horizontal = Dimens.PaddingMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            if (artist.isNotEmpty()) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onPlayPauseClicked,
                enabled = enabled // Disable button when no song is loaded
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.play_pause)
                )
            }
            IconButton(
                onClick = onSkipNextClicked,
                enabled = enabled // Disable button when no song is loaded
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.next)
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    title: String,
    artist: String,
    isPlaying: Boolean,
    totalDuration: Long,
    progressBarHeight: Float,
    currentPositionFlow: Flow<Long>,
    onPlayPauseClicked: () -> Unit,
    onSkipNextClicked: () -> Unit,
    onSeek: (Float) -> Unit, // position in ms
    isControlsEnabled: Boolean = true, // CHANGE: New parameter, default true
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        tonalElevation = Dimens.ElevationSmall,
        shadowElevation = Dimens.ElevationSmall
    ) {
        Column {
            MiniPlayerProgressBarHost(
                currentPositionFlow = currentPositionFlow,
                totalDuration = totalDuration,
                height = progressBarHeight,
                onSeek = onSeek
            )

            PlayerControls(
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                enabled = isControlsEnabled, // Pass value through
                onPlayPauseClicked = onPlayPauseClicked,
                onSkipNextClicked = onSkipNextClicked
            )
        }
    }
}