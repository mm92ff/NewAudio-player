package com.example.newaudio.data.database

import com.example.newaudio.data.database.dao.VideoPlaylistVideoResult
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoEntityTest {

    @Test
    fun `toDomainModel falls back to filename when title is blank`() {
        val entity = VideoEntity(
            path = "/storage/emulated/0/Movies/Sub Folder/Fight Clip.mp4",
            contentUri = "content://video/fight",
            title = "",
            duration = 1_000L,
            thumbnailUri = null,
            parentPath = "/storage/emulated/0/Movies/Sub Folder",
            filename = "Fight Clip.mp4",
            lastModified = 123L,
            size = 456L,
            width = 1920,
            height = 1080
        )

        assertEquals("Fight Clip", entity.toDomainModel().title)
    }

    @Test
    fun `video playlist result falls back to filename when title is blank`() {
        val result = VideoPlaylistVideoResult(
            path = "/storage/emulated/0/Movies/Sub Folder/Playlist Clip.mp4",
            contentUri = "content://video/playlist",
            title = "",
            duration = 1_000L,
            thumbnailUri = null,
            size = 456L,
            width = 1920,
            height = 1080,
            position = 0
        )

        assertEquals("Playlist Clip", result.toDomainModel().title)
    }
}
