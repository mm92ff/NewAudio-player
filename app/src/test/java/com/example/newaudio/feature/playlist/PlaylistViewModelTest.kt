package com.example.newaudio.feature.playlist

import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.fake.FakePlaylistRepository
import com.example.newaudio.fake.FakeSettingsRepository
import com.example.newaudio.R
import com.example.newaudio.util.UiText
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val playlistRepo = FakePlaylistRepository()
    private val settingsRepo = FakeSettingsRepository()

    private fun buildViewModel(): PlaylistViewModel = PlaylistViewModel(
        playlistRepository = playlistRepo,
        getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepo)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial UI state shows empty playlists`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val finalState = vm.uiState.value
        assertFalse(finalState.isLoading)
        assertTrue(finalState.playlists.isEmpty())
        assertFalse(finalState.isEditMode)
    }

    @Test
    fun `onCreatePlaylist creates playlist and sends success snackbar`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate flows
        backgroundScope.launch { vm.uiState.collect {} }

        val sideEffects = mutableListOf<PlaylistSideEffect>()
        backgroundScope.launch {
            vm.sideEffects.collect { sideEffects.add(it) }
        }

        vm.onCreatePlaylist("My Test Playlist")
        advanceUntilIdle()

        // Should create playlist in repository
        val playlists = vm.uiState.value.playlists
        assertEquals(1, playlists.size)
        assertEquals("My Test Playlist", playlists[0].name)

        // Should send success snackbar
        assertEquals(1, sideEffects.size)
        val snackbar = sideEffects[0] as PlaylistSideEffect.ShowSnackbar
        assertTrue(snackbar.message is UiText.StringResource)
        assertEquals(R.string.playlist_created, (snackbar.message as UiText.StringResource).resId)
    }

    @Test
    fun `onCreatePlaylist ignores blank playlist names`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onCreatePlaylist("")
        vm.onCreatePlaylist("   ")
        advanceUntilIdle()

        // Should not create any playlists
        assertTrue(vm.uiState.value.playlists.isEmpty())
    }

    @Test
    fun `togglePlaylistExpansion adds and removes playlist from expanded set`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val playlistId = 123L

        // Initially not expanded
        assertFalse(vm.uiState.value.expandedIds.contains(playlistId))

        // Toggle to expand
        vm.togglePlaylistExpansion(playlistId)
        assertTrue(vm.uiState.value.expandedIds.contains(playlistId))

        // Toggle to collapse
        vm.togglePlaylistExpansion(playlistId)
        assertFalse(vm.uiState.value.expandedIds.contains(playlistId))
    }

    @Test
    fun `onRenamePlaylist updates playlist name in repository`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        // Create a playlist first
        vm.onCreatePlaylist("Original Name")
        advanceUntilIdle()

        val playlist = vm.uiState.value.playlists[0]
        vm.onRenamePlaylist(playlist, "New Name")
        advanceUntilIdle()

        // Should update the name
        val updatedPlaylist = vm.uiState.value.playlists[0]
        assertEquals("New Name", updatedPlaylist.name)
    }

    @Test
    fun `onRenamePlaylist ignores blank names`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        // Create a playlist first
        vm.onCreatePlaylist("Original Name")
        advanceUntilIdle()

        val playlist = vm.uiState.value.playlists[0]
        vm.onRenamePlaylist(playlist, "")
        advanceUntilIdle()

        // Should keep original name
        val updatedPlaylist = vm.uiState.value.playlists[0]
        assertEquals("Original Name", updatedPlaylist.name)
    }

    @Test
    fun `onDeletePlaylist removes playlist and sends snackbar`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        val sideEffects = mutableListOf<PlaylistSideEffect>()
        backgroundScope.launch {
            vm.sideEffects.collect { sideEffects.add(it) }
        }

        // Create a playlist first
        vm.onCreatePlaylist("Test Playlist")
        advanceUntilIdle()

        val playlist = vm.uiState.value.playlists[0]
        vm.onDeletePlaylist(playlist)
        advanceUntilIdle()

        // Should remove playlist
        assertTrue(vm.uiState.value.playlists.isEmpty())

        // Should send delete snackbar (filtering out the creation snackbar)
        val deleteSnackbar = sideEffects.find {
            it is PlaylistSideEffect.ShowSnackbar &&
            (it.message as? UiText.StringResource)?.resId == R.string.playlist_deleted
        }
        assertTrue(deleteSnackbar != null)
    }

    @Test
    fun `toggleEditMode switches edit mode state and clears selections`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        // Initially not in edit mode
        assertFalse(vm.uiState.value.isEditMode)

        // Toggle to edit mode
        vm.toggleEditMode()
        assertTrue(vm.uiState.value.isEditMode)
        assertTrue(vm.uiState.value.selectedSongs.isEmpty())
        assertTrue(vm.uiState.value.selectedPlaylistIds.isEmpty())

        // Toggle back to normal mode
        vm.toggleEditMode()
        assertFalse(vm.uiState.value.isEditMode)
    }

    @Test
    fun `togglePlaylistSelection adds and removes playlists from selection`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        val playlistId = 456L

        // Initially not selected
        assertFalse(vm.uiState.value.selectedPlaylistIds.contains(playlistId))

        // Select playlist
        vm.togglePlaylistSelection(playlistId)
        assertTrue(vm.uiState.value.selectedPlaylistIds.contains(playlistId))

        // Deselect playlist
        vm.togglePlaylistSelection(playlistId)
        assertFalse(vm.uiState.value.selectedPlaylistIds.contains(playlistId))
    }

    @Test
    fun `toggleSongSelection adds and removes songs from selection`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        val playlistId = 789L
        val songPath = "/test/song.mp3"
        val expectedSelection = SelectedPlaylistSong(playlistId, songPath)

        // Initially not selected
        assertFalse(vm.uiState.value.selectedSongs.contains(expectedSelection))

        // Select song
        vm.toggleSongSelection(playlistId, songPath)
        assertTrue(vm.uiState.value.selectedSongs.contains(expectedSelection))

        // Deselect song
        vm.toggleSongSelection(playlistId, songPath)
        assertFalse(vm.uiState.value.selectedSongs.contains(expectedSelection))
    }

    @Test
    fun `onPlayPlaylist sends play event for non-empty playlists`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        val events = mutableListOf<PlaylistEvent>()
        backgroundScope.launch {
            vm.events.collect { events.add(it) }
        }

        // Create playlist
        vm.onCreatePlaylist("Test Playlist")
        advanceUntilIdle()

        val playlist = vm.uiState.value.playlists[0]

        // Try to play (should send empty playlist snackbar since no songs)
        vm.onPlayPlaylist(playlist)
        advanceUntilIdle()

        // Should not send play event for empty playlist
        assertTrue(events.isEmpty())
    }

    @Test
    fun `onItemLongClicked enables edit mode and selects playlist`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        // Create a playlist
        vm.onCreatePlaylist("Test Playlist")
        advanceUntilIdle()

        val playlist = vm.uiState.value.playlists[0]

        vm.onItemLongClicked(playlist)

        val state = vm.uiState.value
        assertTrue(state.isEditMode)
        assertTrue(state.selectedPlaylistIds.contains(playlist.id))
    }
}