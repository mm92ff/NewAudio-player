package com.example.newaudio.feature.settings.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.newaudio.R
import com.example.newaudio.ui.theme.Dimens

@Composable
fun ProgressBarHeightSetting(
    height: Float,
    onHeightChange: (Float) -> Unit,
    title: @Composable () -> Unit
) {
    Column {
        title()
        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))
        SettingsCard {
            Column(modifier = Modifier.padding(Dimens.PaddingMedium)) {
                Slider(
                    value = height,
                    onValueChange = onHeightChange,
                    valueRange = 1f..40f,
                    steps = 38
                )
                val heightAsInt = height.toInt()
                Text(
                    text = pluralStringResource(
                        R.plurals.settings_progress_bar_height_value,
                        heightAsInt,
                        heightAsInt
                    ),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun OneHandedModeSetting(
    isEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.one_handed_mode),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.one_handed_mode_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
