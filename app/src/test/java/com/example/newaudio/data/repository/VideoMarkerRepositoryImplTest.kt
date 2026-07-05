package com.example.newaudio.data.repository

import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.data.database.VideoEntity
import com.example.newaudio.data.database.VideoMarkerEntity
import com.example.newaudio.data.database.dao.VideoMarkerDao
import com.example.newaudio.domain.model.Video
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoMarkerRepositoryImplTest {

    private val dispatcher = StandardTestDispatcher()
    private val markerDao = mockk<VideoMarkerDao>(relaxed = true)
    private val videoDao = mockk<VideoDao>(relaxed = true)

    private fun repository() = VideoMarkerRepositoryImpl(
        markerDao = markerDao,
        videoDao = videoDao,
        ioDispatcher = dispatcher
    )

    @Test
    fun `observeMarkersForVideo returns empty list for null path`() = runTest(dispatcher) {
        val markers = repository().observeMarkersForVideo(null).first()

        assertTrue(markers.isEmpty())
    }

    @Test
    fun `observeMarkersForVideo maps dao entities to domain markers`() = runTest(dispatcher) {
        every { markerDao.observeMarkersForVideo("/videos/clip.mp4") } returns flowOf(
            listOf(markerEntity(id = 3L, positionMs = 12_000L))
        )

        val markers = repository().observeMarkersForVideo("/videos/clip.mp4").first()

        assertEquals(1, markers.size)
        assertEquals(3L, markers.single().id)
        assertEquals(12_000L, markers.single().positionMs)
    }

    @Test
    fun `addMarker clamps marker position to video duration`() = runTest(dispatcher) {
        val video = video()
        coEvery { videoDao.getVideoByPath(video.path) } returns videoEntity(duration = 30_000L)
        coEvery { markerDao.getMarkersForVideo(video.path) } returns emptyList()
        coEvery { markerDao.insert(any()) } returns 7L

        val marker = repository().addMarker(video, 45_000L)

        assertEquals(7L, marker?.id)
        assertEquals(30_000L, marker?.positionMs)
        coVerify {
            markerDao.insert(match { it.positionMs == 30_000L && it.fileHash == "hash-1" })
        }
    }

    @Test
    fun `addMarker skips duplicate within one second`() = runTest(dispatcher) {
        val video = video()
        coEvery { videoDao.getVideoByPath(video.path) } returns videoEntity(duration = 30_000L)
        coEvery { markerDao.getMarkersForVideo(video.path) } returns listOf(
            markerEntity(id = 4L, positionMs = 12_500L)
        )

        val marker = repository().addMarker(video, 12_000L)

        assertEquals(4L, marker?.id)
        coVerify(exactly = 0) { markerDao.insert(any()) }
    }

    @Test
    fun `moveMarker delegates clamped non-negative position to dao`() = runTest(dispatcher) {
        repository().moveMarker(markerId = 6L, positionMs = -50L)

        coVerify { markerDao.updatePosition(6L, 0L, any()) }
    }

    @Test
    fun `deleteMarker delegates marker id to dao`() = runTest(dispatcher) {
        repository().deleteMarker(6L)

        coVerify { markerDao.deleteById(6L) }
    }

    @Test
    fun `updateVideoPath updates path and filename`() = runTest(dispatcher) {
        repository().updateVideoPath(
            oldPath = "/old/clip.mp4",
            newPath = "/new/renamed.mp4"
        )

        coVerify {
            markerDao.updateVideoPath(
                oldPath = "/old/clip.mp4",
                newPath = "/new/renamed.mp4",
                newFilename = "renamed.mp4",
                updatedAt = any()
            )
        }
    }

    @Test
    fun `updateVideoFolderPath updates markers below old folder`() = runTest(dispatcher) {
        coEvery { markerDao.getAllMarkers() } returns listOf(
            markerEntity(id = 1L, videoPath = "/old/root/a.mp4"),
            markerEntity(id = 2L, videoPath = "/old/root/sub/b.mp4"),
            markerEntity(id = 3L, videoPath = "/other/c.mp4")
        )

        repository().updateVideoFolderPath("/old/root", "/new/root")

        coVerify {
            markerDao.updateVideoPath(
                oldPath = "/old/root/a.mp4",
                newPath = "/new/root/a.mp4",
                newFilename = "a.mp4",
                updatedAt = any()
            )
        }
        coVerify {
            markerDao.updateVideoPath(
                oldPath = "/old/root/sub/b.mp4",
                newPath = "/new/root/sub/b.mp4",
                newFilename = "b.mp4",
                updatedAt = any()
            )
        }
        coVerify(exactly = 0) {
            markerDao.updateVideoPath(
                oldPath = "/other/c.mp4",
                newPath = any(),
                newFilename = any(),
                updatedAt = any()
            )
        }
    }

    private fun video() = Video(
        path = "/videos/clip.mp4",
        contentUri = "content://video/clip",
        title = "clip",
        duration = 30_000L,
        thumbnailUri = null,
        width = 1920,
        height = 1080
    )

    private fun videoEntity(duration: Long = 30_000L) = VideoEntity(
        path = "/videos/clip.mp4",
        contentUri = "content://video/clip",
        title = "clip",
        duration = duration,
        thumbnailUri = null,
        parentPath = "/videos",
        filename = "clip.mp4",
        lastModified = 0L,
        size = 1234L,
        width = 1920,
        height = 1080,
        fileHash = "hash-1"
    )

    private fun markerEntity(
        id: Long = 1L,
        videoPath: String = "/videos/clip.mp4",
        positionMs: Long = 10_000L
    ) = VideoMarkerEntity(
        id = id,
        videoPath = videoPath,
        fileHash = "hash-1",
        filename = videoPath.substringAfterLast('/'),
        fileSize = 1234L,
        durationMs = 30_000L,
        positionMs = positionMs,
        createdAt = 1L,
        updatedAt = 1L
    )
}
