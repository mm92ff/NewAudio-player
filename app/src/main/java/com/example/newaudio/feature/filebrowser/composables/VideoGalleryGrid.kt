package com.example.newaudio.feature.filebrowser.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.example.newaudio.R
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.ui.theme.Dimens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGalleryGrid(
    items: List<FileItem>,
    gridState: LazyGridState = rememberLazyGridState(),
    activeVideoPath: String?,
    selectedPaths: Set<String>,
    isEditMode: Boolean,
    displayMode: UserPreferences.VideoDisplayMode,
    galleryColumns: Int,
    showVideoNames: Boolean,
    topSpacerHeight: Dp,
    initialScrollKey: String,
    imageLoader: ImageLoader,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onEmptyAreaLongClick: () -> Unit
) {
    LaunchedEffect(initialScrollKey, items.isNotEmpty(), topSpacerHeight) {
        if (items.isNotEmpty() && topSpacerHeight > 0.dp) {
            gridState.scrollToItem(1)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(galleryColumns.coerceIn(2, 4)),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        item(
            key = "gallery_reachability_spacer",
            span = { GridItemSpan(maxLineSpan) }
        ) {
            if (topSpacerHeight > 0.dp) {
                Spacer(
                    modifier = Modifier
                        .height(topSpacerHeight)
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                if (!isEditMode) onEmptyAreaLongClick()
                            }
                        )
                )
            }
        }

        items(
            items = items,
            key = { it.path }
        ) { item ->
            val isActive = remember(item, activeVideoPath) {
                when (item) {
                    is FileItem.VideoFile -> item.video.path == activeVideoPath
                    is FileItem.Folder -> activeVideoPath?.startsWith(item.path) == true
                    else -> false
                }
            }
            val isSelected = remember(selectedPaths, item.path) {
                selectedPaths.contains(item.path)
            }

            VideoGalleryTile(
                item = item,
                isActive = isActive,
                isSelected = isSelected,
                isEditMode = isEditMode,
                displayMode = displayMode,
                galleryColumns = galleryColumns.coerceIn(2, 4),
                showVideoNames = showVideoNames,
                imageLoader = imageLoader,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) }
            )
        }

        item(key = "gallery_bottom_spacer") {
            Spacer(modifier = Modifier.size(Dimens.PaddingMedium))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoGalleryTile(
    item: FileItem,
    isActive: Boolean,
    isSelected: Boolean,
    isEditMode: Boolean,
    displayMode: UserPreferences.VideoDisplayMode,
    galleryColumns: Int,
    showVideoNames: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.small
    val isFilledVideoTile = item is FileItem.VideoFile &&
        displayMode == UserPreferences.VideoDisplayMode.GALLERY_FILLED
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.secondary
        isActive -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, borderColor, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(if (isFilledVideoTile) Modifier else Modifier.padding(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        when (item) {
            is FileItem.VideoFile -> {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val thumbnailModifier = when {
                        displayMode == UserPreferences.VideoDisplayMode.GALLERY_FILLED -> Modifier.fillMaxSize()
                        displayMode == UserPreferences.VideoDisplayMode.GALLERY_SQUARE -> Modifier.fillMaxSize()
                        item.video.isPortrait() -> Modifier
                            .width(maxWidth * 0.62f)
                            .height(maxHeight * 0.95f)
                        else -> Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    }

                    VideoThumbnail(
                        video = item.video,
                        contentDescription = stringResource(R.string.video_description, item.name),
                        contentColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        imageLoader = imageLoader,
                        modifier = thumbnailModifier,
                        contentScale = if (displayMode == UserPreferences.VideoDisplayMode.GALLERY_FILLED) {
                            ContentScale.Crop
                        } else {
                            ContentScale.Fit
                        }
                    )
                }
            }
            is FileItem.Folder -> {
                val contentColor = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = stringResource(R.string.folder_description, item.name),
                        tint = contentColor,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = item.name,
                        color = contentColor,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (galleryColumns == 2) {
                        item.mediaCount?.let { count ->
                            val countText = LocalContext.current.resources.getQuantityString(
                                R.plurals.folder_video_count,
                                count,
                                count
                            )
                            Text(
                                text = countText,
                                color = contentColor.copy(alpha = 0.78f),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            else -> Unit
        }

        if (item is FileItem.VideoFile && showVideoNames) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.64f))
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (isEditMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}
