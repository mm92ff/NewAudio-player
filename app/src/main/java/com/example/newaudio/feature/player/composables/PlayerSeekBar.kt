package com.example.newaudio.feature.player.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.newaudio.R
import com.example.newaudio.util.formatDurationMMSS
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSeekBar(
    currentPosition: Long,
    totalDuration: Long,
    onSeek: (Float) -> Unit,
    progressBarHeight: Float,
    modifier: Modifier = Modifier
) {
    val isEnabled = totalDuration > 0

    var isDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    // ✅ Sync only via Effect, not during Composition
    LaunchedEffect(currentPosition, totalDuration, isDragging) {
        if (!isDragging && isEnabled) {
            val clamped = min(max(currentPosition, 0L), totalDuration)
            sliderValue = clamped.toFloat()
        }
        if (!isEnabled) {
            sliderValue = 0f
        }
    }

    val formattedCurrentPosition by remember {
        derivedStateOf { formatDurationMMSS(sliderValue.toLong()) }
    }
    val formattedTotalDuration by remember(totalDuration) {
        derivedStateOf { formatDurationMMSS(totalDuration) }
    }

    val seekBarDesc = stringResource(R.string.seek_bar_description)

    Column(modifier = modifier) {
        Slider(
            value = sliderValue,
            onValueChange = {
                isDragging = true
                sliderValue = it
            },
            onValueChangeFinished = {
                onSeek(sliderValue)   // sliderValue ist ms (Range 0..totalDuration)
                isDragging = false
            },
            valueRange = if (isEnabled) 0f..totalDuration.toFloat() else 0f..1f,
            enabled = isEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = seekBarDesc },
            thumb = { Box(modifier = Modifier.size(0.dp)) },
            track = { sliderState ->
                val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                val fraction = if (range > 0f) {
                    ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                } else 0f

                Box(
                    modifier = Modifier
                        .height(progressBarHeight.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(percent = 50))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary)
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                    )
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formattedCurrentPosition, style = MaterialTheme.typography.labelSmall)
            Text(text = formattedTotalDuration, style = MaterialTheme.typography.labelSmall)
        }
    }
}
