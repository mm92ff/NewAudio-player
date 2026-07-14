package com.example.newaudio.data.media.mapping

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Media3ItemMapperTest {
    private val mapper = Media3ItemMapper()

    @Test
    fun `song round trip keeps identity uri metadata and explicit type`() {
        val song = Song("/music/a.mp3", "content://songs/1", "A", "Artist", 99L, null)

        val item = mapper.toMediaItem(song)
        val fallback = mapper.toSong(item)

        assertEquals(song.path, item.mediaId)
        assertEquals(song.contentUri, item.localConfiguration?.uri?.toString())
        assertEquals(Media3ItemMapper.MediaType.AUDIO, mapper.declaredMediaType(item))
        assertEquals(song.title, fallback.title)
        assertEquals(song.artist, fallback.artist)
    }

    @Test
    fun `video has explicit type and extension lookup is case insensitive`() {
        val video = Video("/video/a.MP4", "content://videos/1", "Video", 20L, null)
        val item = mapper.toMediaItem(video)

        assertEquals(Media3ItemMapper.MediaType.VIDEO, mapper.declaredMediaType(item))
        assertEquals(Media3ItemMapper.MediaType.VIDEO, mapper.extensionMediaType(video.path))
        assertTrue(mapper.isVideo(item))
        assertNull(mapper.extensionMediaType("/files/readme.bin"))
    }

    @Test
    fun `blank title falls back to file name`() {
        val item = mapper.toMediaItem(
            Song("/music/fallback.mp3", "", "", "Artist", 1L, null)
        )

        assertEquals("fallback", item.mediaMetadata.title.toString())
        assertEquals("/music/fallback.mp3", item.localConfiguration?.uri?.toString())
    }

    @Test
    fun `library model has precedence over incomplete media item metadata`() {
        val stored = Song(
            "/music/stored.mp3",
            "content://stored",
            "Stored title",
            "Stored artist",
            123L,
            "content://art"
        )
        val item = androidx.media3.common.MediaItem.Builder()
            .setMediaId(stored.path)
            .build()

        assertEquals(stored, mapper.toSong(item, stored))
    }

    @Test
    fun `known audio extension is detected without metadata extras`() {
        assertEquals(
            Media3ItemMapper.MediaType.AUDIO,
            mapper.extensionMediaType("/MUSIC/TRACK.FLAC")
        )
    }

    @Test
    fun `explicit audio type wins over video extension`() {
        val item = mapper.toMediaItem(
            Song("/music/misnamed.mp4", "content://song", "Song", "Artist", 1L, null)
        )

        assertEquals(Media3ItemMapper.MediaType.AUDIO, mapper.mediaType(item))
        assertFalse(mapper.isVideo(item))
    }

    @Test
    fun `explicit video type wins over audio extension`() {
        val item = mapper.toMediaItem(
            Video("/video/misnamed.mp3", "content://video", "Video", 1L, null)
        )

        assertEquals(Media3ItemMapper.MediaType.VIDEO, mapper.mediaType(item))
        assertTrue(mapper.isVideo(item))
    }

    @Test
    fun `unknown metadata falls back to extension and unknown extension stays unknown`() {
        val video = androidx.media3.common.MediaItem.Builder()
            .setMediaId("/video/fallback.mp4")
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setExtras(android.os.Bundle().apply {
                        putString(Media3ItemMapper.MEDIA_TYPE_KEY, "other")
                    })
                    .build()
            )
            .build()
        val unknown = androidx.media3.common.MediaItem.Builder()
            .setMediaId("/files/item.bin")
            .build()

        assertEquals(Media3ItemMapper.MediaType.VIDEO, mapper.mediaType(video))
        assertNull(mapper.mediaType(unknown))
    }
}
