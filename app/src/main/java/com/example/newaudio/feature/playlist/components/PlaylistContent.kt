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
import com.example.newaudio.R
import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.Song
import com.example.newaudio.feature.playlist.PlaylistUiState
import com.example.newaudio.feature.playlist.SelectedPlaylistSong

@Composable
fun PlaylistContent(
    uiState: PlaylistUiState,
    activeSongPath: String?,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    // Playlist Callbacks
    onPlaylistClick: (Long) -> Unit,
    onPlaylistLongClick: (Playlist) -> Unit,
    onRenameClick: (Playlist) -> Unit,
    onDeleteClick: (Playlist) -> Unit,
    onDuplicateClick: (Playlist) -> Unit,
    onPlayPlaylistClick: (Playlist) -> Unit,
    // Song Callbacks
    onSongClick: (Song, Long) -> Unit,
    onSongLongClick: (Long, Song) -> Unit
) {
    if (uiState.playlists.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.playlist_empty))
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState
        ) {
            uiState.playlists.forEach { playlist ->
                item(key = "playlist_${playlist.id}") {
                    val isExpanded = uiState.expandedIds.contains(playlist.id)
                    val isSelected = uiState.selectedPlaylistIds.contains(playlist.id)

                    PlaylistItem(
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
                    val songs = uiState.playlistSongs[playlist.id] ?: emptyList()
                    items(
                        items = songs,
                        key = { "playlist_${playlist.id}_song_${it.path}" }
                    ) { song ->
                        val isActive = song.path == activeSongPath
                        val isSelected = remember(uiState.selectedSongs, song.path) {
                            uiState.selectedSongs.contains(SelectedPlaylistSong(playlist.id, song.path))
                        }

                        PlaylistSongItem(
                            song = song,
                            isActive = isActive,
                            isEditMode = uiState.isEditMode,
                            isSelected = isSelected,
                            transparentListItems = uiState.transparentListItems,
                            onClick = { onSongClick(song, playlist.id) },
                            onLongClick = { onSongLongClick(playlist.id, song) }
                        )
                    }
                }
            }
        }
    }
}
