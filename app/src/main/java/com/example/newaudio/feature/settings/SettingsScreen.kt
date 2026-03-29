package com.example.newaudio.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.newaudio.R
import com.example.newaudio.feature.settings.composables.*
import com.example.newaudio.feature.settings.composables.LocalSettingsCardStyle
import com.example.newaudio.feature.settings.composables.SettingsCardStyle
import com.example.newaudio.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onShowConsole: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val showResetDialog by viewModel.showResetDialog.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // IMPORTANT: Coroutine scope for UI operations
    val scope = rememberCoroutineScope()

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissResetDialog,
            title = { Text(stringResource(R.string.reset_database_title)) },
            text = { Text(stringResource(R.string.reset_database_confirmation)) },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmResetDatabase) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissResetDialog) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text.asString(context))
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.onMusicFolderChange(it.toString())
        }
    }

    // --- Export Launcher (FIXED & ROBUST) ---
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { destinationUri ->
            scope.launch {
                // 1. Path to temporary file
                val tempFile = File(context.cacheDir, "export_temp.json")

                // 2. Call ViewModel and WAIT (suspend)
                // The ViewModel now writes to 'file://path/to/temp'
                val exportSuccess = viewModel.exportPlaylistsSuspend(tempFile.absolutePath)

                if (exportSuccess) {
                    // 3. If export succeeded, copy the content into the file chosen by the user
                    val copySuccess = withContext(Dispatchers.IO) {
                        try {
                            // "wt" = Write & Truncate (important when overwriting!)
                            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { output ->
                                tempFile.inputStream().use { input ->
                                    input.copyTo(output)
                                }
                            }
                            true
                        } catch (e: Exception) {
                            e.printStackTrace()
                            false
                        } finally {
                            // Clean up
                            if (tempFile.exists()) {
                                tempFile.delete()
                            }
                        }
                    }

                    // If copying fails (e.g. due to permissions), inform the user
                    if (!copySuccess) {
                        viewModel.onCopyFailed()
                    }
                }
                // If exportSuccess == false, the ViewModel has already sent an error event.
            }
        }
    }

    // --- Import Launcher ---
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val tempFile = File(context.cacheDir, "import_temp.json")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    // Import via ViewModel (with file:// prefix adjustment in VM)
                    withContext(Dispatchers.Main) {
                        viewModel.onImportPlaylists(tempFile.absolutePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val cardStyle = remember(
            settings.settingsCardTransparent,
            settings.settingsCardBorderWidth,
            settings.settingsCardBorderColor
        ) {
            SettingsCardStyle(
                transparent = settings.settingsCardTransparent,
                borderWidthDp = settings.settingsCardBorderWidth,
                borderColor = settings.settingsCardBorderColor
            )
        }
        CompositionLocalProvider(LocalSettingsCardStyle provides cardStyle) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.SettingsScreen_SectionSpacing)
        ) {
            item { KillAppSetting(context = context) }

            item {
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsFilledTonalButton(
                        onClick = { exportLauncher.launch("newaudio_playlists.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileUpload, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.export_playlists))
                    }
                    SettingsFilledTonalButton(
                        onClick = { importLauncher.launch("application/json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.import_playlists))
                    }
                }
            }

            item { MarqueeSetting(settings.useMarquee, viewModel::onUseMarqueeChange) }
            item { OneHandedModeSetting(settings.oneHandedMode, viewModel::onOneHandedModeChange) }
            item { ShowHiddenFilesSetting(settings.showHiddenFiles, viewModel::onShowHiddenFilesChange) }
            item { PlayOnFolderClickSetting(settings.playOnFolderClick, viewModel::onPlayOnFolderClickChange) }
            item { ShowFolderSongCountSetting(settings.showFolderSongCount, viewModel::onShowFolderSongCountChange) }
            item { ThemeSetting(settings.theme, viewModel::onThemeChange) }
            item { ColorSetting(settings.primaryColor, viewModel::onPrimaryColorChange) }
            item { BackgroundTintSetting(settings.backgroundTintFraction, viewModel::onBackgroundTintFractionChange) }
            item { BackgroundGradientSetting(settings.backgroundGradientEnabled, viewModel::onBackgroundGradientEnabledChange) }
            item { TransparentListItemsSetting(settings.transparentListItems, viewModel::onTransparentListItemsChange) }
            item {
                SettingsCardAppearanceSetting(
                    transparent = settings.settingsCardTransparent,
                    onTransparentChange = viewModel::onSettingsCardTransparentChange,
                    borderWidthDp = settings.settingsCardBorderWidth,
                    onBorderWidthChange = viewModel::onSettingsCardBorderWidthChange,
                    borderColor = settings.settingsCardBorderColor,
                    onBorderColorChange = viewModel::onSettingsCardBorderColorChange
                )
            }
            item { MusicFolderSetting(settings.musicFolderPath) { folderPickerLauncher.launch(null) } }
            item { BluetoothAutoplaySetting(settings.isAutoPlayOnBluetooth, viewModel::onAutoPlayOnBluetoothChange) }

            item {
                ProgressBarHeightSetting(
                    height = settings.miniPlayerProgressBarHeight,
                    onHeightChange = viewModel::onMiniPlayerProgressBarHeightChange
                ) {
                    Text(stringResource(R.string.mini_player_progress_bar_height), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            item {
                ProgressBarHeightSetting(
                    height = settings.fullScreenPlayerProgressBarHeight,
                    onHeightChange = viewModel::onFullScreenPlayerProgressBarHeightChange
                ) {
                    Text(stringResource(R.string.full_screen_player_progress_bar_height), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            item { DeveloperOptions(onShowConsole, viewModel::onResetDatabaseClicked) }
        }
        } // CompositionLocalProvider
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}