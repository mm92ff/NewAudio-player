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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.example.newaudio.R
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.ui.theme.Dimens

@Composable
fun MusicFolderSetting(
    currentPath: String,
    onSelectFolder: () -> Unit
) {
    FolderSetting(
        title = stringResource(R.string.music_folder),
        currentPath = currentPath,
        emptyText = stringResource(R.string.select_music_folder),
        folderContentDescription = stringResource(R.string.settings_music_folder_icon),
        editContentDescription = stringResource(R.string.settings_edit_music_folder_icon),
        onSelectFolder = onSelectFolder
    )
}

@Composable
fun VideoFolderSetting(
    currentPath: String,
    onSelectFolder: () -> Unit
) {
    FolderSetting(
        title = stringResource(R.string.video_folder),
        currentPath = currentPath,
        emptyText = stringResource(R.string.select_video_folder),
        folderContentDescription = stringResource(R.string.settings_video_folder_icon),
        editContentDescription = stringResource(R.string.settings_edit_video_folder_icon),
        onSelectFolder = onSelectFolder
    )
}

@Composable
private fun FolderSetting(
    title: String,
    currentPath: String,
    emptyText: String,
    folderContentDescription: String,
    editContentDescription: String,
    onSelectFolder: () -> Unit
) {
    Column {
        Text(
            text = title,
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
                    contentDescription = folderContentDescription,
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
                        text = displayName ?: emptyText,
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
                    contentDescription = editContentDescription
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

@Composable
fun ShowVideoPreviewItemsSetting(
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
                    text = stringResource(R.string.settings_video_preview_items_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.settings_video_preview_items_desc),
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

@Composable
fun VideoDisplayModeSetting(
    selectedMode: UserPreferences.VideoDisplayMode,
    onModeSelected: (UserPreferences.VideoDisplayMode) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.video_display_mode_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))
        SettingsCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Dimens.PaddingSmall)) {
                VideoDisplayModeOption(
                    selected = selectedMode == UserPreferences.VideoDisplayMode.LIST,
                    title = stringResource(R.string.video_display_mode_list),
                    onClick = { onModeSelected(UserPreferences.VideoDisplayMode.LIST) }
                )
                VideoDisplayModeOption(
                    selected = selectedMode == UserPreferences.VideoDisplayMode.PREVIEW_LIST,
                    title = stringResource(R.string.video_display_mode_preview_list),
                    onClick = { onModeSelected(UserPreferences.VideoDisplayMode.PREVIEW_LIST) }
                )
                VideoDisplayModeOption(
                    selected = selectedMode == UserPreferences.VideoDisplayMode.GALLERY_SQUARE,
                    title = stringResource(R.string.video_display_mode_gallery_square),
                    onClick = { onModeSelected(UserPreferences.VideoDisplayMode.GALLERY_SQUARE) }
                )
                VideoDisplayModeOption(
                    selected = selectedMode == UserPreferences.VideoDisplayMode.GALLERY_ADAPTIVE,
                    title = stringResource(R.string.video_display_mode_gallery_adaptive),
                    onClick = { onModeSelected(UserPreferences.VideoDisplayMode.GALLERY_ADAPTIVE) }
                )
                VideoDisplayModeOption(
                    selected = selectedMode == UserPreferences.VideoDisplayMode.GALLERY_FILLED,
                    title = stringResource(R.string.video_display_mode_gallery_filled),
                    onClick = { onModeSelected(UserPreferences.VideoDisplayMode.GALLERY_FILLED) }
                )
            }
        }
    }
}

@Composable
fun VideoGalleryColumnsSetting(
    selectedColumns: Int,
    onColumnsSelected: (Int) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.video_gallery_columns_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))
        SettingsCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Dimens.PaddingSmall)) {
                VideoDisplayModeOption(
                    selected = selectedColumns == 2,
                    title = stringResource(R.string.video_gallery_columns_two),
                    onClick = { onColumnsSelected(2) }
                )
                VideoDisplayModeOption(
                    selected = selectedColumns == 3,
                    title = stringResource(R.string.video_gallery_columns_three),
                    onClick = { onColumnsSelected(3) }
                )
                VideoDisplayModeOption(
                    selected = selectedColumns == 4,
                    title = stringResource(R.string.video_gallery_columns_four),
                    onClick = { onColumnsSelected(4) }
                )
            }
        }
    }
}

@Composable
fun ShowVideoNamesInGallerySetting(
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
                    text = stringResource(R.string.show_video_names_in_gallery_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.show_video_names_in_gallery_desc),
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

@Composable
fun VideoMarkersSetting(
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
                    text = stringResource(R.string.video_markers_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.video_markers_desc),
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

@Composable
private fun VideoDisplayModeOption(
    selected: Boolean,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.PaddingSmall, vertical = Dimens.SettingsScreen_RowSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}
