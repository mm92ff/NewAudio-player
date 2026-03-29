package com.example.newaudio.feature.settings.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.newaudio.R
import com.example.newaudio.ui.theme.Dimens
import com.example.newaudio.util.Constants
import kotlin.math.roundToInt

private val borderColorOptions = Constants.ThemeColors.extendedColorOptions

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsCardAppearanceSetting(
    transparent: Boolean,
    onTransparentChange: (Boolean) -> Unit,
    borderWidthDp: Float,
    onBorderWidthChange: (Float) -> Unit,
    borderColor: String,
    onBorderColorChange: (String) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.settings_card_appearance_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))

        // Toggle: transparent background
        SettingsCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.PaddingMedium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_card_transparent_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.settings_card_transparent_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = transparent, onCheckedChange = onTransparentChange)
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))

        // Slider: Rahmendicke
        SettingsCard {
            Column(modifier = Modifier.padding(Dimens.PaddingMedium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_card_border_width_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.settings_card_border_width_value, borderWidthDp.roundToInt()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Slider(
                    value = borderWidthDp,
                    onValueChange = onBorderWidthChange,
                    valueRange = 0f..4f,
                    steps = 3
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))

        // Farbauswahl: Rahmenfarbe
        Text(
            text = stringResource(R.string.settings_card_border_color_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SettingsScreen_RowSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.SettingsScreen_RowSpacing)
        ) {
            borderColorOptions.forEach { hex ->
                ColorCircle(
                    hexColor = hex,
                    isSelected = borderColor == hex,
                    onClick = { onBorderColorChange(hex) }
                )
            }
        }
    }
}
