package com.example.newaudio.feature.playlist.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.example.newaudio.R
import com.example.newaudio.domain.model.Video
import com.example.newaudio.feature.filebrowser.composables.VideoThumbnail
import com.example.newaudio.util.formatDurationMMSS

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoPlaylistVideoItem(
    video: Video,
    isActive: Boolean,
    isEditMode: Boolean,
    isSelected: Boolean,
    transparentListItems: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val contentColor = if (isActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.10f)
            transparentListItems -> Color.Transparent
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        label = "video_playlist_video_item_bg"
    )

    ListItem(
        colors = if (transparentListItems) ListItemDefaults.colors(containerColor = Color.Transparent) else ListItemDefaults.colors(),
        modifier = Modifier
            .padding(start = 32.dp)
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        headlineContent = {
            Text(
                text = video.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
        },
        supportingContent = {
            Text(
                text = formatDurationMMSS(video.duration),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.7f)
            )
        },
        leadingContent = {
            if (isEditMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            } else {
                VideoThumbnail(
                    video = video,
                    contentDescription = stringResource(R.string.video_description, video.title),
                    contentColor = contentColor,
                    imageLoader = imageLoader,
                    modifier = Modifier.size(width = 72.dp, height = 44.dp)
                )
            }
        }
    )
}
