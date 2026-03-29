package com.example.newaudio.feature.filebrowser.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.newaudio.R
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.util.formatDurationMMSS

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileBrowserItem(
    item: FileItem,
    isActive: Boolean,
    isEditMode: Boolean,
    isSelected: Boolean,
    isRepeatingSong: Boolean,
    transparentListItems: Boolean,
    onClick: () -> Unit,
    onFolderIconClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleRepeatMode: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val contentColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.10f)
        else -> Color.Transparent
    }

    ListItem(
        colors = if (transparentListItems) ListItemDefaults.colors(containerColor = Color.Transparent) else ListItemDefaults.colors(),
        modifier = Modifier
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        headlineContent = {
            Text(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor
            )
        },
        supportingContent = {
            when (item) {
                is FileItem.AudioFile -> {
                    val durationText = formatDurationMMSS(item.song.duration)
                    Text(
                        text = stringResource(R.string.song_meta_format, item.song.artist, durationText),
                        maxLines = 1,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is FileItem.Folder -> {
                    item.songCount?.let { count ->
                        val countText = LocalContext.current.resources.getQuantityString(R.plurals.folder_song_count, count, count)
                        Text(
                            text = countText,
                            maxLines = 1,
                            color = contentColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is FileItem.OtherFile -> { /* Do nothing */ }
            }
        },
        leadingContent = {
            if (isEditMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            } else {
                when (item) {
                    is FileItem.AudioFile -> {
                        val rotation by if (isRepeatingSong) {
                            val infiniteTransition = rememberInfiniteTransition(label = "repeat_rotation")
                            infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ), label = "rotation"
                            )
                        } else {
                            remember { mutableStateOf(0f) }
                        }

                        IconButton(onClick = onToggleRepeatMode, enabled = isActive) {
                            Icon(
                                imageVector = if (isRepeatingSong) Icons.Default.Sync else Icons.Default.MusicNote,
                                contentDescription = stringResource(R.string.file_browser_toggle_repeat),
                                tint = contentColor,
                                modifier = Modifier.rotate(rotation)
                            )
                        }
                    }
                    is FileItem.Folder -> {
                        IconButton(onClick = onFolderIconClick) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = stringResource(R.string.folder_description, item.name),
                                tint = contentColor
                            )
                        }
                    }
                    is FileItem.OtherFile -> {
                        Icon(
                            imageVector = Icons.Default.FilePresent,
                            contentDescription = stringResource(R.string.file_description, item.name),
                            tint = contentColor
                        )
                    }
                }
            }
        },
        trailingContent = {
            if (!isEditMode && item !is FileItem.Folder) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.more_options), tint = contentColor)
                    }
                    FileItemMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onRename = onRename,
                        onDelete = onDelete,
                        onCopy = onCopy,
                        onMove = onMove,
                        onAddToPlaylist = onAddToPlaylist
                    )
                }
            }
        }
    )
}

@Composable
private fun FileItemMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.add_to_playlist)) },
            onClick = { onAddToPlaylist(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rename)) },
            onClick = { onRename(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.Edit, null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy)) },
            onClick = { onCopy(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.move)) },
            onClick = { onMove(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.DriveFileMove, null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete)) },
            onClick = { onDelete(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.Delete, null) }
        )
    }
}
