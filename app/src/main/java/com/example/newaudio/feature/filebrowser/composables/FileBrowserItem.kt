package com.example.newaudio.feature.filebrowser.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.newaudio.R
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.MediaBrowserMode
import com.example.newaudio.domain.model.Video
import com.example.newaudio.util.formatDurationMMSS

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileBrowserItem(
    item: FileItem,
    isActive: Boolean,
    isEditMode: Boolean,
    isSelected: Boolean,
    isRepeatingSong: Boolean,
    browserMode: MediaBrowserMode,
    transparentListItems: Boolean,
    showVideoPreview: Boolean,
    videoThumbnailImageLoader: ImageLoader? = null,
    onClick: () -> Unit,
    onFolderIconClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToVideoPlaylist: () -> Unit,
    onToggleRepeatMode: () -> Unit
) {
    if (item is FileItem.VideoFile && showVideoPreview && videoThumbnailImageLoader != null) {
        VideoPreviewListItem(
            item = item,
            isActive = isActive,
            isEditMode = isEditMode,
            isSelected = isSelected,
            transparentListItems = transparentListItems,
            onClick = onClick,
            onLongClick = onLongClick,
            onDelete = onDelete,
            onRename = onRename,
            onCopy = onCopy,
            onMove = onMove,
            onAddToVideoPlaylist = onAddToVideoPlaylist,
            videoThumbnailImageLoader = videoThumbnailImageLoader
        )
        return
    }

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
                is FileItem.VideoFile -> {
                    val durationText = formatDurationMMSS(item.video.duration)
                    Text(
                        text = durationText,
                        maxLines = 1,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is FileItem.Folder -> {
                    item.mediaCount?.let { count ->
                        val countPlural = when (browserMode) {
                            MediaBrowserMode.MUSIC -> R.plurals.folder_audio_count
                            MediaBrowserMode.VIDEO -> R.plurals.folder_video_count
                        }
                        val countText = LocalContext.current.resources.getQuantityString(countPlural, count, count)
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
                    is FileItem.VideoFile -> {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = stringResource(R.string.video_description, item.name),
                            tint = contentColor
                        )
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
                        onAddToPlaylist = onAddToPlaylist,
                        onAddToVideoPlaylist = onAddToVideoPlaylist,
                        showAddToPlaylist = item is FileItem.AudioFile,
                        showAddToVideoPlaylist = item is FileItem.VideoFile
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoPreviewListItem(
    item: FileItem.VideoFile,
    isActive: Boolean,
    isEditMode: Boolean,
    isSelected: Boolean,
    transparentListItems: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onAddToVideoPlaylist: () -> Unit,
    videoThumbnailImageLoader: ImageLoader
) {
    var showMenu by remember { mutableStateOf(false) }
    val contentColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val isPortraitVideo = item.video.isPortrait()
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.10f)
        !transparentListItems -> MaterialTheme.colorScheme.surface
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (isPortraitVideo) 132.dp else 96.dp)
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isEditMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() }
            )
        }

        VideoThumbnail(
            video = item.video,
            contentDescription = stringResource(R.string.video_description, item.name),
            contentColor = contentColor,
            imageLoader = videoThumbnailImageLoader,
            modifier = Modifier.size(
                width = if (item.video.isPortrait()) 72.dp else 120.dp,
                height = if (item.video.isPortrait()) 112.dp else 68.dp
            )
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = formatDurationMMSS(item.video.duration),
                maxLines = 1,
                color = contentColor.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (!isEditMode) {
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
                    onAddToPlaylist = {},
                    onAddToVideoPlaylist = onAddToVideoPlaylist,
                    showAddToPlaylist = false,
                    showAddToVideoPlaylist = true
                )
            }
        }
    }
}

@Composable
internal fun VideoThumbnail(
    video: Video,
    contentDescription: String,
    contentColor: Color,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val model = remember(video.thumbnailUri, video.contentUri, video.path) {
        video.previewModel()
    }
    val cacheKey = remember(video.thumbnailUri, video.contentUri, video.path) {
        video.previewCacheKey()
    }
    val request = remember(context, model, cacheKey) {
        model?.let {
            ImageRequest.Builder(context)
                .data(it)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .crossfade(false)
                .build()
        }
    }
    val shape = MaterialTheme.shapes.small

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (request == null) {
            VideoThumbnailFallback(contentDescription, contentColor)
        } else {
            SubcomposeAsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                imageLoader = imageLoader,
                loading = {
                    VideoThumbnailFallback(contentDescription, contentColor)
                },
                error = {
                    VideoThumbnailFallback(contentDescription, contentColor)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun VideoThumbnailFallback(
    contentDescription: String,
    contentColor: Color
) {
    Icon(
        imageVector = Icons.Default.Movie,
        contentDescription = contentDescription,
        tint = contentColor.copy(alpha = 0.82f),
        modifier = Modifier.size(32.dp)
    )
}

internal fun Video.previewModel(): String? {
    return thumbnailUri?.takeIf { it.isNotBlank() }
        ?: contentUri.takeIf { it.isNotBlank() }
        ?: path.takeIf { it.isNotBlank() }
}

internal fun Video.previewCacheKey(): String? {
    return previewModel()?.let { "video-preview:$it" }
}

internal fun Video.isPortrait(): Boolean {
    return width > 0 && height > 0 && height > width
}

@Composable
private fun FileItemMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToVideoPlaylist: () -> Unit,
    showAddToPlaylist: Boolean,
    showAddToVideoPlaylist: Boolean
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (showAddToPlaylist) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_playlist)) },
                onClick = { onAddToPlaylist(); onDismiss() },
                leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) }
            )
        }
        if (showAddToVideoPlaylist) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_video_playlist)) },
                onClick = { onAddToVideoPlaylist(); onDismiss() },
                leadingIcon = { Icon(Icons.Default.VideoLibrary, null) }
            )
        }
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
