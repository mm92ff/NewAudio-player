package com.example.newaudio.domain.repository

import com.example.newaudio.domain.model.UserPreferences
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistExportContainer(
    val version: Int = 4,
    val playlists: List<PlaylistExportModel>,
    val settings: UserPreferences? = null,
    val videoPlaylists: List<VideoPlaylistExportModel> = emptyList(),
    val videoMarkers: List<VideoMarkerExportModel> = emptyList()
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

@Serializable
data class VideoPlaylistExportModel(
    val name: String,
    val createdAt: Long,
    val videos: List<VideoExportModel>
)

@Serializable
data class VideoExportModel(
    val path: String,
    val title: String,
    val duration: Long,
    val size: Long
)

@Serializable
data class VideoMarkerExportModel(
    val videoPath: String,
    val fileHash: String?,
    val filename: String,
    val fileSize: Long,
    val durationMs: Long,
    val positionMs: Long,
    val createdAt: Long,
    val updatedAt: Long
)
