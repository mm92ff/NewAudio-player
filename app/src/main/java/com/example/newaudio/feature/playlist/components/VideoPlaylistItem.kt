package com.example.newaudio.feature.playlist.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.newaudio.R
import com.example.newaudio.domain.model.VideoPlaylist

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoPlaylistItem(
    playlist: VideoPlaylist,
    isEditMode: Boolean,
    isSelected: Boolean,
    isExpanded: Boolean,
    transparentListItems: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDuplicateClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.10f) else Color.Transparent,
        label = "video_playlist_item_bg"
    )

    ListItem(
        colors = if (transparentListItems) ListItemDefaults.colors(containerColor = Color.Transparent) else ListItemDefaults.colors(),
        modifier = Modifier
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        headlineContent = { Text(playlist.name) },
        leadingContent = {
            if (isEditMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            } else {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = if (isExpanded) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
        },
        trailingContent = {
            if (!isEditMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlayClick) {
                        Icon(Icons.Default.PlayArrow, stringResource(R.string.play_pause))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename)) },
                                onClick = {
                                    showMenu = false
                                    onRenameClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.duplicate)) },
                                onClick = {
                                    showMenu = false
                                    onDuplicateClick()
                                },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null) }
                            )
                        }
                    }
                }
            }
        }
    )
}
