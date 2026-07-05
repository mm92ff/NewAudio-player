package com.example.newaudio.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Video(
    val path: String,
    val contentUri: String,
    val title: String,
    val duration: Long,
    val thumbnailUri: String?,
    val width: Int = 0,
    val height: Int = 0
)
