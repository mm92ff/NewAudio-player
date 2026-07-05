package com.example.newaudio.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newaudio.R
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.model.VideoPlaylist
import com.example.newaudio.domain.repository.IVideoPlaylistRepository
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.util.Constants
import com.example.newaudio.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Collections
import javax.inject.Inject

sealed class VideoPlaylistEvent {
    data class PlayVideoPlaylist(val videos: List<Video>) : VideoPlaylistEvent()
    data class PlayVideoInPlaylist(val video: Video, val allVideos: List<Video>) : VideoPlaylistEvent()
}

@HiltViewModel
class VideoPlaylistViewModel @Inject constructor(
    private val videoPlaylistRepository: IVideoPlaylistRepository,
    getUserSettingsUseCase: GetUserSettingsUseCase
) : ViewModel() {

    private val _internalState = MutableStateFlow(VideoPlaylistUiState())
    private val _expandedIds = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())
    private var reorderJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val playlistVideosFlow: Flow<ImmutableMap<Long, ImmutableList<Video>>> = _expandedIds
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(persistentMapOf())
            } else {
                val flows = ids.map { id ->
                    videoPlaylistRepository.getVideosInPlaylist(id)
                        .map { videos -> id to videos.toImmutableList() }
                }
                combine(flows) { pairs ->
                    val mapBuilder = persistentMapOf<Long, ImmutableList<Video>>().builder()
                    pairs.forEach { (id, videos) -> mapBuilder.put(id, videos) }
                    mapBuilder.build()
                }
            }
        }

    val uiState = combine(
        videoPlaylistRepository.getAllVideoPlaylists().map { it.toImmutableList() },
        _expandedIds,
        playlistVideosFlow,
        _internalState,
        getUserSettingsUseCase().map { it.transparentListItems }
    ) { playlists, expanded, videosMap, internal, transparentListItems ->
        internal.copy(
            playlists = playlists,
            expandedIds = expanded,
            playlistVideos = videosMap,
            transparentListItems = transparentListItems
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.STATE_FLOW_SHARING_TIMEOUT_MS),
        initialValue = VideoPlaylistUiState(isLoading = true)
    )

    private val _events = Channel<VideoPlaylistEvent>()
    val events = _events.receiveAsFlow()

    private val _sideEffects = Channel<PlaylistSideEffect>()
    val sideEffects = _sideEffects.receiveAsFlow()

    fun onCreatePlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                videoPlaylistRepository.createVideoPlaylist(name)
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.video_playlist_created, name)))
            } catch (e: Exception) {
                Timber.e(e, "Error creating video playlist")
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.error_with_message, e.message ?: "")))
            }
        }
    }

    fun togglePlaylistExpansion(playlistId: Long) {
        _expandedIds.update { current ->
            if (current.contains(playlistId)) current.remove(playlistId) else current.add(playlistId)
        }
    }

    fun onRenamePlaylist(playlist: VideoPlaylist, newName: String) {
        if (newName.isBlank() || newName == playlist.name) return
        viewModelScope.launch {
            try {
                videoPlaylistRepository.updateVideoPlaylist(playlist.copy(name = newName))
            } catch (e: Exception) {
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_rename_failed)))
            }
        }
    }

    fun onDeletePlaylist(playlist: VideoPlaylist) {
        viewModelScope.launch {
            try {
                _expandedIds.update { it.remove(playlist.id) }
                videoPlaylistRepository.deleteVideoPlaylist(playlist)
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_deleted)))
            } catch (e: Exception) {
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_delete_failed)))
            }
        }
    }

    fun onDuplicatePlaylist(playlist: VideoPlaylist) {
        val newName = "${playlist.name}_v2.0"
        viewModelScope.launch {
            try {
                videoPlaylistRepository.duplicateVideoPlaylist(playlist, newName)
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_duplicated)))
            } catch (e: Exception) {
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_duplicate_failed)))
            }
        }
    }

    fun onRemoveVideoFromPlaylist(playlistId: Long, videoPath: String) {
        viewModelScope.launch {
            try {
                videoPlaylistRepository.removeVideoFromPlaylist(playlistId, videoPath)
            } catch (e: Exception) {
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_remove_video_failed)))
            }
        }
    }

    fun onPlayPlaylist(playlist: VideoPlaylist) {
        viewModelScope.launch {
            val videos = uiState.value.playlistVideos[playlist.id]
                ?: videoPlaylistRepository.getVideosInPlaylist(playlist.id).first().toImmutableList()
            if (videos.isNotEmpty()) {
                _events.send(VideoPlaylistEvent.PlayVideoPlaylist(videos))
            } else {
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_is_empty)))
            }
        }
    }

    fun onPlayVideoInPlaylist(video: Video, playlistId: Long) {
        viewModelScope.launch {
            val allVideos = uiState.value.playlistVideos[playlistId]
                ?: videoPlaylistRepository.getVideosInPlaylist(playlistId).first().toImmutableList()
            if (allVideos.isNotEmpty()) {
                _events.send(VideoPlaylistEvent.PlayVideoInPlaylist(video, allVideos))
            }
        }
    }

    fun toggleEditMode() {
        _internalState.update {
            it.copy(
                isEditMode = !it.isEditMode,
                selectedVideos = persistentSetOf(),
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

    fun toggleVideoSelection(playlistId: Long, videoPath: String) {
        _internalState.update { state ->
            val item = SelectedPlaylistVideo(playlistId, videoPath)
            val newSelection = if (state.selectedVideos.contains(item)) {
                state.selectedVideos.remove(item)
            } else {
                state.selectedVideos.add(item)
            }
            state.copy(selectedVideos = newSelection)
        }
    }

    fun removeSelected() {
        val selectedVideos = uiState.value.selectedVideos
        val selectedPlaylists = uiState.value.selectedPlaylistIds
        if (selectedVideos.isEmpty() && selectedPlaylists.isEmpty()) return

        viewModelScope.launch {
            try {
                selectedVideos.groupBy { it.playlistId }.forEach { (playlistId, selectedItems) ->
                    videoPlaylistRepository.removeVideosFromPlaylist(
                        playlistId,
                        selectedItems.map { it.videoPath }
                    )
                }
                if (selectedPlaylists.isNotEmpty()) {
                    videoPlaylistRepository.deleteVideoPlaylists(selectedPlaylists.toList())
                }
                _expandedIds.update { current -> current.removeAll(selectedPlaylists) }
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_selected_deleted, selectedVideos.size + selectedPlaylists.size)))
                toggleEditMode()
            } catch (e: Exception) {
                Timber.e(e, "Error in removeSelected for video playlists")
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_selected_delete_failed)))
            }
        }
    }

    fun moveSelectedUp() {
        val state = uiState.value
        if (state.selectedPlaylistIds.size == 1) {
            state.playlists.find { it.id == state.selectedPlaylistIds.first() }?.let { onMoveUp(it) }
        } else if (state.selectedVideos.size == 1) {
            val selected = state.selectedVideos.first()
            val videos = state.playlistVideos[selected.playlistId]?.toMutableList() ?: return
            val video = videos.find { it.path == selected.videoPath }
            video?.let { onMoveVideoUp(selected.playlistId, it) }
        }
    }

    fun moveSelectedDown() {
        val state = uiState.value
        if (state.selectedPlaylistIds.size == 1) {
            state.playlists.find { it.id == state.selectedPlaylistIds.first() }?.let { onMoveDown(it) }
        } else if (state.selectedVideos.size == 1) {
            val selected = state.selectedVideos.first()
            val videos = state.playlistVideos[selected.playlistId]?.toMutableList() ?: return
            val video = videos.find { it.path == selected.videoPath }
            video?.let { onMoveVideoDown(selected.playlistId, it) }
        }
    }

    fun onItemLongClicked(playlist: VideoPlaylist) {
        if (!uiState.value.isEditMode) toggleEditMode()
        togglePlaylistSelection(playlist.id)
    }

    fun onVideoLongClicked(playlistId: Long, video: Video) {
        if (!uiState.value.isEditMode) toggleEditMode()
        toggleVideoSelection(playlistId, video.path)
    }

    private fun onMoveUp(playlist: VideoPlaylist) {
        val currentList = uiState.value.playlists.toMutableList()
        val index = currentList.indexOfFirst { it.id == playlist.id }
        if (index > 0) {
            Collections.swap(currentList, index, index - 1)
            currentList[index] = currentList[index].copy(position = index)
            currentList[index - 1] = currentList[index - 1].copy(position = index - 1)
            savePlaylistsOrder(currentList)
        }
    }

    private fun onMoveDown(playlist: VideoPlaylist) {
        val currentList = uiState.value.playlists.toMutableList()
        val index = currentList.indexOfFirst { it.id == playlist.id }
        if (index != -1 && index < currentList.size - 1) {
            Collections.swap(currentList, index, index + 1)
            currentList[index] = currentList[index].copy(position = index)
            currentList[index + 1] = currentList[index + 1].copy(position = index + 1)
            savePlaylistsOrder(currentList)
        }
    }

    private fun savePlaylistsOrder(playlists: List<VideoPlaylist>) {
        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            delay(Constants.REORDER_DEBOUNCE_MS)
            try {
                videoPlaylistRepository.updateVideoPlaylistsOrder(playlists)
            } catch (e: Exception) {
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_move_failed)))
            }
        }
    }

    private fun onMoveVideoUp(playlistId: Long, video: Video) {
        val videos = uiState.value.playlistVideos[playlistId]?.toMutableList() ?: return
        val index = videos.indexOfFirst { it.path == video.path }
        if (index > 0) {
            Collections.swap(videos, index, index - 1)
            saveVideosOrder(playlistId, videos)
        }
    }

    private fun onMoveVideoDown(playlistId: Long, video: Video) {
        val videos = uiState.value.playlistVideos[playlistId]?.toMutableList() ?: return
        val index = videos.indexOfFirst { it.path == video.path }
        if (index != -1 && index < videos.size - 1) {
            Collections.swap(videos, index, index + 1)
            saveVideosOrder(playlistId, videos)
        }
    }

    private fun saveVideosOrder(playlistId: Long, videos: List<Video>) {
        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            delay(Constants.REORDER_DEBOUNCE_MS)
            try {
                videoPlaylistRepository.updatePlaylistVideosOrder(playlistId, videos)
            } catch (e: Exception) {
                _sideEffects.send(PlaylistSideEffect.ShowSnackbar(UiText.StringResource(R.string.playlist_move_failed)))
            }
        }
    }
}
