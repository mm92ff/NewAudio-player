package com.example.newaudio.feature.console

import com.example.newaudio.domain.model.LogLevel
import com.example.newaudio.fake.FakeErrorRepository
import kotlinx.collections.immutable.persistentSetOf
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val errorRepo = FakeErrorRepository()

    private fun buildViewModel(): ConsoleViewModel = ConsoleViewModel(
        errorRepository = errorRepo
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
    fun `initial state shows empty logs with default filters`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val initialState = vm.uiState.value
        assertTrue(initialState.logs.isEmpty())
        assertEquals(
            persistentSetOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR),
            initialState.activeFilters
        )
    }

    @Test
    fun `logs are filtered by active filters`() = runTest {
        // Add logs of different levels
        errorRepo.addMultipleLogs(
            "Info message" to LogLevel.INFO,
            "Warning message" to LogLevel.WARN,
            "Error message" to LogLevel.ERROR
        )

        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // All logs should be visible with default filters
        val state = vm.uiState.value
        assertEquals(3, state.logs.size)
    }

    @Test
    fun `onFilterToggle removes filter when it exists`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Initially INFO filter is active
        assertTrue(vm.uiState.value.activeFilters.contains(LogLevel.INFO))

        // Toggle INFO filter off
        vm.onFilterToggle(LogLevel.INFO)
        advanceUntilIdle()

        // INFO filter should be removed
        assertFalse(vm.uiState.value.activeFilters.contains(LogLevel.INFO))
        assertTrue(vm.uiState.value.activeFilters.contains(LogLevel.WARN))
        assertTrue(vm.uiState.value.activeFilters.contains(LogLevel.ERROR))
    }

    @Test
    fun `onFilterToggle adds filter when it does not exist`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Remove INFO filter first
        vm.onFilterToggle(LogLevel.INFO)
        advanceUntilIdle()

        // Confirm INFO is removed
        assertFalse(vm.uiState.value.activeFilters.contains(LogLevel.INFO))

        // Toggle INFO filter back on
        vm.onFilterToggle(LogLevel.INFO)
        advanceUntilIdle()

        // INFO filter should be added back
        assertTrue(vm.uiState.value.activeFilters.contains(LogLevel.INFO))
    }

    @Test
    fun `filtering hides logs not matching active filters`() = runTest {
        // Add logs of different levels
        errorRepo.addMultipleLogs(
            "Info message" to LogLevel.INFO,
            "Warning message" to LogLevel.WARN,
            "Error message" to LogLevel.ERROR
        )

        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Initially all 3 logs should be visible
        assertEquals(3, vm.uiState.value.logs.size)

        // Remove INFO filter
        vm.onFilterToggle(LogLevel.INFO)
        advanceUntilIdle()

        // Only WARN and ERROR logs should be visible
        val filteredLogs = vm.uiState.value.logs
        assertEquals(2, filteredLogs.size)
        assertTrue(filteredLogs.none { it.level == LogLevel.INFO })
        assertTrue(filteredLogs.any { it.level == LogLevel.WARN })
        assertTrue(filteredLogs.any { it.level == LogLevel.ERROR })
    }

    @Test
    fun `onClearConsole removes all logs`() = runTest {
        // Add some logs
        errorRepo.addMultipleLogs(
            "Test message 1" to LogLevel.INFO,
            "Test message 2" to LogLevel.ERROR
        )

        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Confirm logs are present
        assertEquals(2, vm.uiState.value.logs.size)

        // Clear console
        vm.onClearConsole()
        advanceUntilIdle()

        // Logs should be cleared
        assertTrue(vm.uiState.value.logs.isEmpty())
        // Verify the repository was called
        assertTrue(errorRepo.logs.isEmpty())
    }

    @Test
    fun `getLogsForClipboard returns only WARN and ERROR logs formatted as strings`() = runTest {
        errorRepo.addMultipleLogs(
            "Info message" to LogLevel.INFO,
            "Warning message" to LogLevel.WARN,
            "Error message" to LogLevel.ERROR
        )

        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val clipboardText = vm.getLogsForClipboard()

        // Should contain WARN and ERROR logs but not INFO
        assertTrue(clipboardText.contains("Warning message"))
        assertTrue(clipboardText.contains("Error message"))
        assertFalse(clipboardText.contains("Info message"))

        // Should have two log entries separated by newline
        assertEquals(2, clipboardText.split("\n").size)
    }

    @Test
    fun `getLogsForClipboard returns empty string when no WARN or ERROR logs exist`() = runTest {
        errorRepo.addLog("Only info message", LogLevel.INFO)

        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val clipboardText = vm.getLogsForClipboard()

        assertTrue(clipboardText.isEmpty())
    }

    @Test
    fun `logs update in real-time when repository adds new logs`() = runTest {
        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Initially no logs
        assertTrue(vm.uiState.value.logs.isEmpty())

        // Add a new log to repository
        errorRepo.addLog("New runtime log", LogLevel.ERROR)
        advanceUntilIdle()

        // Should appear in UI state
        val logs = vm.uiState.value.logs
        assertEquals(1, logs.size)
        assertEquals("New runtime log", logs[0].message)
        assertEquals(LogLevel.ERROR, logs[0].level)
    }

    @Test
    fun `all filters can be toggled off resulting in empty log display`() = runTest {
        errorRepo.addMultipleLogs(
            "Info message" to LogLevel.INFO,
            "Warning message" to LogLevel.WARN,
            "Error message" to LogLevel.ERROR
        )

        val vm = buildViewModel()

        // Start collection to activate the StateFlow
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Initially all logs visible
        assertEquals(3, vm.uiState.value.logs.size)

        // Turn off all filters
        vm.onFilterToggle(LogLevel.INFO)
        vm.onFilterToggle(LogLevel.WARN)
        vm.onFilterToggle(LogLevel.ERROR)
        advanceUntilIdle()

        // No logs should be visible
        assertTrue(vm.uiState.value.logs.isEmpty())
        assertTrue(vm.uiState.value.activeFilters.isEmpty())
    }
}