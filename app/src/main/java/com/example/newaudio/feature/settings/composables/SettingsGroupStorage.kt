package com.example.newaudio.feature.settings.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.example.newaudio.R
import com.example.newaudio.ui.theme.Dimens

@Composable
fun MusicFolderSetting(
    currentPath: String,
    onSelectFolder: () -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.music_folder),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))

        SettingsCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectFolder() }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Dimens.PaddingMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SettingsScreen_RowSpacing)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = stringResource(R.string.settings_music_folder_icon),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    val displayName = remember(currentPath) {
                        if (currentPath.isNotEmpty()) {
                            currentPath.toUri().lastPathSegment ?: currentPath
                        } else {
                            null
                        }
                    }
                    Text(
                        text = displayName ?: stringResource(R.string.select_music_folder),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (displayName != null) {
                        Text(
                            text = currentPath,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.settings_edit_music_folder_icon)
                )
            }
        }
    }
}

@Composable
fun ShowHiddenFilesSetting(
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
                    text = stringResource(R.string.settings_show_hidden_files_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.settings_show_hidden_files_desc),
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
