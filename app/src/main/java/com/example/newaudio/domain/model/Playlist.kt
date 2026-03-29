package com.example.newaudio.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: Long = 0,
    val name: String,
    val position: Int = 0, // New: for reordering
    val createdAt: Long = System.currentTimeMillis()
)
