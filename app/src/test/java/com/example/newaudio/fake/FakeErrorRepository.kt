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
        // no-op in tests — avoids Timber.plant() requirement
    }

    override suspend fun clearLogs() {
        _logs.value = emptyList()
    }
}
