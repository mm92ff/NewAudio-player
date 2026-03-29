package com.example.newaudio.feature.player

import androidx.media3.common.Player
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.usecase.file.GetParentPathUseCase
import com.example.newaudio.domain.usecase.file.GetRootPathUseCase
import com.example.newaudio.domain.usecase.media.GetSongMetadataUseCase
import com.example.newaudio.domain.usecase.media.RestorePlaybackStateUseCase
import com.example.newaudio.domain.usecase.media.SavePlaybackStateUseCase
import com.example.newaudio.domain.usecase.media.ScanLibraryIfEmptyUseCase
import com.example.newaudio.domain.usecase.player.ApplyUserPreferencesUseCase
import com.example.newaudio.domain.usecase.player.InitializePlaybackSessionUseCase
import com.example.newaudio.domain.usecase.player.SeekTrackUseCase
import com.example.newaudio.domain.usecase.player.SkipTrackUseCase
import com.example.newaudio.domain.usecase.player.TogglePlaybackUseCase
import com.example.newaudio.domain.usecase.player.ToggleRepeatModeUseCase
import com.example.newaudio.domain.usecase.player.ToggleShuffleUseCase
import com.example.newaudio.fake.FakeEqualizerRepository
import com.example.newaudio.fake.FakeErrorRepository
import com.example.newaudio.fake.FakeMediaRepository
import com.example.newaudio.fake.FakeMediaScannerRepository
import com.example.newaudio.fake.FakeSettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mediaRepo = FakeMediaRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val equalizerRepo = FakeEqualizerRepository()
    private val errorRepo = FakeErrorRepository()
    private val scannerRepo = FakeMediaScannerRepository()

    // GetSongMetadataUseCase requires Android Context — mock it
    private val getSongMetadata = mockk<GetSongMetadataUseCase>(relaxed = true).also {
        coEvery { it(any()) } returns emptyMap()
    }

    private fun buildViewModel(): PlayerViewModel {
        val saveState = SavePlaybackStateUseCase(settingsRepo)
        val applyPrefs = ApplyUserPreferencesUseCase(settingsRepo, mediaRepo)
        val scanIfEmpty = ScanLibraryIfEmptyUseCase(mediaRepo, scannerRepo, GetRootPathUseCase())
        val restoreState = RestorePlaybackStateUseCase(settingsRepo, GetParentPathUseCase(), mediaRepo)
        val initSession = InitializePlaybackSessionUseCase(mediaRepo, applyPrefs, scanIfEmpty, restoreState)

        return PlayerViewModel(
            mediaRepository = mediaRepo,
            initializePlaybackSessionUseCase = initSession,
            togglePlaybackUseCase = TogglePlaybackUseCase(mediaRepo),
            seekTrackUseCase = SeekTrackUseCase(mediaRepo, saveState),
            skipTrackUseCase = SkipTrackUseCase(mediaRepo),
            toggleShuffleUseCase = ToggleShuffleUseCase(mediaRepo),
            toggleRepeatModeUseCase = ToggleRepeatModeUseCase(mediaRepo),
            getSongMetadataUseCase = getSongMetadata,
            equalizerRepository = equalizerRepo,
            settingsRepository = settingsRepo,
            errorRepository = errorRepo,
            ioDispatcher = testDispatcher
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onPlayPauseToggle delegates to media repository`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onPlayPauseToggle()
        advanceUntilIdle()
        assertEquals(1, mediaRepo.togglePlaybackCalled)
    }

    @Test
    fun `onSkipNext delegates to media repository`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onSkipNext()
        advanceUntilIdle()
        assertEquals(1, mediaRepo.skipNextCalled)
    }

    @Test
    fun `onSkipPrevious delegates to media repository`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onSkipPrevious()
        advanceUntilIdle()
        assertEquals(1, mediaRepo.skipPreviousCalled)
    }

    @Test
    fun `onToggleRepeatOne switches mode to ONE when not already ONE`() = runTest {
        mediaRepo.setState(
            IMediaRepository.PlaybackState(repeatMode = Player.REPEAT_MODE_ALL)
        )
        val vm = buildViewModel()
        // backgroundScope is not checked for completion by runTest — safe for long-lived collectors
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onToggleRepeatOne()
        advanceUntilIdle()
        assertEquals(UserPreferences.RepeatMode.ONE, mediaRepo.setRepeatModeCalled)
    }

    @Test
    fun `onToggleRepeatOne restores previous mode when already ONE`() = runTest {
        mediaRepo.setState(IMediaRepository.PlaybackState(repeatMode = Player.REPEAT_MODE_ALL))
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        // First call: remember ALL, switch to ONE
        vm.onToggleRepeatOne()
        advanceUntilIdle()
        assertEquals(UserPreferences.RepeatMode.ONE, mediaRepo.setRepeatModeCalled)
        // Second call: restore ALL
        vm.onToggleRepeatOne()
        advanceUntilIdle()
        assertEquals(UserPreferences.RepeatMode.ALL, mediaRepo.setRepeatModeCalled)
    }

    @Test
    fun `onSeek calls seekTo with long value`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onSeek(45_000f)
        advanceUntilIdle()
        assertEquals(45_000L, mediaRepo.seekToPosition)
    }
}
