package com.example.newaudio.feature.filebrowser.composables

import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.newaudio.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserAppBar(
    currentPath: String,
    canNavigateBack: Boolean,
    onNavigateUp: () -> Unit,
    onSettingsClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    isEditMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    onToggleEditMode: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    isReversedLayout: Boolean = false
) {
    if (isEditMode) {
        EditModeAppBar(
            selectedCount = selectedCount,
            allSelected = allSelected,
            onCloseEditMode = onToggleEditMode,
            onSelectAll = onSelectAll,
            windowInsets = windowInsets,
            modifier = modifier
        )
    } else {
        NormalAppBar(
            currentPath = currentPath,
            canNavigateBack = canNavigateBack,
            onNavigateUp = onNavigateUp,
            onSettingsClick = onSettingsClick,
            onPlaylistClick = onPlaylistClick,
            onToggleEditMode = onToggleEditMode,
            isReversedLayout = isReversedLayout,
            windowInsets = windowInsets,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalAppBar(
    currentPath: String,
    canNavigateBack: Boolean,
    onNavigateUp: () -> Unit,
    onSettingsClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    onToggleEditMode: () -> Unit,
    isReversedLayout: Boolean,
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier
) {
    val decodedPath = Uri.decode(currentPath)
    val title = decodedPath.split("/").lastOrNull()?.ifBlank { null } ?: stringResource(R.string.browser)

    val actionsButtons: @Composable () -> Unit = {
        // Define the list of buttons
        val buttons = listOf<@Composable () -> Unit>(
            {
                IconButton(onClick = onToggleEditMode) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = stringResource(R.string.edit_mode)
                    )
                }
            },
            {
                IconButton(onClick = onPlaylistClick) {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = stringResource(R.string.playlist_icon_description)
                    )
                }
            },
            {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings)
                    )
                }
            }
        )

        // Reverse order when "isReversedLayout" (one-handed mode) is active.
        // Normal: [Edit] [Playlist] [Settings]
        // One-handed: [Settings] [Playlist] [Edit]
        val displayButtons = if (isReversedLayout) buttons.asReversed() else buttons

        Row {
            displayButtons.forEach { it() }
        }
    }

    val backButton: @Composable () -> Unit = {
        if (canNavigateBack) {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        }
    }

    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (isReversedLayout) actionsButtons() else backButton()
        },
        actions = {
            if (isReversedLayout) backButton() else actionsButtons()
        },
        windowInsets = windowInsets,
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditModeAppBar(
    selectedCount: Int,
    allSelected: Boolean,
    onCloseEditMode: () -> Unit,
    onSelectAll: () -> Unit,
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.selected_count, selectedCount),
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onCloseEditMode) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel)
                )
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = stringResource(R.string.select_all),
                    tint = if (allSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
        },
        windowInsets = windowInsets,
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}