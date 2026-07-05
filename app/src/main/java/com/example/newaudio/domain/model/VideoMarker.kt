package com.example.newaudio.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class VideoMarker(
    val id: Long = 0L,
    val videoPath: String,
    val fileHash: String? = null,
    val filename: String,
    val fileSize: Long,
    val durationMs: Long,
    val positionMs: Long,
    val createdAt: Long,
    val updatedAt: Long
)
