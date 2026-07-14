package com.example.newaudio.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.example.newaudio.R
import com.example.newaudio.feature.settings.composables.*
import com.example.newaudio.feature.settings.composables.LocalSettingsCardStyle
import com.example.newaudio.feature.settings.composables.SettingsCardStyle
import com.example.newaudio.ui.NewAudioTestTags

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

    // Note: Removed rememberCoroutineScope as I/O operations moved to ViewModel

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

    val videoFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.onVideoFolderChange(it.toString())
        }
    }

    // --- Export Launcher ---
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { destinationUri ->
            // Delegate I/O operations to ViewModel instead of handling in composable
            viewModel.onExportPlaylistsToUri(destinationUri, context)
        }
    }

    // --- Import Launcher ---
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Delegate I/O operations to ViewModel instead of handling in composable
            viewModel.onImportPlaylistsFromUri(it, context)
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onAutoPlayOnBluetoothChange(true)
        } else {
            viewModel.onBluetoothPermissionDenied()
        }
    }

    val onBluetoothAutoplayChange: (Boolean) -> Unit = { enabled ->
        val needsPermission = enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            viewModel.onAutoPlayOnBluetoothChange(enabled)
        }
    }

    Scaffold(
        modifier = Modifier.testTag(NewAudioTestTags.SETTINGS),
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
            SettingsTabs(
                settings = settings,
                generalActions = GeneralSettingsActions(
                    onUseMarqueeChange = viewModel::onUseMarqueeChange,
                    onOneHandedModeChange = viewModel::onOneHandedModeChange,
                    onPlayOnFolderClickChange = viewModel::onPlayOnFolderClickChange,
                    onResumeSessionOnModeSwitchChange = viewModel::onResumeSessionOnModeSwitchChange,
                    onBluetoothAutoplayChange = onBluetoothAutoplayChange
                ),
                mediaActions = MediaSettingsActions(
                    onMusicFolderClick = { folderPickerLauncher.launch(null) },
                    onVideoFolderClick = { videoFolderPickerLauncher.launch(null) },
                    onShowHiddenFilesChange = viewModel::onShowHiddenFilesChange,
                    onShowFolderSongCountChange = viewModel::onShowFolderSongCountChange,
                    onVideoDisplayModeChange = viewModel::onVideoDisplayModeChange,
                    onVideoGalleryColumnsChange = viewModel::onVideoGalleryColumnsChange,
                    onShowVideoNamesInGalleryChange = viewModel::onShowVideoNamesInGalleryChange,
                    onVideoMarkersEnabledChange = viewModel::onVideoMarkersEnabledChange
                ),
                designActions = DesignSettingsActions(
                    onThemeChange = viewModel::onThemeChange,
                    onPrimaryColorChange = viewModel::onPrimaryColorChange,
                    onBackgroundTintFractionChange = viewModel::onBackgroundTintFractionChange,
                    onBackgroundGradientEnabledChange = viewModel::onBackgroundGradientEnabledChange,
                    onTransparentListItemsChange = viewModel::onTransparentListItemsChange,
                    onSettingsCardTransparentChange = viewModel::onSettingsCardTransparentChange,
                    onSettingsCardBorderWidthChange = viewModel::onSettingsCardBorderWidthChange,
                    onSettingsCardBorderColorChange = viewModel::onSettingsCardBorderColorChange,
                    onMiniPlayerProgressBarHeightChange = viewModel::onMiniPlayerProgressBarHeightChange,
                    onFullScreenPlayerProgressBarHeightChange = viewModel::onFullScreenPlayerProgressBarHeightChange
                ),
                systemActions = SystemSettingsActions(
                    onExportBackup = { exportLauncher.launch("newaudio_playlists.json") },
                    onImportBackup = { importLauncher.launch("application/json") },
                    onShowConsole = onShowConsole,
                    onKillApp = { killNewAudioApp(context) },
                    onResetDatabase = viewModel::onResetDatabaseClicked
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
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
