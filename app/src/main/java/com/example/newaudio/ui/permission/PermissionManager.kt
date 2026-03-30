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

    var isFolderSetupSkipped by remember { mutableStateOf(false) }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var isGranted by remember {
        mutableStateOf(checkPermission(context, audioPermission))
    }

    var showRationaleScreen by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[audioPermission] == true || checkPermission(context, audioPermission)
        isGranted = granted

        if (!granted) {
            showRationaleScreen = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isGranted = checkPermission(context, audioPermission)
                if (isGranted) {
                    showRationaleScreen = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isGranted) {
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
                if (state.isMusicFolderSet || isFolderSetupSkipped) {
                    onPermissionGranted()
                } else {
                    MusicFolderSetupScreen(
                        onFolderSelected = { path ->
                            viewModel.onMusicFolderSelected(path)
                        },
                        onSkip = {
                            isFolderSetupSkipped = true
                        }
                    )
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
                        text = "Error",
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
                            isFolderSetupSkipped = true
                        },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text("Skip Setup")
                    }
                }
            }
        }
    } else {
        LaunchedEffect(Unit) {
            if (!showRationaleScreen) {
                val permsToRequest = mutableListOf(audioPermission)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                launcher.launch(permsToRequest.toTypedArray())
            }
        }

        if (showRationaleScreen) {
            PermissionRationaleScreen(
                onGrantClick = {
                    val permsToRequest = mutableListOf(audioPermission)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    launcher.launch(permsToRequest.toTypedArray())
                },
                onSettingsClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                showSettingsLink = !ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, audioPermission)
            )
        }
    }
}

private fun checkPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun PermissionRationaleScreen(
    onGrantClick: () -> Unit,
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

        if (showSettingsLink) {
            Button(
                onClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth(Dimens.BUTTON_WIDTH_FACTOR)
            ) {
                Text(stringResource(id = R.string.permission_open_settings))
            }
        } else {
            Button(
                onClick = onGrantClick,
                modifier = Modifier.fillMaxWidth(Dimens.BUTTON_WIDTH_FACTOR)
            ) {
                Text(stringResource(R.string.grant_permissions))
            }
        }
    }
}
