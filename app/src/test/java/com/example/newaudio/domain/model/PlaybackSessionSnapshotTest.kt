package com.example.newaudio.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionSnapshotTest {

    private val songA = Song(
        path = "/music/a.mp3",
        contentUri = "content://music/a",
        title = "Song A",
        artist = "Artist",
        duration = 120_000L,
        albumArtPath = null
    )
    private val songB = songA.copy(
        path = "/music/b.mp3",
        contentUri = "content://music/b",
        title = "Song B"
    )
    private val videoA = Video(
        path = "/video/a.mp4",
        contentUri = "content://video/a",
        title = "Video A",
        duration = 180_000L,
        thumbnailUri = null,
        width = 1920,
        height = 1080
    )
    private val videoB = videoA.copy(
        path = "/video/b.mp4",
        contentUri = "content://video/b",
        title = "Video B"
    )

    @Test
    fun `music snapshot resumes matching clicked song at saved position`() {
        val snapshot = PlaybackSessionSnapshot.MusicSession(
            songs = listOf(songA, songB),
            currentIndex = 1,
            currentPath = songB.path,
            positionMs = 42_000L,
            folderPath = "/music",
            wasPlaying = true
        )

        val start = snapshot.resolveMusicSessionStart(
            songs = listOf(songA, songB),
            requestedIndex = 1,
            folderPath = "/music"
        )

        assertEquals(1, start.index)
        assertEquals(42_000L, start.positionMs)
        assertTrue(start.usedSnapshot)
    }

    @Test
    fun `music snapshot does not resume different clicked song`() {
        val snapshot = PlaybackSessionSnapshot.MusicSession(
            songs = listOf(songA, songB),
            currentIndex = 1,
            currentPath = songB.path,
            positionMs = 42_000L,
            folderPath = "/music",
            wasPlaying = true
        )

        val start = snapshot.resolveMusicSessionStart(
            songs = listOf(songA, songB),
            requestedIndex = 0,
            folderPath = "/music"
        )

        assertEquals(0, start.index)
        assertEquals(0L, start.positionMs)
        assertFalse(start.usedSnapshot)
    }

    @Test
    fun `music snapshot falls back to requested index when folder differs`() {
        val snapshot = PlaybackSessionSnapshot.MusicSession(
            songs = listOf(songA),
            currentIndex = 0,
            currentPath = songA.path,
            positionMs = 15_000L,
            folderPath = "/music/old",
            wasPlaying = true
        )

        val start = snapshot.resolveMusicSessionStart(
            songs = listOf(songA),
            requestedIndex = 0,
            folderPath = "/music/new"
        )

        assertEquals(0, start.index)
        assertEquals(0L, start.positionMs)
        assertFalse(start.usedSnapshot)
    }

    @Test
    fun `video snapshot resumes matching clicked video at saved position`() {
        val snapshot = PlaybackSessionSnapshot.VideoSession(
            videos = listOf(videoA, videoB),
            currentIndex = 0,
            currentPath = videoA.path,
            positionMs = 91_000L,
            folderPath = "/video",
            wasPlaying = true
        )

        val start = snapshot.resolveVideoSessionStart(
            videos = listOf(videoA, videoB),
            requestedIndex = 0,
            folderPath = "/video"
        )

        assertEquals(0, start.index)
        assertEquals(91_000L, start.positionMs)
        assertTrue(start.usedSnapshot)
    }

    @Test
    fun `video snapshot clamps invalid requested index`() {
        val start = (null as PlaybackSessionSnapshot.VideoSession?).resolveVideoSessionStart(
            videos = listOf(videoA, videoB),
            requestedIndex = 99,
            folderPath = "/video"
        )

        assertEquals(1, start.index)
        assertEquals(0L, start.positionMs)
        assertFalse(start.usedSnapshot)
    }

    @Test
    fun `empty video playlist returns zero start without snapshot`() {
        val start = (null as PlaybackSessionSnapshot.VideoSession?).resolveVideoSessionStart(
            videos = emptyList(),
            requestedIndex = 5,
            folderPath = "/video"
        )

        assertEquals(0, start.index)
        assertEquals(0L, start.positionMs)
        assertFalse(start.usedSnapshot)
    }
}
