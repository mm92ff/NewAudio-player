package com.example.newaudio.domain.repository

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistExportModelsTest {

    @Test
    fun `old playlist backup decodes with empty video playlists`() {
        val json = """
            {
              "version": 2,
              "playlists": [
                {
                  "name": "Audio",
                  "createdAt": 10,
                  "songs": []
                }
              ]
            }
        """.trimIndent()

        val container = Json { ignoreUnknownKeys = true }
            .decodeFromString<PlaylistExportContainer>(json)

        assertEquals(2, container.version)
        assertEquals("Audio", container.playlists.single().name)
        assertTrue(container.videoPlaylists.isEmpty())
        assertTrue(container.videoMarkers.isEmpty())
    }

    @Test
    fun `video playlists round trip through backup format`() {
        val container = PlaylistExportContainer(
            playlists = emptyList(),
            videoPlaylists = listOf(
                VideoPlaylistExportModel(
                    name = "Training",
                    createdAt = 20,
                    videos = listOf(
                        VideoExportModel(
                            path = "/videos/clip.mp4",
                            title = "clip",
                            duration = 30_000,
                            size = 1234
                        )
                    )
                )
            )
        )

        val json = Json.encodeToString(container)
        val decoded = Json.decodeFromString<PlaylistExportContainer>(json)

        assertEquals(4, decoded.version)
        assertEquals("Training", decoded.videoPlaylists.single().name)
        assertEquals("/videos/clip.mp4", decoded.videoPlaylists.single().videos.single().path)
    }

    @Test
    fun `video markers round trip through backup format`() {
        val container = PlaylistExportContainer(
            playlists = emptyList(),
            videoMarkers = listOf(
                VideoMarkerExportModel(
                    videoPath = "/videos/clip.mp4",
                    fileHash = "abc",
                    filename = "clip.mp4",
                    fileSize = 1234,
                    durationMs = 30_000,
                    positionMs = 12_000,
                    createdAt = 20,
                    updatedAt = 30
                )
            )
        )

        val json = Json.encodeToString(container)
        val decoded = Json.decodeFromString<PlaylistExportContainer>(json)

        assertEquals(4, decoded.version)
        assertEquals(12_000, decoded.videoMarkers.single().positionMs)
        assertEquals("abc", decoded.videoMarkers.single().fileHash)
    }
}
