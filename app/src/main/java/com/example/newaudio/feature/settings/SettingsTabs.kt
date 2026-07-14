package com.example.newaudio.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.newaudio.R
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.model.UserPreferences.Theme
import com.example.newaudio.domain.model.UserPreferences.VideoDisplayMode
import com.example.newaudio.feature.settings.composables.BackgroundGradientSetting
import com.example.newaudio.feature.settings.composables.BackgroundTintSetting
import com.example.newaudio.feature.settings.composables.BluetoothAutoplaySetting
import com.example.newaudio.feature.settings.composables.ColorSetting
import com.example.newaudio.feature.settings.composables.DeveloperOptions
import com.example.newaudio.feature.settings.composables.MarqueeSetting
import com.example.newaudio.feature.settings.composables.MusicFolderSetting
import com.example.newaudio.feature.settings.composables.OneHandedModeSetting
import com.example.newaudio.feature.settings.composables.PlayOnFolderClickSetting
import com.example.newaudio.feature.settings.composables.ProgressBarHeightSetting
import com.example.newaudio.feature.settings.composables.ResumeSessionOnModeSwitchSetting
import com.example.newaudio.feature.settings.composables.SettingsCardAppearanceSetting
import com.example.newaudio.feature.settings.composables.SettingsFilledTonalButton
import com.example.newaudio.feature.settings.composables.ShowFolderSongCountSetting
import com.example.newaudio.feature.settings.composables.ShowHiddenFilesSetting
import com.example.newaudio.feature.settings.composables.ShowVideoNamesInGallerySetting
import com.example.newaudio.feature.settings.composables.ThemeSetting
import com.example.newaudio.feature.settings.composables.TransparentListItemsSetting
import com.example.newaudio.feature.settings.composables.VideoDisplayModeSetting
import com.example.newaudio.feature.settings.composables.VideoFolderSetting
import com.example.newaudio.feature.settings.composables.VideoGalleryColumnsSetting
import com.example.newaudio.feature.settings.composables.VideoMarkersSetting
import com.example.newaudio.ui.NewAudioTestTags
import com.example.newaudio.ui.theme.Dimens

data class GeneralSettingsActions(
    val onUseMarqueeChange: (Boolean) -> Unit = {},
    val onOneHandedModeChange: (Boolean) -> Unit = {},
    val onPlayOnFolderClickChange: (Boolean) -> Unit = {},
    val onResumeSessionOnModeSwitchChange: (Boolean) -> Unit = {},
    val onBluetoothAutoplayChange: (Boolean) -> Unit = {}
)

data class MediaSettingsActions(
    val onMusicFolderClick: () -> Unit = {},
    val onVideoFolderClick: () -> Unit = {},
    val onShowHiddenFilesChange: (Boolean) -> Unit = {},
    val onShowFolderSongCountChange: (Boolean) -> Unit = {},
    val onVideoDisplayModeChange: (VideoDisplayMode) -> Unit = {},
    val onVideoGalleryColumnsChange: (Int) -> Unit = {},
    val onShowVideoNamesInGalleryChange: (Boolean) -> Unit = {},
    val onVideoMarkersEnabledChange: (Boolean) -> Unit = {}
)

data class DesignSettingsActions(
    val onThemeChange: (Theme) -> Unit = {},
    val onPrimaryColorChange: (String) -> Unit = {},
    val onBackgroundTintFractionChange: (Float) -> Unit = {},
    val onBackgroundGradientEnabledChange: (Boolean) -> Unit = {},
    val onTransparentListItemsChange: (Boolean) -> Unit = {},
    val onSettingsCardTransparentChange: (Boolean) -> Unit = {},
    val onSettingsCardBorderWidthChange: (Float) -> Unit = {},
    val onSettingsCardBorderColorChange: (String) -> Unit = {},
    val onMiniPlayerProgressBarHeightChange: (Float) -> Unit = {},
    val onFullScreenPlayerProgressBarHeightChange: (Float) -> Unit = {}
)

data class SystemSettingsActions(
    val onExportBackup: () -> Unit = {},
    val onImportBackup: () -> Unit = {},
    val onShowConsole: () -> Unit = {},
    val onKillApp: () -> Unit = {},
    val onResetDatabase: () -> Unit = {}
)

@Composable
fun SettingsTabs(
    settings: UserPreferences,
    generalActions: GeneralSettingsActions,
    mediaActions: MediaSettingsActions,
    designActions: DesignSettingsActions,
    systemActions: SystemSettingsActions,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(SettingsTab.GENERAL.ordinal) }
    val generalListState = rememberLazyListState()
    val mediaListState = rememberLazyListState()
    val designListState = rememberLazyListState()
    val systemListState = rememberLazyListState()
    val selectedTab = SettingsTab.entries[selectedTabIndex]

    Column(modifier = modifier.fillMaxSize()) {
        SettingsTabRow(
            selectedTab = selectedTab,
            onTabSelected = { selectedTabIndex = it.ordinal }
        )
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                SettingsTab.GENERAL -> GeneralSettingsTab(
                    settings = settings,
                    actions = generalActions,
                    listState = generalListState
                )
                SettingsTab.MEDIA -> MediaSettingsTab(
                    settings = settings,
                    actions = mediaActions,
                    listState = mediaListState
                )
                SettingsTab.DESIGN -> DesignSettingsTab(
                    settings = settings,
                    actions = designActions,
                    listState = designListState
                )
                SettingsTab.SYSTEM -> SystemSettingsTab(
                    actions = systemActions,
                    listState = systemListState
                )
            }
        }
    }
}

@Composable
private fun SettingsTabRow(
    selectedTab: SettingsTab,
    onTabSelected: (SettingsTab) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = Modifier.testTag(NewAudioTestTags.SETTINGS_TAB_ROW),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        SettingsTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(tab.tabTestTag),
                text = {
                    Text(
                        text = stringResource(tab.titleRes),
                        maxLines = 1
                    )
                }
            )
        }
    }
}

@Composable
private fun GeneralSettingsTab(
    settings: UserPreferences,
    actions: GeneralSettingsActions,
    listState: LazyListState
) {
    SettingsTabList(SettingsTab.GENERAL, listState) {
        item { MarqueeSetting(settings.useMarquee, actions.onUseMarqueeChange) }
        item { OneHandedModeSetting(settings.oneHandedMode, actions.onOneHandedModeChange) }
        item { PlayOnFolderClickSetting(settings.playOnFolderClick, actions.onPlayOnFolderClickChange) }
        item {
            ResumeSessionOnModeSwitchSetting(
                settings.resumeSessionOnModeSwitch,
                actions.onResumeSessionOnModeSwitchChange
            )
        }
        item { BluetoothAutoplaySetting(settings.isAutoPlayOnBluetooth, actions.onBluetoothAutoplayChange) }
    }
}

@Composable
private fun MediaSettingsTab(
    settings: UserPreferences,
    actions: MediaSettingsActions,
    listState: LazyListState
) {
    SettingsTabList(SettingsTab.MEDIA, listState) {
        item { MusicFolderSetting(settings.musicFolderPath, actions.onMusicFolderClick) }
        item { VideoFolderSetting(settings.videoFolderPath, actions.onVideoFolderClick) }
        item { ShowHiddenFilesSetting(settings.showHiddenFiles, actions.onShowHiddenFilesChange) }
        item { ShowFolderSongCountSetting(settings.showFolderSongCount, actions.onShowFolderSongCountChange) }
        item { VideoDisplayModeSetting(settings.videoDisplayMode, actions.onVideoDisplayModeChange) }
        if (settings.videoDisplayMode.isGalleryMode()) {
            item {
                VideoGalleryColumnsSetting(
                    selectedColumns = settings.videoGalleryColumns,
                    onColumnsSelected = actions.onVideoGalleryColumnsChange
                )
            }
            item {
                ShowVideoNamesInGallerySetting(
                    isEnabled = settings.showVideoNamesInGallery,
                    onCheckedChange = actions.onShowVideoNamesInGalleryChange
                )
            }
        }
        item { VideoMarkersSetting(settings.videoMarkersEnabled, actions.onVideoMarkersEnabledChange) }
    }
}

@Composable
private fun DesignSettingsTab(
    settings: UserPreferences,
    actions: DesignSettingsActions,
    listState: LazyListState
) {
    SettingsTabList(SettingsTab.DESIGN, listState) {
        item { ThemeSetting(settings.theme, actions.onThemeChange) }
        item { ColorSetting(settings.primaryColor, actions.onPrimaryColorChange) }
        item { BackgroundTintSetting(settings.backgroundTintFraction, actions.onBackgroundTintFractionChange) }
        item {
            BackgroundGradientSetting(
                settings.backgroundGradientEnabled,
                actions.onBackgroundGradientEnabledChange
            )
        }
        item { TransparentListItemsSetting(settings.transparentListItems, actions.onTransparentListItemsChange) }
        item {
            SettingsCardAppearanceSetting(
                transparent = settings.settingsCardTransparent,
                onTransparentChange = actions.onSettingsCardTransparentChange,
                borderWidthDp = settings.settingsCardBorderWidth,
                onBorderWidthChange = actions.onSettingsCardBorderWidthChange,
                borderColor = settings.settingsCardBorderColor,
                onBorderColorChange = actions.onSettingsCardBorderColorChange
            )
        }
        item {
            ProgressBarHeightSetting(
                height = settings.miniPlayerProgressBarHeight,
                onHeightChange = actions.onMiniPlayerProgressBarHeightChange
            ) {
                Text(
                    stringResource(R.string.mini_player_progress_bar_height),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        item {
            ProgressBarHeightSetting(
                height = settings.fullScreenPlayerProgressBarHeight,
                onHeightChange = actions.onFullScreenPlayerProgressBarHeightChange
            ) {
                Text(
                    stringResource(R.string.full_screen_player_progress_bar_height),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SystemSettingsTab(
    actions: SystemSettingsActions,
    listState: LazyListState
) {
    SettingsTabList(SettingsTab.SYSTEM, listState) {
        item { BackupSetting(actions.onExportBackup, actions.onImportBackup) }
        item {
            DeveloperOptions(
                onShowConsole = actions.onShowConsole,
                onKillApp = actions.onKillApp,
                onResetDatabase = actions.onResetDatabase
            )
        }
    }
}

@Composable
private fun SettingsTabList(
    tab: SettingsTab,
    listState: LazyListState,
    content: LazyListScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(tab.contentTestTag)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(NewAudioTestTags.SETTINGS_SCROLL),
            state = listState,
            contentPadding = PaddingValues(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.SettingsScreen_SectionSpacing),
            content = content
        )
    }
}

@Composable
private fun BackupSetting(
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.playlists),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.backup_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsFilledTonalButton(
                onClick = onExportBackup,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.export_playlists))
            }
            SettingsFilledTonalButton(
                onClick = onImportBackup,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.import_playlists))
            }
        }
    }
}

private fun VideoDisplayMode.isGalleryMode(): Boolean =
    this == VideoDisplayMode.GALLERY_SQUARE ||
        this == VideoDisplayMode.GALLERY_ADAPTIVE ||
        this == VideoDisplayMode.GALLERY_FILLED
