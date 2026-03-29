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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.example.newaudio.R
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.ui.theme.Dimens
import com.example.newaudio.util.Constants

@Composable
fun ThemeSetting(
    selectedTheme: UserPreferences.Theme,
    onThemeSelected: (UserPreferences.Theme) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.theme),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))

        SettingsCard {
            Column {
                ThemeOption(
                    selected = selectedTheme == UserPreferences.Theme.LIGHT,
                    onClick = { onThemeSelected(UserPreferences.Theme.LIGHT) }
                ) {
                    Text(stringResource(R.string.theme_light), modifier = Modifier.weight(1f))
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Dimens.PaddingMedium),
                    thickness = Dimens.SettingsScreen_DividerThickness
                )
                ThemeOption(
                    selected = selectedTheme == UserPreferences.Theme.DARK,
                    onClick = { onThemeSelected(UserPreferences.Theme.DARK) }
                ) {
                    Text(stringResource(R.string.theme_dark), modifier = Modifier.weight(1f))
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Dimens.PaddingMedium),
                    thickness = Dimens.SettingsScreen_DividerThickness
                )
                ThemeOption(
                    selected = selectedTheme == UserPreferences.Theme.SYSTEM,
                    onClick = { onThemeSelected(UserPreferences.Theme.SYSTEM) }
                ) {
                    Text(stringResource(R.string.theme_system), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSetting(
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.primary_color),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SettingsScreen_RowSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.SettingsScreen_RowSpacing)
        ) {
            Constants.ThemeColors.extendedColorOptions.forEach { hex ->
                ColorCircle(
                    hexColor = hex,
                    isSelected = selectedColor == hex,
                    onClick = { onColorSelected(hex) }
                )
            }
        }
    }
}

@Composable
fun BackgroundTintSetting(
    tintFraction: Float,
    onTintFractionChange: (Float) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.background_tint_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))
        SettingsCard {
            Column(modifier = Modifier.padding(Dimens.PaddingMedium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.background_tint_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(tintFraction * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Slider(
                    value = tintFraction,
                    onValueChange = onTintFractionChange,
                    valueRange = 0f..0.30f,
                    steps = 29
                )
            }
        }
    }
}

@Composable
fun TransparentListItemsSetting(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.transparent_list_items_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.transparent_list_items_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}

@Composable
fun BackgroundGradientSetting(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.background_gradient_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.background_gradient_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}
