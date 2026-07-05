package com.example.newaudio.data.database

import com.example.newaudio.data.database.dao.PlaylistSongResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SongEntityTest {

    @Test
    fun `toDomainModel falls back to filename when title is blank`() {
        val entity = SongEntity(
            path = "/storage/emulated/0/Music/Sub Folder/Audio Clip.mp3",
            contentUri = "content://audio/clip",
            title = "",
            artist = "Artist",
            album = "Album",
            duration = 1_000L,
            albumArtPath = null,
            parentPath = "/storage/emulated/0/Music/Sub Folder",
            filename = "Audio Clip.mp3",
            lastModified = 123L,
            size = 456L
        )

        assertEquals("Audio Clip", entity.toDomainModel().title)
    }

    @Test
    fun `playlist song result falls back to filename when title is blank`() {
        val result = PlaylistSongResult(
            path = "/storage/emulated/0/Music/Sub Folder/Playlist Audio.mp3",
            contentUri = "content://audio/playlist",
            title = "",
            artist = "Artist",
            duration = 1_000L,
            albumArtPath = null,
            position = 0
        )

        assertEquals("Playlist Audio", result.toDomainModel().title)
    }
}
