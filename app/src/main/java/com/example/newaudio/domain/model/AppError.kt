package com.example.newaudio.domain.model

/**
 * Represents a single error entry for the console.
 * @param timestamp The time the error occurred, in milliseconds since the epoch.
 */
data class AppError(
    val message: String,
    val throwable: Throwable? = null,
    val timestamp: Long = System.currentTimeMillis()
)
