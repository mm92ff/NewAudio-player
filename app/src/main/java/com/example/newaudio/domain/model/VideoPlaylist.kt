package com.example.newaudio.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VideoPlaylist(
    val id: Long = 0,
    val name: String,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
