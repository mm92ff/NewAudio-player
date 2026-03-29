package com.example.newaudio.domain.usecase.player

import com.example.newaudio.domain.model.Song
import com.example.newaudio.fake.FakeMediaRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PlaySongUseCaseTest {

    private val repo = FakeMediaRepository()
    private val useCase = PlaySongUseCase(repo)

    private val songA = Song(
        path = "/music/folder/a.mp3",
        contentUri = "content://a",
        title = "Song A",
        artist = "Artist",
        duration = 180_000L,
        albumArtPath = null
    )
    private val songB = Song(
        path = "/music/folder/b.mp3",
        contentUri = "content://b",
        title = "Song B",
        artist = "Artist",
        duration = 200_000L,
        albumArtPath = null
    )

    @Before
    fun setUp() {
        repo.stubbedParentPath = "/music/folder"
        repo.stubbedSongsInFolder = listOf(songA, songB)
    }

    @Test
    fun `plays full folder starting at clicked song`() = runTest {
        useCase(songA)
        assertEquals(listOf(songA, songB), repo.lastPlaylistPlayed)
        assertEquals(0, repo.lastStartIndex)
    }

    @Test
    fun `start index matches clicked song position in folder`() = runTest {
        useCase(songB)
        assertEquals(1, repo.lastStartIndex)
    }

    @Test
    fun `when parentPath is null plays only the single song`() = runTest {
        repo.stubbedParentPath = null
        useCase(songA)
        assertEquals(listOf(songA), repo.lastPlaylistPlayed)
        assertEquals(0, repo.lastStartIndex)
    }

    @Test
    fun `when folder songs are empty plays only the clicked song`() = runTest {
        repo.stubbedSongsInFolder = emptyList()
        useCase(songA)
        assertEquals(listOf(songA), repo.lastPlaylistPlayed)
    }

    @Test
    fun `when song is not in folder result appends it at the end`() = runTest {
        val songC = songA.copy(path = "/music/folder/c.mp3", contentUri = "content://c")
        repo.stubbedSongsInFolder = listOf(songA, songB) // songC not in folder
        useCase(songC)
        val playlist = repo.lastPlaylistPlayed!!
        assertEquals(songC, playlist.last())
    }

    @Test
    fun `folder path is passed to repository`() = runTest {
        useCase(songA)
        assertEquals("/music/folder", repo.lastFolderPath)
    }
}
