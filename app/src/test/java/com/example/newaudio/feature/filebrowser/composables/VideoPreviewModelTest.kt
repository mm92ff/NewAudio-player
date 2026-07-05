package com.example.newaudio.feature.filebrowser.composables

import com.example.newaudio.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoPreviewModelTest {

    @Test
    fun `previewModel prefers thumbnail uri`() {
        val video = video(
            thumbnailUri = "content://thumb",
            contentUri = "content://video",
            path = "/video/a.mp4"
        )

        assertEquals("content://thumb", video.previewModel())
    }

    @Test
    fun `previewModel falls back to content uri`() {
        val video = video(
            thumbnailUri = null,
            contentUri = "content://video",
            path = "/video/a.mp4"
        )

        assertEquals("content://video", video.previewModel())
    }

    @Test
    fun `previewModel falls back to file path`() {
        val video = video(
            thumbnailUri = "",
            contentUri = "",
            path = "/video/a.mp4"
        )

        assertEquals("/video/a.mp4", video.previewModel())
    }

    @Test
    fun `previewModel returns null when no source is available`() {
        val video = video(
            thumbnailUri = "",
            contentUri = "",
            path = ""
        )

        assertNull(video.previewModel())
    }

    @Test
    fun `previewCacheKey is derived from selected preview model`() {
        val video = video(
            thumbnailUri = null,
            contentUri = "content://video",
            path = "/video/a.mp4"
        )

        assertEquals("video-preview:content://video", video.previewCacheKey())
    }

    @Test
    fun `previewCacheKey returns null when no source is available`() {
        val video = video(
            thumbnailUri = "",
            contentUri = "",
            path = ""
        )

        assertNull(video.previewCacheKey())
    }

    @Test
    fun `isPortrait returns true when height is greater than width`() {
        val video = video(
            thumbnailUri = null,
            contentUri = "content://video",
            path = "/video/portrait.mp4",
            width = 1080,
            height = 1920
        )

        assertEquals(true, video.isPortrait())
    }

    @Test
    fun `isPortrait returns false for landscape videos`() {
        val video = video(
            thumbnailUri = null,
            contentUri = "content://video",
            path = "/video/landscape.mp4",
            width = 1920,
            height = 1080
        )

        assertEquals(false, video.isPortrait())
    }

    @Test
    fun `isPortrait returns false when dimensions are unknown`() {
        val video = video(
            thumbnailUri = null,
            contentUri = "content://video",
            path = "/video/unknown.mp4",
            width = 0,
            height = 0
        )

        assertEquals(false, video.isPortrait())
    }

    private fun video(
        thumbnailUri: String?,
        contentUri: String,
        path: String,
        width: Int = 1920,
        height: Int = 1080
    ): Video {
        return Video(
            path = path,
            contentUri = contentUri,
            title = "Video",
            duration = 1_000L,
            thumbnailUri = thumbnailUri,
            width = width,
            height = height
        )
    }
}
