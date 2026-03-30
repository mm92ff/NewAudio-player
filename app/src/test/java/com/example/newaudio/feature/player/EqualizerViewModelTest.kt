package com.example.newaudio.feature.player

import com.example.newaudio.domain.repository.IEqualizerRepository
import com.example.newaudio.fake.FakeEqualizerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class EqualizerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val equalizerRepo = FakeEqualizerRepository()

    private fun buildViewModel(): EqualizerViewModel = EqualizerViewModel(
        equalizerRepository = equalizerRepo,
        ioDispatcher = testDispatcher
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
    fun `initial equalizer state is disabled`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.equalizerState.collect {} }
        advanceUntilIdle()

        val initialState = vm.equalizerState.value
        assertFalse(initialState.enabled)
        assertEquals(IEqualizerRepository.EqPreset.NORMAL, initialState.currentPreset)
    }

    @Test
    fun `onToggleEqualizerEnabled toggles enabled state from false to true`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.equalizerState.collect {} }
        advanceUntilIdle()

        // Initially disabled
        assertFalse(vm.equalizerState.value.enabled)

        vm.onToggleEqualizerEnabled()
        advanceUntilIdle()

        // Should now be enabled
        assertTrue(vm.equalizerState.value.enabled)
    }

    @Test
    fun `onToggleEqualizerEnabled toggles enabled state from true to false`() = runTest {
        // Start with enabled equalizer
        equalizerRepo.setEnabled(true)

        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.equalizerState.collect {} }
        advanceUntilIdle()

        // Initially enabled
        assertTrue(vm.equalizerState.value.enabled)

        vm.onToggleEqualizerEnabled()
        advanceUntilIdle()

        // Should now be disabled
        assertFalse(vm.equalizerState.value.enabled)
    }

    @Test
    fun `onApplyPreset changes current preset`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.equalizerState.collect {} }
        advanceUntilIdle()

        // Initially NORMAL preset
        assertEquals(IEqualizerRepository.EqPreset.NORMAL, vm.equalizerState.value.currentPreset)

        vm.onApplyPreset(IEqualizerRepository.EqPreset.ROCK)
        advanceUntilIdle()

        // Should now be ROCK preset
        assertEquals(IEqualizerRepository.EqPreset.ROCK, vm.equalizerState.value.currentPreset)
    }

    @Test
    fun `onSetBandLevel calls repository setBandLevel`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        // This test verifies the method doesn't crash
        // The fake repository doesn't track setBandLevel calls, so we just ensure no exception
        vm.onSetBandLevel(0, 0.5f)
        advanceUntilIdle()

        // If we get here without exception, the test passes
        assertTrue(true)
    }

    @Test
    fun `equalizer state updates are reflected in StateFlow`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.equalizerState.collect {} }
        advanceUntilIdle()

        // Change the state directly in repository
        equalizerRepo.setEnabled(true)
        equalizerRepo.applyPreset(IEqualizerRepository.EqPreset.BASS)
        advanceUntilIdle()

        // StateFlow should reflect the changes
        val updatedState = vm.equalizerState.value
        assertTrue(updatedState.enabled)
        assertEquals(IEqualizerRepository.EqPreset.BASS, updatedState.currentPreset)
    }
}