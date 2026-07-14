package com.example.newaudio.data.media.deletion

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DeletedMediaDecisionCalculatorTest {
    private val calculator = DeletedMediaDecisionCalculator()

    @Test
    fun `blank paths and paths absent from controller are no-ops`() {
        val snapshot = audioSnapshot(listOf(song("a")), currentIndex = 0)

        assertNull(calculator.calculate(listOf("", "   "), snapshot))
        assertNull(calculator.calculate(listOf("/other"), snapshot))
    }

    @Test
    fun `normalization handles windows separators repeats and trailing slash`() {
        val first = Song("C:/Music/A/one.mp3", "content://one", "One", "Artist", 1L, null)
        val sibling = Song("C:/Music/AB/two.mp3", "content://two", "Two", "Artist", 1L, null)
        val snapshot = audioSnapshot(listOf(first, sibling), currentIndex = 0)

        val decision = calculator.calculate(listOf("C:\\Music\\A\\\\"), snapshot)

        assertEquals(listOf(0), decision?.indicesToRemove)
        assertEquals(listOf(sibling), decision?.remainingSongs)
        assertEquals(0, decision?.targetIndex)
        assertEquals(ActiveMediaKind.AUDIO, decision?.deletedActiveMedia)
    }

    @Test
    fun `multiple removals calculate successor index without controller access`() {
        val songs = listOf(song("a"), song("b"), song("c"), song("d"))
        val snapshot = audioSnapshot(songs, currentIndex = 2)

        val decision = calculator.calculate(
            listOf(songs[0].path, songs[2].path),
            snapshot
        )

        assertEquals(listOf(0, 2), decision?.indicesToRemove)
        assertEquals(listOf(songs[1], songs[3]), decision?.remainingSongs)
        assertEquals(1, decision?.targetIndex)
        assertEquals(ActiveMediaKind.AUDIO, decision?.deletedActiveMedia)
    }

    @Test
    fun `deleting current last item selects previous item`() {
        val songs = listOf(song("a"), song("b"), song("c"))

        val decision = calculator.calculate(
            listOf(songs.last().path),
            audioSnapshot(songs, currentIndex = 2)
        )

        assertEquals(1, decision?.targetIndex)
        assertEquals(listOf(songs[0], songs[1]), decision?.remainingSongs)
    }

    @Test
    fun `video mode remains video and matching stays case sensitive`() {
        val video = video("Clip")
        val snapshot = DeletedMediaSnapshot(
            controllerPaths = listOf(video.path),
            songs = emptyList(),
            videos = listOf(video),
            folderPath = "/video",
            originalCurrentIndex = 0,
            currentSongPath = null,
            currentVideoPath = video.path
        )

        assertNull(calculator.calculate(listOf("/VIDEO/CLIP.MP4"), snapshot))
        val decision = calculator.calculate(listOf("/video//Clip.mp4/"), snapshot)

        assertEquals(ActiveMediaKind.VIDEO, decision?.deletedActiveMedia)
        assertEquals(emptyList<Video>(), decision?.remainingVideos)
        assertNull(decision?.targetIndex)
    }

    @Test
    fun `invalid player index resolves active path before choosing replacement`() {
        val songs = listOf(song("a"), song("b"), song("c"))
        val snapshot = DeletedMediaSnapshot(
            controllerPaths = songs.map(Song::path),
            songs = songs,
            videos = emptyList(),
            folderPath = "/music",
            originalCurrentIndex = -1,
            currentSongPath = songs[2].path,
            currentVideoPath = null
        )

        val decision = calculator.calculate(listOf(songs[2].path), snapshot)

        assertEquals(2, decision?.originalCurrentIndex)
        assertEquals(1, decision?.targetIndex)
    }

    @Test
    fun `filesystem and uri roots are rejected as unsafe no ops`() {
        val snapshot = audioSnapshot(listOf(song("a")), currentIndex = 0)

        assertNull(calculator.calculate(listOf("/", "C:\\", "content://"), snapshot))
    }

    @Test
    fun `dot segments are rejected while uri and unc boundaries remain deterministic`() {
        val uriSong = Song(
            "content://library/folder/a.mp3",
            "content://library/folder/a.mp3",
            "URI",
            "Artist",
            1L,
            null
        )
        val uncSong = Song(
            "//server/share/folder/b.mp3",
            "//server/share/folder/b.mp3",
            "UNC",
            "Artist",
            1L,
            null
        )
        val snapshot = audioSnapshot(listOf(uriSong, uncSong), currentIndex = 0)

        assertNull(calculator.calculate(listOf("/music/../folder"), snapshot))
        assertEquals(
            listOf(0),
            calculator.calculate(listOf("content://library/folder"), snapshot)?.indicesToRemove
        )
        assertEquals(
            listOf(1),
            calculator.calculate(listOf("\\\\server\\share\\folder"), snapshot)
                ?.indicesToRemove
        )
    }

    @Test
    fun `snapshot rejects simultaneous audio and video queues`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeletedMediaSnapshot(
                controllerPaths = emptyList(),
                songs = listOf(song("a")),
                videos = listOf(video("a")),
                folderPath = null,
                originalCurrentIndex = 0,
                currentSongPath = null,
                currentVideoPath = null
            )
        }
    }

    private fun audioSnapshot(
        songs: List<Song>,
        currentIndex: Int
    ) = DeletedMediaSnapshot(
        controllerPaths = songs.map(Song::path),
        songs = songs,
        videos = emptyList(),
        folderPath = "/music",
        originalCurrentIndex = currentIndex,
        currentSongPath = songs.getOrNull(currentIndex)?.path,
        currentVideoPath = null
    )

    private fun song(name: String) = Song(
        "/music/$name.mp3",
        "content://$name",
        name,
        "Artist",
        1_000L,
        null
    )

    private fun video(name: String) = Video(
        "/video/$name.mp4",
        "content://$name",
        name,
        1_000L,
        null
    )
}
