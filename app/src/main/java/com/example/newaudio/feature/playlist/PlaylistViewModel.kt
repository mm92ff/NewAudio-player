package com.example.newaudio.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.repository.IPlaylistRepository
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.newaudio.R
import com.example.newaudio.util.Constants
import com.example.newaudio.util.UiText
import kotlinx.collections.immutable.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Collections
import javax.inject.Inject

sealed class PlaylistEvent {
    data class PlayPlaylist(val songs: List<Song>) : PlaylistEvent()
    data class PlaySongInPlaylist(val song: Song, val allSongs: List<Song>) : PlaylistEvent()
}

sealed interface PlaylistSideEffect {
    data class ShowSnackbar(val message: UiText) : PlaylistSideEffect
}

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: IPlaylistRepository,
    private val getUserSettingsUseCase: GetUserSettingsUseCase
) : ViewModel() {

    private val _internalState = MutableStateFlow(PlaylistUiState())
    private val _expandedIds = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    private var reorderJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val playlistSongsFlow: Flow<ImmutableMap<Long, ImmutableList<Song>>> = _expandedIds
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(persistentMapOf())
            } else {
                val flows = ids.map { id ->
                    playlistRepository.getSongsInPlaylist(id)
                        .map { songs -> id to songs.toImmutableList() }
                }
                combine(flows) { pairs ->
                    val mapBuilder = persistentMapOf<Long, ImmutableList<Song>>().builder()
                    pairs.forEach { (id, songs) -> mapBuilder.put(id, songs) }
                    mapBuilder.build()
                }
            }
        }

    val uiState: StateFlow<PlaylistUiState> = combine(
        playlistRepository.getAllPlaylists().map { it.toImmutableList() },
        _expandedIds,
        playlistSongsFlow,
        _internalState,
        getUserSettingsUseCase().map { it.transparentListItems }
    ) { playlists, expanded, songsMap, internal, transparentListItems ->
        internal.copy(
            playlists = playlists,
            expandedIds = expanded,
            playlistSongs = songsMap,
            transparentListItems = transparentListItems
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.STATE_FLOW_SHARING_TIMEOUT_MS),
        initialValue = PlaylistUiState(isLoading = true)
    )

    private val _events = Channel<PlaylistEvent>()
    val events: Flow<PlaylistEvent> = _events.receiveAsFlow()

    private val _sideEffects = Channel<PlaylistSideEffect>()
    val sideEffects: Flow<PlaylistSideEffect> = _sideEffects.receiveAsFlow()

    // --- Actions ---

    fun onCreatePlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                playlistRepository.createPlaylist(name)
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_created, name)))
            } catch (e: Exception) {
                Timber.e(e, "Error creating playlist")
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.error_with_message, e.message ?: "")))
            }
        }
    }

    fun togglePlaylistExpansion(playlistId: Long) {
        _expandedIds.update { current ->
            if (current.contains(playlistId)) current.remove(playlistId) else current.add(playlistId)
        }
    }

    fun onRenamePlaylist(playlist: Playlist, newName: String) {
        if (newName.isBlank() || newName == playlist.name) return
        viewModelScope.launch {
            try {
                playlistRepository.updatePlaylist(playlist.copy(name = newName))
            } catch (e: Exception) {
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_rename_failed)))
            }
        }
    }

    fun onDeletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            try {
                _expandedIds.update { it.remove(playlist.id) }
                playlistRepository.deletePlaylist(playlist)
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_deleted)))
            } catch (e: Exception) {
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_delete_failed)))
            }
        }
    }

    fun onDuplicatePlaylist(playlist: Playlist) {
        val newName = "${playlist.name}_v2.0"
        viewModelScope.launch {
            try {
                playlistRepository.duplicatePlaylist(playlist, newName)
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_duplicated)))
            } catch (e: Exception) {
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_duplicate_failed)))
            }
        }
    }

    fun onRemoveSongFromPlaylist(playlistId: Long, songPath: String) {
        viewModelScope.launch {
            try {
                playlistRepository.removeSongFromPlaylist(playlistId, songPath)
            } catch (e: Exception) {
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_remove_song_failed)))
            }
        }
    }

    fun onPlayPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val songs = uiState.value.playlistSongs[playlist.id]
                ?: playlistRepository.getSongsInPlaylist(playlist.id).first().toImmutableList()

            if (songs.isNotEmpty()) {
                _events.send(PlaylistEvent.PlayPlaylist(songs))
            } else {
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_is_empty)))
            }
        }
    }

    fun onPlaySongInPlaylist(song: Song, playlistId: Long) {
        viewModelScope.launch {
            val allSongs = uiState.value.playlistSongs[playlistId] ?: persistentListOf()
            _events.send(PlaylistEvent.PlaySongInPlaylist(song, allSongs))
        }
    }

    // --- Unified Edit Mode ---

    fun toggleEditMode() {
        _internalState.update {
            it.copy(
                isEditMode = !it.isEditMode,
                selectedSongs = persistentSetOf(),
                selectedPlaylistIds = persistentSetOf()
            )
        }
    }

    fun togglePlaylistSelection(playlistId: Long) {
        _internalState.update { state ->
            val newSelection = if (state.selectedPlaylistIds.contains(playlistId)) {
                state.selectedPlaylistIds.remove(playlistId)
            } else {
                state.selectedPlaylistIds.add(playlistId)
            }
            state.copy(selectedPlaylistIds = newSelection)
        }
    }

    fun toggleSongSelection(playlistId: Long, songPath: String) {
        _internalState.update { state ->
            val item = SelectedPlaylistSong(playlistId, songPath)
            val currentSelection = state.selectedSongs
            val newSelection = if (currentSelection.contains(item)) {
                currentSelection.remove(item)
            } else {
                currentSelection.add(item)
            }
            state.copy(selectedSongs = newSelection)
        }
    }

    fun removeSelected() {
        val selectedSongs = uiState.value.selectedSongs
        val selectedPlaylists = uiState.value.selectedPlaylistIds

        if (selectedSongs.isEmpty() && selectedPlaylists.isEmpty()) return

        viewModelScope.launch {
            try {
                val songsByPlaylist = selectedSongs.groupBy { it.playlistId }
                songsByPlaylist.forEach { (playlistId, selectedItems) ->
                    val pathsToDelete = selectedItems.map { it.songPath }
                    playlistRepository.removeSongsFromPlaylist(playlistId, pathsToDelete)
                }
                if (selectedPlaylists.isNotEmpty()) {
                    playlistRepository.deletePlaylists(selectedPlaylists.toList())
                }
                _expandedIds.update { current -> current.removeAll(selectedPlaylists) }
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_selected_deleted, selectedSongs.size + selectedPlaylists.size)))
                toggleEditMode()
            } catch (e: Exception) {
                Timber.e(e, "Error in removeSelected")
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_selected_delete_failed)))
            }
        }
    }

    fun moveSelectedUp() {
        val state = uiState.value
        if (state.selectedPlaylistIds.size == 1) {
            val id = state.selectedPlaylistIds.first()
            val playlist = state.playlists.find { it.id == id }
            playlist?.let { onMoveUp(it) }
        } else if (state.selectedSongs.size == 1) {
            val selected = state.selectedSongs.first()
            val songs = state.playlistSongs[selected.playlistId] ?: return
            val song = songs.find { it.path == selected.songPath }
            song?.let { onMoveSongUp(selected.playlistId, it) }
        }
    }

    fun moveSelectedDown() {
        val state = uiState.value
        if (state.selectedPlaylistIds.size == 1) {
            val id = state.selectedPlaylistIds.first()
            val playlist = state.playlists.find { it.id == id }
            playlist?.let { onMoveDown(it) }
        } else if (state.selectedSongs.size == 1) {
            val selected = state.selectedSongs.first()
            val songs = state.playlistSongs[selected.playlistId] ?: return
            val song = songs.find { it.path == selected.songPath }
            song?.let { onMoveSongDown(selected.playlistId, it) }
        }
    }

    fun onItemLongClicked(playlist: Playlist) {
        if (!uiState.value.isEditMode) {
            toggleEditMode()
        }
        togglePlaylistSelection(playlist.id)
    }

    fun onSongLongClicked(playlistId: Long, song: Song) {
        if (!uiState.value.isEditMode) {
            toggleEditMode()
        }
        toggleSongSelection(playlistId, song.path)
    }

    private fun onMoveUp(playlist: Playlist) {
        val currentList = uiState.value.playlists.toMutableList()
        val index = currentList.indexOfFirst { it.id == playlist.id }
        if (index > 0) {
            Collections.swap(currentList, index, index - 1)
            // Fix positions locally for optimistic update
            val p1 = currentList[index].copy(position = index)
            val p2 = currentList[index - 1].copy(position = index - 1)
            currentList[index] = p1
            currentList[index - 1] = p2

            // Save Debounced
            savePlaylistsOrder(currentList)
        }
    }

    private fun onMoveDown(playlist: Playlist) {
        val currentList = uiState.value.playlists.toMutableList()
        val index = currentList.indexOfFirst { it.id == playlist.id }
        if (index != -1 && index < currentList.size - 1) {
            Collections.swap(currentList, index, index + 1)
            val p1 = currentList[index].copy(position = index)
            val p2 = currentList[index + 1].copy(position = index + 1)
            currentList[index] = p1
            currentList[index + 1] = p2

            // Save Debounced
            savePlaylistsOrder(currentList)
        }
    }

    private fun savePlaylistsOrder(playlists: List<Playlist>) {
        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            delay(Constants.REORDER_DEBOUNCE_MS)
            try {
                playlistRepository.updatePlaylistsOrder(playlists)
            } catch (e: Exception) {
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_move_failed)))
            }
        }
    }

    private fun onMoveSongUp(playlistId: Long, song: Song) {
        val currentSongs = uiState.value.playlistSongs[playlistId]?.toMutableList() ?: return
        val index = currentSongs.indexOfFirst { it.path == song.path }
        if (index > 0) {
            Collections.swap(currentSongs, index, index - 1)
            saveSongsOrder(playlistId, currentSongs)
        }
    }

    private fun onMoveSongDown(playlistId: Long, song: Song) {
        val currentSongs = uiState.value.playlistSongs[playlistId]?.toMutableList() ?: return
        val index = currentSongs.indexOfFirst { it.path == song.path }
        if (index != -1 && index < currentSongs.size - 1) {
            Collections.swap(currentSongs, index, index + 1)
            saveSongsOrder(playlistId, currentSongs)
        }
    }

    private fun saveSongsOrder(playlistId: Long, songs: List<Song>) {
        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            delay(Constants.REORDER_DEBOUNCE_MS)
            try {
                playlistRepository.updatePlaylistSongsOrder(playlistId, songs)
            } catch (e: Exception) {
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_move_failed)))
            }
        }
    }
}