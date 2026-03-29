package com.example.newaudio.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import com.example.newaudio.R
import com.example.newaudio.domain.repository.IEqualizerRepository
import com.example.newaudio.ui.theme.Dimens
import com.example.newaudio.util.Constants
import kotlin.math.roundToInt

@Composable
fun EqualizerSheet(
    equalizerState: IEqualizerRepository.EqualizerState,
    onEnabledChange: (Boolean) -> Unit,
    onBandLevelChange: (Int, Float) -> Unit,
    onPresetSelected: (IEqualizerRepository.EqPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.PaddingMedium)
            .navigationBarsPadding()
    ) {
        EqualizerHeader(
            enabled = equalizerState.enabled,
            currentPreset = equalizerState.currentPreset,
            onEnabledChange = onEnabledChange,
            onPresetSelected = onPresetSelected
        )

        Spacer(modifier = Modifier.height(Dimens.EqualizerSheet_SpacerHeight))

        EqualizerBandRow(
            bands = equalizerState.bands,
            isEnabled = equalizerState.enabled,
            onBandLevelChange = onBandLevelChange
        )
    }
}

@Composable
private fun EqualizerHeader(
    enabled: Boolean,
    currentPreset: IEqualizerRepository.EqPreset,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelected: (IEqualizerRepository.EqPreset) -> Unit
) {
    var presetMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.equalizer),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
        )

        Box(modifier = Modifier.padding(end = Dimens.PaddingSmall)) {
            AssistChip(
                onClick = { presetMenuExpanded = true },
                label = {
                    Text(
                        text = getPresetName(currentPreset),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                trailingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.EqualizerSheet_DropDownIconSize)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                enabled = enabled
            )

            DropdownMenu(
                expanded = presetMenuExpanded,
                onDismissRequest = { presetMenuExpanded = false }
            ) {
                IEqualizerRepository.EqPreset.entries.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = getPresetName(preset),
                                fontWeight = if (preset == currentPreset) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onPresetSelected(preset)
                            presetMenuExpanded = false
                        }
                    )
                }
            }
        }

        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun EqualizerBandRow(
    bands: List<IEqualizerRepository.EqualizerBand>,
    isEnabled: Boolean,
    onBandLevelChange: (Int, Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.EqualizerSheet_SliderHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        if (bands.isNotEmpty()) {
            bands.forEach { band ->
                VerticalEqualizerSlider(
                    band = band,
                    isEnabled = isEnabled,
                    onLevelChange = { level -> onBandLevelChange(band.id, level) }
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun VerticalEqualizerSlider(
    band: IEqualizerRepository.EqualizerBand,
    isEnabled: Boolean,
    onLevelChange: (Float) -> Unit
) {
    val centerFreqHz = band.centerFreq.roundToInt()
    val description = stringResource(R.string.eq_hz_format, centerFreqHz)

    val alpha = if (isEnabled) 1f else Constants.UI.DISABLED_ALPHA

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(Dimens.EqualizerSheet_SliderWidth)
            .alpha(alpha)
    ) {
        Slider(
            value = band.currentLevel,
            onValueChange = onLevelChange,
            valueRange = band.rangeMin..band.rangeMax, // Use stable primitives
            enabled = isEnabled,
            modifier = Modifier
                .semantics { contentDescription = description }
                .graphicsLayer {
                    rotationZ = Constants.UI.VERTICAL_ROTATION_DEGREES
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth,
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(-placeable.width, 0)
                    }
                }
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.eq_db_format, band.currentLevel.roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun getPresetName(preset: IEqualizerRepository.EqPreset): String {
    return when (preset) {
        IEqualizerRepository.EqPreset.CUSTOM -> stringResource(R.string.eq_preset_custom)
        IEqualizerRepository.EqPreset.NORMAL -> stringResource(R.string.eq_preset_normal)
        IEqualizerRepository.EqPreset.BASS -> stringResource(R.string.eq_preset_bass)
        IEqualizerRepository.EqPreset.POP -> stringResource(R.string.eq_preset_pop)
        IEqualizerRepository.EqPreset.CLASSIC -> stringResource(R.string.eq_preset_classic)
        IEqualizerRepository.EqPreset.ROCK -> stringResource(R.string.eq_preset_rock)
        IEqualizerRepository.EqPreset.JAZZ -> stringResource(R.string.eq_preset_jazz)
        IEqualizerRepository.EqPreset.VOCAL -> stringResource(R.string.eq_preset_vocal)
        IEqualizerRepository.EqPreset.FLAT -> stringResource(R.string.eq_preset_flat)
        IEqualizerRepository.EqPreset.CLASSICAL -> stringResource(R.string.eq_preset_classical)
    }
}