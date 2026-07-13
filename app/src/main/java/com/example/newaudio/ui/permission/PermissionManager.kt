package com.example.newaudio.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.newaudio.R
import com.example.newaudio.ui.theme.Dimens

@Composable
fun PermissionAndSetupManager(
    onPermissionGranted: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: PermissionViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var isMusicFolderSetupSkipped by remember { mutableStateOf(false) }
    var isVideoFolderSetupSkipped by remember { mutableStateOf(false) }

    val audioPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val videoPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    var mediaAccess by remember {
        mutableStateOf(currentMediaAccess(context))
    }
    var hasSafAccess by remember {
        mutableStateOf(hasPersistedSafReadAccess(context))
    }

    var audioRequested by rememberSaveable { mutableStateOf(false) }
    var videoRequested by rememberSaveable { mutableStateOf(false) }
    var notificationRequested by rememberSaveable { mutableStateOf(false) }

    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        mediaAccess = currentMediaAccess(context)
        hasSafAccess = hasPersistedSafReadAccess(context)
    }
    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        mediaAccess = currentMediaAccess(context)
        hasSafAccess = hasPersistedSafReadAccess(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Optional: denial never blocks playback. */ }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mediaAccess = currentMediaAccess(context)
                hasSafAccess = hasPersistedSafReadAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (mediaAccess.hasAnyAccess || hasSafAccess) {
        // Permission is granted.
        // Wait for settings to be loaded
        when (val state = uiState) {
            is PermissionUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is PermissionUiState.Success -> {
                if (mediaAccess.audio && !state.isMusicFolderSet && !isMusicFolderSetupSkipped) {
                    MusicFolderSetupScreen(
                        onFolderSelected = { path ->
                            viewModel.onMusicFolderSelected(path)
                        },
                        onSkip = {
                            isMusicFolderSetupSkipped = true
                        }
                    )
                } else if (mediaAccess.video && !state.isVideoFolderSet && !isVideoFolderSetupSkipped) {
                    VideoFolderSetupScreen(
                        onFolderSelected = { path ->
                            viewModel.onVideoFolderSelected(path)
                        },
                        onUseDefaultFolder = { path ->
                            viewModel.onVideoFolderSelected(path)
                        },
                        onSkip = {
                            isVideoFolderSetupSkipped = true
                        }
                    )
                } else {
                    onPermissionGranted()
                }
            }
            is PermissionUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Dimens.PaddingMedium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.permission_setup_error),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
                    Text(
                        text = state.message,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
                    Button(
                        onClick = {
                            isMusicFolderSetupSkipped = true
                            isVideoFolderSetupSkipped = true
                        },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text(stringResource(R.string.permission_skip_setup))
                    }
                }
            }
        }
    } else {
        val audioNeedsSettings = audioRequested && audioPermissions.none {
            ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, it)
        }
        val videoNeedsSettings = videoRequested && videoPermissions.none {
            ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, it)
        }
        PermissionRationaleScreen(
            onGrantAudioClick = {
                audioRequested = true
                audioLauncher.launch(audioPermissions)
            },
            onGrantVideoClick = {
                videoRequested = true
                videoLauncher.launch(videoPermissions)
            },
            onSettingsClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            showSettingsLink = audioNeedsSettings || videoNeedsSettings
        )
    }

    LaunchedEffect(mediaAccess.hasAnyAccess, hasSafAccess) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (mediaAccess.hasAnyAccess || hasSafAccess) &&
            !notificationRequested &&
            !checkPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notificationRequested = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private fun checkPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private data class MediaAccess(val audio: Boolean, val video: Boolean) {
    val hasAnyAccess: Boolean get() = audio || video
}

private fun currentMediaAccess(context: Context): MediaAccess {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        val granted = checkPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        return MediaAccess(audio = granted, video = granted)
    }

    val audio = checkPermission(context, Manifest.permission.READ_MEDIA_AUDIO)
    val video = checkPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            checkPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
    return MediaAccess(audio = audio, video = video)
}

private fun hasPersistedSafReadAccess(context: Context): Boolean =
    context.contentResolver.persistedUriPermissions.any { it.isReadPermission }

@Composable
private fun PermissionRationaleScreen(
    onGrantAudioClick: () -> Unit,
    onGrantVideoClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showSettingsLink: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.PaddingMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.permissions_required),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
        Text(
            text = stringResource(R.string.permissions_rationale),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingLarge))

        Button(
            onClick = onGrantAudioClick,
            modifier = Modifier.fillMaxWidth(Dimens.BUTTON_WIDTH_FACTOR)
        ) {
            Text(stringResource(R.string.grant_audio_permission))
        }
        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
        Button(
            onClick = onGrantVideoClick,
            modifier = Modifier.fillMaxWidth(Dimens.BUTTON_WIDTH_FACTOR)
        ) {
            Text(stringResource(R.string.grant_video_permission))
        }
        if (showSettingsLink) {
            Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
            Button(
                onClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth(Dimens.BUTTON_WIDTH_FACTOR)
            ) {
                Text(stringResource(id = R.string.permission_open_settings))
            }
        }
    }
}
