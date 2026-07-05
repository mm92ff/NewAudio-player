package com.example.newaudio.feature.filebrowser.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.MediaBrowserMode
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.model.UserPreferences.VideoDisplayMode
import com.example.newaudio.feature.filebrowser.FileBrowserUiState
import com.example.newaudio.ui.theme.Dimens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileBrowserList(
    uiState: FileBrowserUiState,
    listState: LazyListState,
    activeSongPath: String?,
    activeVideoPath: String?,
    onItemClick: (FileItem) -> Unit,
    onFolderIconClick: (FileItem) -> Unit,
    onDeleteClick: (FileItem) -> Unit,
    onRenameClick: (FileItem) -> Unit,
    onCopyClick: (FileItem) -> Unit,
    onMoveClick: (FileItem) -> Unit,
    onAddToPlaylistClick: (FileItem.AudioFile) -> Unit,
    onAddToVideoPlaylistClick: (FileItem.VideoFile) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onEmptyAreaLongClick: () -> Unit,
    onToggleRepeatMode: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val topSpacerHeight = maxHeight - Dimens.FileBrowser_ItemHeight
        val context = LocalContext.current
        val videoThumbnailImageLoader = remember(context) {
            ImageLoader.Builder(context)
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .build()
        }
        val isGalleryMode = uiState.browserMode == MediaBrowserMode.VIDEO &&
            (uiState.videoDisplayMode == VideoDisplayMode.GALLERY_SQUARE ||
                uiState.videoDisplayMode == VideoDisplayMode.GALLERY_ADAPTIVE ||
                uiState.videoDisplayMode == VideoDisplayMode.GALLERY_FILLED)

        if (isGalleryMode) {
            VideoGalleryGrid(
                items = uiState.fileItems,
                activeVideoPath = activeVideoPath,
                selectedPaths = uiState.selectedPaths,
                isEditMode = uiState.isEditMode,
                displayMode = uiState.videoDisplayMode,
                galleryColumns = uiState.videoGalleryColumns,
                showVideoNames = uiState.showVideoNamesInGallery,
                topSpacerHeight = topSpacerHeight,
                initialScrollKey = "${uiState.currentPath}|${uiState.videoDisplayMode}|${uiState.videoGalleryColumns}",
                imageLoader = videoThumbnailImageLoader,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                onEmptyAreaLongClick = onEmptyAreaLongClick
            )
            return@BoxWithConstraints
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            item(key = "reachability_spacer") {
                if (topSpacerHeight > 0.dp) {
                    Spacer(
                        modifier = Modifier
                            .height(topSpacerHeight)
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    if (!uiState.isEditMode) onEmptyAreaLongClick()
                                }
                            )
                    )
                }
            }

            items(
                items = uiState.fileItems,
                key = { it.path }
            ) { item ->
                val isActive = remember(item, activeSongPath, activeVideoPath) {
                    when (item) {
                        is FileItem.Folder -> {
                            activeSongPath?.startsWith(item.path) == true ||
                                activeVideoPath?.startsWith(item.path) == true
                        }
                        is FileItem.AudioFile -> item.song.path == activeSongPath
                        is FileItem.VideoFile -> item.video.path == activeVideoPath
                        else -> false
                    }
                }

                val isSelected = remember(uiState.selectedPaths, item.path) {
                    uiState.selectedPaths.contains(item.path)
                }

                val isRepeatingSong = remember(isActive, uiState.repeatMode) {
                    isActive && uiState.repeatMode == UserPreferences.RepeatMode.ONE
                }

                FileBrowserItem(
                    item = item,
                    isActive = isActive,
                    isEditMode = uiState.isEditMode,
                    isSelected = isSelected,
                    isRepeatingSong = isRepeatingSong,
                    browserMode = uiState.browserMode,
                    transparentListItems = uiState.transparentListItems,
                    showVideoPreview = uiState.browserMode == MediaBrowserMode.VIDEO &&
                        uiState.videoDisplayMode == VideoDisplayMode.PREVIEW_LIST,
                    videoThumbnailImageLoader = videoThumbnailImageLoader,
                    onClick = { onItemClick(item) },
                    onFolderIconClick = { onFolderIconClick(item) },
                    onLongClick = { onItemLongClick(item) },
                    onDelete = { onDeleteClick(item) },
                    onRename = { onRenameClick(item) },
                    onCopy = { onCopyClick(item) },
                    onMove = { onMoveClick(item) },
                    onAddToPlaylist = { if (item is FileItem.AudioFile) onAddToPlaylistClick(item) },
                    onAddToVideoPlaylist = { if (item is FileItem.VideoFile) onAddToVideoPlaylistClick(item) },
                    onToggleRepeatMode = onToggleRepeatMode
                )
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
            }
        }
    }
}
