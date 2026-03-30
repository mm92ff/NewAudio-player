package com.example.newaudio.fake

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IMediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeMediaRepository : IMediaRepository {

    private val _playbackState = MutableStateFlow(IMediaRepository.PlaybackState())
    val playbackState get() = _playbackState.asStateFlow()

    var togglePlaybackCalled = 0
    var toggleShuffleCalled = 0
    var skipNextCalled = 0
    var skipPreviousCalled = 0
    var seekToPosition: Long? = null
    var lastPlaylistPlayed: List<Song>? = null
    var lastStartIndex: Int? = null
    var lastFolderPath: String? = null
    var setRepeatModeCalled: UserPreferences.RepeatMode? = null
    var clearDatabaseCalled = false
    var initializeCalled = false

    // Configurable stubs
    var stubbedLibrarySongCount = 0
    var stubbedParentPath: String? = null
    var stubbedSongsInFolder: List<Song> = emptyList()

    fun setState(state: IMediaRepository.PlaybackState) {
        _playbackState.value = state
    }

    override fun getPlaybackState(): Flow<IMediaRepository.PlaybackState> = _playbackState

    override suspend fun initialize() { initializeCalled = true }

    override suspend fun getLibrarySongCount() = stubbedLibrarySongCount

    override suspend fun playPlaylist(songs: List<Song>, startIndex: Int, folderPath: String?) {
        lastPlaylistPlayed = songs
        lastStartIndex = startIndex
        lastFolderPath = folderPath
    }

    override suspend fun restorePlaylist(songs: List<Song>, startIndex: Int, startPosition: Long, folderPath: String?) {}

    override suspend fun ensureSongInLibraryAndGetParentPath(songPath: String) = stubbedParentPath

    override suspend fun getSongsInFolder(parentPath: String) = stubbedSongsInFolder

    override suspend fun togglePlayback() { togglePlaybackCalled++ }

    override suspend fun toggleShuffle() { toggleShuffleCalled++ }

    override suspend fun setShuffleEnabled(enabled: Boolean) {}

    override suspend fun setRepeatMode(repeatMode: UserPreferences.RepeatMode) {
        setRepeatModeCalled = repeatMode
        _playbackState.value = _playbackState.value.copy(
            repeatMode = when (repeatMode) {
                UserPreferences.RepeatMode.NONE -> 0
                UserPreferences.RepeatMode.ONE -> 1
                UserPreferences.RepeatMode.ALL -> 2
            }
        )
    }

    override suspend fun skipNext() { skipNextCalled++ }

    override suspend fun skipPrevious() { skipPreviousCalled++ }

    override suspend fun seekTo(position: Long) { seekToPosition = position }

    override suspend fun clearPlayerError() {
        _playbackState.value = _playbackState.value.copy(playerError = null)
    }

    override suspend fun clearDatabase() { clearDatabaseCalled = true }
}
