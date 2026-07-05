package com.example.newaudio.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.newaudio.data.database.PlaylistEntity
import com.example.newaudio.data.database.VideoEntity
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.data.database.VideoMarkerEntity
import com.example.newaudio.data.database.dao.VideoMarkerDao
import com.example.newaudio.data.database.VideoPlaylistEntity
import com.example.newaudio.data.database.VideoPlaylistItemEntity
import com.example.newaudio.data.database.dao.PlaylistDao
import com.example.newaudio.data.database.dao.VideoPlaylistDao
import com.example.newaudio.data.database.dao.VideoPlaylistVideoResult
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.PlaylistExportContainer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryImplTest {

    private val dispatcher = StandardTestDispatcher()
    private val playlistDao = mockk<PlaylistDao>(relaxed = true)
    private val videoPlaylistDao = mockk<VideoPlaylistDao>(relaxed = true)
    private val videoDao = mockk<VideoDao>(relaxed = true)
    private val videoMarkerDao = mockk<VideoMarkerDao>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private fun buildRepository(): PlaylistRepositoryImpl {
        every { context.contentResolver } returns contentResolver
        return PlaylistRepositoryImpl(
            playlistDao = playlistDao,
            videoPlaylistDao = videoPlaylistDao,
            videoDao = videoDao,
            videoMarkerDao = videoMarkerDao,
            context = context,
            ioDispatcher = dispatcher
        )
    }

    @Test
    fun `exportPlaylists writes video playlist with real video size`() = runTest(dispatcher) {
        val output = File.createTempFile("playlist-export", ".json").apply { deleteOnExit() }
        every { playlistDao.getAllPlaylists() } returns flowOf(emptyList<PlaylistEntity>())
        coEvery { videoMarkerDao.getAllMarkers() } returns emptyList()
        every { videoPlaylistDao.getAllVideoPlaylists() } returns flowOf(
            listOf(VideoPlaylistEntity(id = 7L, name = "Training", position = 0, createdAt = 10L))
        )
        every { videoPlaylistDao.getVideosInPlaylist(7L) } returns flowOf(
            listOf(
                VideoPlaylistVideoResult(
                    path = "/videos/clip.mp4",
                    contentUri = "content://video/clip",
                    title = "clip",
                    duration = 30_000L,
                    thumbnailUri = null,
                    size = 1234L,
                    width = 1920,
                    height = 1080,
                    position = 0
                )
            )
        )
        every { contentResolver.openOutputStream(any<Uri>()) } returns FileOutputStream(output)

        val success = buildRepository().exportPlaylists("content://backup/export", UserPreferences.default())

        assertTrue(success)
        val decoded = Json.decodeFromString<PlaylistExportContainer>(output.readText())
        assertEquals(1234L, decoded.videoPlaylists.single().videos.single().size)
    }

    @Test
    fun `exportPlaylists writes video markers with hash metadata`() = runTest(dispatcher) {
        val output = File.createTempFile("marker-export", ".json").apply { deleteOnExit() }
        every { playlistDao.getAllPlaylists() } returns flowOf(emptyList<PlaylistEntity>())
        every { videoPlaylistDao.getAllVideoPlaylists() } returns flowOf(emptyList<VideoPlaylistEntity>())
        coEvery { videoMarkerDao.getAllMarkers() } returns listOf(
            VideoMarkerEntity(
                id = 4L,
                videoPath = "/videos/clip.mp4",
                fileHash = "video-hash",
                filename = "clip.mp4",
                fileSize = 1234L,
                durationMs = 30_000L,
                positionMs = 12_000L,
                createdAt = 20L,
                updatedAt = 30L
            )
        )
        every { contentResolver.openOutputStream(any<Uri>()) } returns FileOutputStream(output)

        val success = buildRepository().exportPlaylists("content://backup/export", UserPreferences.default())

        assertTrue(success)
        val decoded = Json.decodeFromString<PlaylistExportContainer>(output.readText())
        val marker = decoded.videoMarkers.single()
        assertEquals("/videos/clip.mp4", marker.videoPath)
        assertEquals("video-hash", marker.fileHash)
        assertEquals(12_000L, marker.positionMs)
    }

    @Test
    fun `importPlaylists restores video playlist through filename and size fallback`() = runTest(dispatcher) {
        val input = File.createTempFile("playlist-import", ".json").apply {
            writeText(
                """
                {
                  "version": 3,
                  "playlists": [],
                  "videoPlaylists": [
                    {
                      "name": "Training",
                      "createdAt": 20,
                      "videos": [
                        {
                          "path": "/old/folder/clip.mp4",
                          "title": "clip",
                          "duration": 30000,
                          "size": 1234
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )
            deleteOnExit()
        }
        every { contentResolver.openInputStream(any<Uri>()) } returns FileInputStream(input)
        coEvery { videoPlaylistDao.findVideoByPath("/old/folder/clip.mp4") } returns null
        coEvery { videoPlaylistDao.findVideoByFilenameAndSize("clip.mp4", 1234L) } returns VideoEntity(
            path = "/new/folder/clip.mp4",
            contentUri = "content://video/clip",
            title = "clip",
            duration = 30_000L,
            thumbnailUri = null,
            parentPath = "/new/folder",
            filename = "clip.mp4",
            lastModified = 0L,
            size = 1234L,
            width = 1920,
            height = 1080
        )
        coEvery {
            videoPlaylistDao.importPlaylistWithVideos(any(), any())
        } returns 1L

        val result = buildRepository().importPlaylists("content://backup/import")

        assertEquals(1, result.playlistsImported)
        assertEquals(1, result.songsFixed)
        coVerify {
            videoPlaylistDao.importPlaylistWithVideos(
                match { it.name == "Training" && it.createdAt == 20L },
                match<List<VideoPlaylistItemEntity>> {
                    it.single().videoPath == "/new/folder/clip.mp4" && it.single().position == 0
                }
            )
        }
    }

    @Test
    fun `importPlaylists restores video marker through direct path`() = runTest(dispatcher) {
        val input = markerImportFile(
            videoPath = "/old/folder/clip.mp4",
            fileHash = "direct-hash"
        )
        val video = videoEntity(
            path = "/old/folder/clip.mp4",
            fileHash = "direct-hash"
        )
        every { contentResolver.openInputStream(any<Uri>()) } returns FileInputStream(input)
        coEvery { videoDao.getVideoByPath("/old/folder/clip.mp4") } returns video
        coEvery { videoMarkerDao.getMarkersForVideo("/old/folder/clip.mp4") } returns emptyList()
        coEvery { videoMarkerDao.insert(any()) } returns 9L

        val result = buildRepository().importPlaylists("content://backup/import")

        assertEquals(1, result.songsFixed)
        coVerify {
            videoMarkerDao.insert(
                match {
                    it.videoPath == "/old/folder/clip.mp4" &&
                        it.fileHash == "direct-hash" &&
                        it.positionMs == 12_000L
                }
            )
        }
    }

    @Test
    fun `importPlaylists restores video marker through file hash when path changed`() = runTest(dispatcher) {
        val input = markerImportFile(
            videoPath = "/old/folder/clip.mp4",
            fileHash = "stable-hash"
        )
        val video = videoEntity(
            path = "/new/folder/clip.mp4",
            fileHash = "stable-hash"
        )
        every { contentResolver.openInputStream(any<Uri>()) } returns FileInputStream(input)
        coEvery { videoDao.getVideoByPath("/old/folder/clip.mp4") } returns null
        coEvery { videoDao.findVideoByHash("stable-hash") } returns video
        coEvery { videoMarkerDao.getMarkersForVideo("/new/folder/clip.mp4") } returns emptyList()
        coEvery { videoMarkerDao.insert(any()) } returns 10L

        val result = buildRepository().importPlaylists("content://backup/import")

        assertEquals(1, result.songsFixed)
        coVerify {
            videoMarkerDao.insert(
                match {
                    it.videoPath == "/new/folder/clip.mp4" &&
                        it.fileHash == "stable-hash" &&
                        it.positionMs == 12_000L
                }
            )
        }
    }

    @Test
    fun `importPlaylists restores video marker through filename size and duration fallback`() = runTest(dispatcher) {
        val input = markerImportFile(
            videoPath = "/old/folder/clip.mp4",
            fileHash = null
        )
        val video = videoEntity(
            path = "/new/folder/clip.mp4",
            fileHash = null
        )
        every { contentResolver.openInputStream(any<Uri>()) } returns FileInputStream(input)
        coEvery { videoDao.getVideoByPath("/old/folder/clip.mp4") } returns null
        coEvery {
            videoDao.findVideoByFilenameSizeAndDuration("clip.mp4", 1234L, 30_000L)
        } returns video
        coEvery { videoMarkerDao.getMarkersForVideo("/new/folder/clip.mp4") } returns emptyList()
        coEvery { videoMarkerDao.insert(any()) } returns 11L

        val result = buildRepository().importPlaylists("content://backup/import")

        assertEquals(1, result.songsFixed)
        coVerify {
            videoMarkerDao.insert(match { it.videoPath == "/new/folder/clip.mp4" })
        }
    }

    @Test
    fun `importPlaylists skips duplicate video marker within one second`() = runTest(dispatcher) {
        val input = markerImportFile(
            videoPath = "/old/folder/clip.mp4",
            fileHash = "stable-hash",
            positionMs = 12_000L
        )
        val video = videoEntity(
            path = "/new/folder/clip.mp4",
            fileHash = "stable-hash"
        )
        every { contentResolver.openInputStream(any<Uri>()) } returns FileInputStream(input)
        coEvery { videoDao.getVideoByPath("/old/folder/clip.mp4") } returns null
        coEvery { videoDao.findVideoByHash("stable-hash") } returns video
        coEvery { videoMarkerDao.getMarkersForVideo("/new/folder/clip.mp4") } returns listOf(
            VideoMarkerEntity(
                id = 3L,
                videoPath = "/new/folder/clip.mp4",
                fileHash = "stable-hash",
                filename = "clip.mp4",
                fileSize = 1234L,
                durationMs = 30_000L,
                positionMs = 12_500L,
                createdAt = 1L,
                updatedAt = 1L
            )
        )

        val result = buildRepository().importPlaylists("content://backup/import")

        assertEquals(0, result.songsFixed)
        coVerify(exactly = 0) { videoMarkerDao.insert(any()) }
    }

    private fun markerImportFile(
        videoPath: String,
        fileHash: String?,
        positionMs: Long = 12_000L
    ): File {
        return File.createTempFile("marker-import", ".json").apply {
            writeText(
                """
                {
                  "version": 4,
                  "playlists": [],
                  "videoPlaylists": [],
                  "videoMarkers": [
                    {
                      "videoPath": "$videoPath",
                      "fileHash": ${fileHash?.let { "\"$it\"" } ?: "null"},
                      "filename": "clip.mp4",
                      "fileSize": 1234,
                      "durationMs": 30000,
                      "positionMs": $positionMs,
                      "createdAt": 20,
                      "updatedAt": 30
                    }
                  ]
                }
                """.trimIndent()
            )
            deleteOnExit()
        }
    }

    private fun videoEntity(
        path: String,
        fileHash: String?
    ): VideoEntity {
        return VideoEntity(
            path = path,
            contentUri = "content://video/clip",
            title = "clip",
            duration = 30_000L,
            thumbnailUri = null,
            parentPath = File(path).parent.orEmpty(),
            filename = File(path).name,
            lastModified = 0L,
            size = 1234L,
            width = 1920,
            height = 1080,
            fileHash = fileHash
        )
    }
}
