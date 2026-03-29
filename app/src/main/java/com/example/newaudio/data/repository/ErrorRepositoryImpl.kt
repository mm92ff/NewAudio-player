package com.example.newaudio.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.LogEntry
import com.example.newaudio.domain.model.LogLevel
import com.example.newaudio.domain.repository.IErrorRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IErrorRepository {

    private val repoScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private object Keys {
        val LOGS = stringPreferencesKey("console_logs")
    }

    companion object {
        private const val LOG_CAPACITY = 500
    }

    override fun getLogs(): Flow<List<LogEntry>> {
        return dataStore.data.map { preferences ->
            val jsonString = preferences[Keys.LOGS]
            if (jsonString.isNullOrEmpty()) {
                emptyList()
            } else {
                try {
                    Json.decodeFromString<List<LogEntry>>(jsonString)
                } catch (e: Exception) {
                    listOf(LogEntry(
                        message = "Failed to decode logs: ${e.message}",
                        level = LogLevel.ERROR,
                        tag = "ErrorRepository"
                    ))
                }
            }
        }
    }

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        repoScope.launch {
            dataStore.edit { prefs ->
                val currentJson = prefs[Keys.LOGS]
                val currentLogs = if (currentJson.isNullOrEmpty()) {
                    mutableListOf()
                } else {
                    try {
                        Json.decodeFromString<List<LogEntry>>(currentJson).toMutableList()
                    } catch (e: Exception) {
                        mutableListOf()
                    }
                }

                val newEntry = LogEntry(
                    level = level,
                    tag = tag,
                    message = message,
                    throwableString = throwable?.stackTraceToString()
                )

                currentLogs.add(0, newEntry)

                var logsAfterTrimming = currentLogs.toList()

                if (logsAfterTrimming.size > LOG_CAPACITY) {
                    logsAfterTrimming = logsAfterTrimming.filter { it.level != LogLevel.INFO }
                }

                if (logsAfterTrimming.size > LOG_CAPACITY) {
                    logsAfterTrimming = logsAfterTrimming.filter { it.level != LogLevel.WARN }
                }

                val finalLogs = logsAfterTrimming.take(LOG_CAPACITY)

                prefs[Keys.LOGS] = Json.encodeToString(finalLogs)
            }
        }
    }

    override suspend fun clearLogs() {
        dataStore.edit { it.remove(Keys.LOGS) }
    }
}
