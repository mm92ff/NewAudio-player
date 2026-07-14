package com.example.newaudio.data.media.playback

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueStateTest {
    @Test
    fun `switching media type clears the other queue`() {
        val state = PlaybackQueueState()
        state.setMusic(listOf(song()), "/music")
        state.setVideos(listOf(video()), "/video")

        val snapshot = state.snapshot()
        assertTrue(snapshot.songs.isEmpty())
        assertEquals(listOf(video()), snapshot.videos)
        assertEquals("/video", snapshot.folderPath)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `replace rejects simultaneous audio and video queues`() {
        PlaybackQueueState().replace(listOf(song()), listOf(video()), null)
    }

    private fun song() = Song("/music/a.mp3", "content://a", "A", "Artist", 1L, null)
    private fun video() = Video("/video/a.mp4", "content://v", "V", 1L, null)
}
