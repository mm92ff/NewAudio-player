package com.example.newaudio.data.media.playback

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.repository.IMediaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionCoordinatorTest {
    @Test
    fun `music session is captured when switching to video and consumed once`() {
        val queueState = PlaybackQueueState().apply {
            setMusic(listOf(song("a"), song("b")), "/music")
        }
        val coordinator = PlaybackSessionCoordinator(queueState)
        coordinator.captureBeforeVideoPlayback(
            IMediaRepository.PlaybackState(currentSong = song("b")),
            ControllerPlaybackSnapshot(currentIndex = 1, currentPosition = 123L, playWhenReady = false)
        )

        val session = coordinator.consumeMusicSession()

        assertEquals("/music/b.mp3", session?.currentPath)
        assertEquals(123L, session?.positionMs)
        assertFalse(session?.wasPlaying ?: true)
        assertNull(coordinator.consumeMusicSession())
    }

    @Test
    fun `invalid player index resolves current path while capturing video`() {
        val videos = listOf(video("a"), video("b"))
        val queueState = PlaybackQueueState().apply { setVideos(videos, "/video") }
        val coordinator = PlaybackSessionCoordinator(queueState)
        coordinator.captureBeforeMusicPlayback(
            IMediaRepository.PlaybackState(currentVideo = videos[1]),
            ControllerPlaybackSnapshot(currentIndex = -1, currentPosition = -9L, playWhenReady = true)
        )

        val session = coordinator.peekVideoSession()

        assertEquals(1, session?.currentIndex)
        assertEquals(0L, session?.positionMs)
        assertTrue(session?.wasPlaying == true)
    }

    @Test
    fun `previewing a stored start does not consume a nonmatching snapshot`() {
        val songs = listOf(song("a"), song("b"))
        val queueState = PlaybackQueueState().apply { setMusic(songs, "/music") }
        val coordinator = PlaybackSessionCoordinator(queueState)
        coordinator.captureBeforeVideoPlayback(
            IMediaRepository.PlaybackState(currentSong = songs[0]),
            ControllerPlaybackSnapshot(0, 77L, true)
        )

        val stored = coordinator.peekMusicSession()
        val start = coordinator.previewMusicStart(songs, requestedIndex = 1, folderPath = "/music")

        assertEquals(1, start.index)
        assertEquals(0L, start.positionMs)
        assertFalse(start.usedSnapshot)
        assertEquals(stored, coordinator.peekMusicSession())
        assertTrue(coordinator.consumeMusicSession(stored))
        assertNull(coordinator.peekMusicSession())
    }

    private fun song(name: String) = Song(
        "/music/$name.mp3", "content://$name", name, "Artist", 1_000L, null
    )

    private fun video(name: String) = Video(
        "/video/$name.mp4", "content://$name", name, 1_000L, null
    )
}
