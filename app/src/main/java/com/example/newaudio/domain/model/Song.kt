package com.example.newaudio.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Song(
    val path: String,
    val contentUri: String, // ✅ NEW: Required for robust playback (Media3/ExoPlayer)
    val title: String,
    val artist: String,
    val duration: Long,
    val albumArtPath: String?
)