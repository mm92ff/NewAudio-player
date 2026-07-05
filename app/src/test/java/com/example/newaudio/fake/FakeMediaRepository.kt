package com.example.newaudio.fake

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.model.Video
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
    var lastVideoPlaylistPlayed: List<Video>? = null
    var lastStartIndex: Int? = null
    var lastFolderPath: String? = null
    var resumeLastMusicSessionCalled = 0
    var resumeLastVideoSessionCalled = 0
    var resumeLastMusicSessionResult = false
    var resumeLastVideoSessionResult = false
    var resumeLastMusicSessionState: IMediaRepository.PlaybackState? = null
    var resumeLastVideoSessionState: IMediaRepository.PlaybackState? = null
    var removedDeletedMediaPaths: List<String>? = null
    var setRepeatModeCalled: UserPreferences.RepeatMode? = null
    var clearDatabaseCalled = false
    var initializeCalled = false

    // Configurable stubs
    var stubbedLibrarySongCount = 0
    var stubbedLibraryVideoCount = 0
    var stubbedParentPath: String? = null
    var stubbedSongsInFolder: List<Song> = emptyList()
    var stubbedVideosInFolder: List<Video> = emptyList()

    fun setState(state: IMediaRepository.PlaybackState) {
        _playbackState.value = state
    }

    override fun getPlaybackState(): Flow<IMediaRepository.PlaybackState> = _playbackState

    override suspend fun initialize() { initializeCalled = true }

    override suspend fun getLibrarySongCount() = stubbedLibrarySongCount
    override suspend fun getLibraryVideoCount() = stubbedLibraryVideoCount

    override suspend fun playPlaylist(songs: List<Song>, startIndex: Int, folderPath: String?) {
        lastPlaylistPlayed = songs
        lastStartIndex = startIndex
        lastFolderPath = folderPath
    }

    override suspend fun playVideoPlaylist(videos: List<Video>, startIndex: Int, folderPath: String?) {
        lastVideoPlaylistPlayed = videos
        lastStartIndex = startIndex
        lastFolderPath = folderPath
    }

    override suspend fun resumeLastMusicSession(): Boolean {
        resumeLastMusicSessionCalled++
        if (resumeLastMusicSessionResult) {
            resumeLastMusicSessionState?.let { _playbackState.value = it }
        }
        return resumeLastMusicSessionResult
    }

    override suspend fun resumeLastVideoSession(): Boolean {
        resumeLastVideoSessionCalled++
        if (resumeLastVideoSessionResult) {
            resumeLastVideoSessionState?.let { _playbackState.value = it }
        }
        return resumeLastVideoSessionResult
    }

    override suspend fun restorePlaylist(songs: List<Song>, startIndex: Int, startPosition: Long, folderPath: String?) {}

    override suspend fun ensureSongInLibraryAndGetParentPath(songPath: String) = stubbedParentPath

    override suspend fun getSongsInFolder(parentPath: String) = stubbedSongsInFolder
    override suspend fun getVideosInFolder(parentPath: String) = stubbedVideosInFolder

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

    override suspend fun removeDeletedMedia(paths: List<String>) {
        removedDeletedMediaPaths = paths
        val normalized = paths.map { it.removeSuffix("/") }
        _playbackState.value = _playbackState.value.let { state ->
            state.copy(
                currentSong = state.currentSong?.takeUnless { song ->
                    normalized.any { deleted -> song.path.isDeletedBy(deleted) }
                },
                currentVideo = state.currentVideo?.takeUnless { video ->
                    normalized.any { deleted -> video.path.isDeletedBy(deleted) }
                },
                isPlaying = if (
                    state.currentSong?.let { song -> normalized.any { deleted -> song.path.isDeletedBy(deleted) } } == true ||
                    state.currentVideo?.let { video -> normalized.any { deleted -> video.path.isDeletedBy(deleted) } } == true
                ) {
                    false
                } else {
                    state.isPlaying
                }
            )
        }
    }

    override suspend fun clearPlayerError() {
        _playbackState.value = _playbackState.value.copy(playerError = null)
    }

    override suspend fun clearDatabase() { clearDatabaseCalled = true }

    private fun String.isDeletedBy(deletedPath: String): Boolean {
        val path = removeSuffix("/")
        val deleted = deletedPath.removeSuffix("/")
        return path == deleted || path.startsWith("$deleted/")
    }
}
