package com.example.newaudio.domain.repository

import com.example.newaudio.domain.model.LogEntry
import com.example.newaudio.domain.model.LogLevel
import kotlinx.coroutines.flow.Flow

interface IErrorRepository {
    fun getLogs(): Flow<List<LogEntry>>
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) // CORRECTED: Not a suspend function
    suspend fun clearLogs()
}
