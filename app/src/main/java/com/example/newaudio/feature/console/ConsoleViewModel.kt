package com.example.newaudio.feature.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Stable
import com.example.newaudio.domain.model.LogEntry
import com.example.newaudio.domain.model.LogLevel
import com.example.newaudio.domain.repository.IErrorRepository
import com.example.newaudio.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
data class ConsoleUiState(
    val logs: ImmutableList<LogEntry> = persistentListOf(),
    val activeFilters: PersistentSet<LogLevel> = persistentSetOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR)
)

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    private val errorRepository: IErrorRepository
) : ViewModel() {

    private val _activeFilters = MutableStateFlow<PersistentSet<LogLevel>>(persistentSetOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR))

    val uiState: StateFlow<ConsoleUiState> = combine(
        errorRepository.getLogs(),
        _activeFilters
    ) { logs, filters ->
        ConsoleUiState(
            logs = logs.filter { it.level in filters }.toImmutableList(),
            activeFilters = filters
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.STATE_FLOW_SHARING_TIMEOUT_MS),
        initialValue = ConsoleUiState()
    )

    fun onClearConsole() {
        viewModelScope.launch {
            errorRepository.clearLogs()
        }
    }

    fun onFilterToggle(level: LogLevel) {
        _activeFilters.update { current ->
            if (level in current) current.remove(level) else current.add(level)
        }
    }

    fun getLogsForClipboard(): String {
        return uiState.value.logs
            .filter { it.level == LogLevel.WARN || it.level == LogLevel.ERROR }
            .joinToString("\n") { it.toString() }
    }
}
