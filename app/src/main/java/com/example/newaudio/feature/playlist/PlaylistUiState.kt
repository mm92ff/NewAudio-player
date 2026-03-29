package com.example.newaudio.feature.playlist

import androidx.compose.runtime.Stable
import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.Song
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

@Stable
data class PlaylistUiState(
    val playlists: ImmutableList<Playlist> = persistentListOf(),
    val expandedIds: PersistentSet<Long> = persistentSetOf(),
    val playlistSongs: ImmutableMap<Long, ImmutableList<Song>> = persistentMapOf(),
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val selectedSongs: PersistentSet<SelectedPlaylistSong> = persistentSetOf(),
    val selectedPlaylistIds: PersistentSet<Long> = persistentSetOf(),
    val transparentListItems: Boolean = false
)

data class SelectedPlaylistSong(
    val playlistId: Long,
    val songPath: String
)
