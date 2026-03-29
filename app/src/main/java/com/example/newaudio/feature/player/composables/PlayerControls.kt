package com.example.newaudio.feature.player.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.newaudio.R
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.ui.theme.Dimens

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: UserPreferences.RepeatMode,
    onPlayPauseClicked: () -> Unit,
    onSkipPreviousClicked: () -> Unit,
    onSkipNextClicked: () -> Unit,
    onShuffleClicked: () -> Unit,
    onRepeatClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onShuffleClicked) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = stringResource(R.string.shuffle),
                tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onSkipPreviousClicked, modifier = Modifier.size(Dimens.FullScreenPlayer_ControlSize)) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.previous),
                modifier = Modifier.size(Dimens.FullScreenPlayer_ControlIconSize)
            )
        }

        FilledIconButton(
            onClick = onPlayPauseClicked,
            modifier = Modifier.size(Dimens.FullScreenPlayer_MainButtonSize)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.play_pause),
                modifier = Modifier.size(Dimens.FullScreenPlayer_ControlIconSize)
            )
        }

        IconButton(onClick = onSkipNextClicked, modifier = Modifier.size(Dimens.FullScreenPlayer_ControlSize)) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.next),
                modifier = Modifier.size(Dimens.FullScreenPlayer_ControlIconSize)
            )
        }

        IconButton(onClick = onRepeatClicked) {
            val (icon, tint) = when (repeatMode) {
                UserPreferences.RepeatMode.ONE -> Pair(Icons.Filled.RepeatOne, MaterialTheme.colorScheme.primary)
                UserPreferences.RepeatMode.ALL -> Pair(Icons.Filled.Repeat, MaterialTheme.colorScheme.primary)
                UserPreferences.RepeatMode.NONE -> Pair(Icons.Filled.Repeat, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(imageVector = icon, contentDescription = stringResource(R.string.repeat), tint = tint)
        }
    }
}
