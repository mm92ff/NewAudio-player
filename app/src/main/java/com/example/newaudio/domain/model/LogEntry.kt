package com.example.newaudio.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a single log entry for the console.
 * @param timestamp The time the entry was created, in milliseconds since the epoch.
 * @param level The severity of the log entry.
 * @param tag The source of the log entry (e.g., ViewModel class name).
 * @param throwableString The string representation of a throwable, if any.
 */
@Serializable
data class LogEntry(
    val message: String,
    val level: LogLevel,
    val tag: String,
    val throwableString: String? = null, // Can't serialize Throwable directly
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class LogLevel {
    INFO, WARN, ERROR
}
