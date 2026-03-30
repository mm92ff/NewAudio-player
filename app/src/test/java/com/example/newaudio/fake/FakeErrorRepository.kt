package com.example.newaudio.fake

import com.example.newaudio.domain.model.LogEntry
import com.example.newaudio.domain.model.LogLevel
import com.example.newaudio.domain.repository.IErrorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeErrorRepository : IErrorRepository {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: List<LogEntry> get() = _logs.value

    override fun getLogs(): Flow<List<LogEntry>> = _logs.asStateFlow()

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val newEntry = LogEntry(
            message = message,
            level = level,
            tag = tag,
            throwableString = throwable?.toString()
        )
        _logs.value = _logs.value + newEntry
    }

    override suspend fun clearLogs() {
        _logs.value = emptyList()
    }

    // Test helper methods
    fun addLog(message: String, level: LogLevel, tag: String = "TEST", throwableString: String? = null) {
        val newEntry = LogEntry(
            message = message,
            level = level,
            tag = tag,
            throwableString = throwableString
        )
        _logs.value = _logs.value + newEntry
    }

    fun addMultipleLogs(vararg entries: Pair<String, LogLevel>) {
        val newEntries = entries.map { (message, level) ->
            LogEntry(message = message, level = level, tag = "TEST")
        }
        _logs.value = _logs.value + newEntries
    }
}
