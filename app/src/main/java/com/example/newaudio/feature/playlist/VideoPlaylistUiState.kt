package com.example.newaudio.feature.playlist

import androidx.compose.runtime.Stable
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.model.VideoPlaylist
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

@Stable
data class VideoPlaylistUiState(
    val playlists: ImmutableList<VideoPlaylist> = persistentListOf(),
    val expandedIds: PersistentSet<Long> = persistentSetOf(),
    val playlistVideos: ImmutableMap<Long, ImmutableList<Video>> = persistentMapOf(),
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val selectedVideos: PersistentSet<SelectedPlaylistVideo> = persistentSetOf(),
    val selectedPlaylistIds: PersistentSet<Long> = persistentSetOf(),
    val transparentListItems: Boolean = false
)

data class SelectedPlaylistVideo(
    val playlistId: Long,
    val videoPath: String
)
