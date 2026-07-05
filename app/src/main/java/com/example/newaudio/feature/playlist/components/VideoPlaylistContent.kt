package com.example.newaudio.feature.playlist.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import coil.ImageLoader
import com.example.newaudio.R
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.model.VideoPlaylist
import com.example.newaudio.feature.playlist.SelectedPlaylistVideo
import com.example.newaudio.feature.playlist.VideoPlaylistUiState

@Composable
fun VideoPlaylistContent(
    uiState: VideoPlaylistUiState,
    activeVideoPath: String?,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onPlaylistClick: (Long) -> Unit,
    onPlaylistLongClick: (VideoPlaylist) -> Unit,
    onRenameClick: (VideoPlaylist) -> Unit,
    onDeleteClick: (VideoPlaylist) -> Unit,
    onDuplicateClick: (VideoPlaylist) -> Unit,
    onPlayPlaylistClick: (VideoPlaylist) -> Unit,
    onVideoClick: (Video, Long) -> Unit,
    onVideoLongClick: (Long, Video) -> Unit
) {
    if (uiState.playlists.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.video_playlist_empty))
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState
        ) {
            uiState.playlists.forEach { playlist ->
                item(key = "video_playlist_${playlist.id}") {
                    val isExpanded = uiState.expandedIds.contains(playlist.id)
                    val isSelected = uiState.selectedPlaylistIds.contains(playlist.id)

                    VideoPlaylistItem(
                        playlist = playlist,
                        isEditMode = uiState.isEditMode,
                        isSelected = isSelected,
                        isExpanded = isExpanded,
                        transparentListItems = uiState.transparentListItems,
                        onClick = { onPlaylistClick(playlist.id) },
                        onLongClick = { onPlaylistLongClick(playlist) },
                        onRenameClick = { onRenameClick(playlist) },
                        onDeleteClick = { onDeleteClick(playlist) },
                        onDuplicateClick = { onDuplicateClick(playlist) },
                        onPlayClick = { onPlayPlaylistClick(playlist) }
                    )
                }

                if (uiState.expandedIds.contains(playlist.id)) {
                    val videos = uiState.playlistVideos[playlist.id] ?: emptyList()
                    items(
                        items = videos,
                        key = { "video_playlist_${playlist.id}_video_${it.path}" }
                    ) { video ->
                        val isActive = video.path == activeVideoPath
                        val isSelected = remember(uiState.selectedVideos, video.path) {
                            uiState.selectedVideos.contains(SelectedPlaylistVideo(playlist.id, video.path))
                        }

                        VideoPlaylistVideoItem(
                            video = video,
                            isActive = isActive,
                            isEditMode = uiState.isEditMode,
                            isSelected = isSelected,
                            transparentListItems = uiState.transparentListItems,
                            imageLoader = imageLoader,
                            onClick = { onVideoClick(video, playlist.id) },
                            onLongClick = { onVideoLongClick(playlist.id, video) }
                        )
                    }
                }
            }
        }
    }
}
