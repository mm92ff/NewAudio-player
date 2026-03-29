package com.example.newaudio.domain.repository

import com.example.newaudio.domain.model.UserPreferences
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistExportContainer(
    val version: Int = 2,
    val playlists: List<PlaylistExportModel>,
    val settings: UserPreferences? = null
)

@Serializable
data class PlaylistExportModel(
    val name: String,
    val createdAt: Long,
    val songs: List<SongExportModel>
)

@Serializable
data class SongExportModel(
    val path: String,
    val title: String,
    val artist: String,
    val size: Long,
    val fileHash: String?
)