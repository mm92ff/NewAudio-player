package com.example.newaudio.data.repository

import com.example.newaudio.data.database.PlaylistEntity
import com.example.newaudio.data.database.PlaylistSongEntity
import com.example.newaudio.data.database.dao.PlaylistDao
import com.example.newaudio.data.database.dao.PlaylistSongResult
import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.Song
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistRepositoryImplCrudTest {
    private val dispatcher = StandardTestDispatcher()
    private val dao = mockk<PlaylistDao>(relaxed = true)
    private val repository = PlaylistRepositoryImpl(dao, dispatcher)

    @Test
    fun `create playlist appends after current maximum position`() = runTest(dispatcher) {
        coEvery { dao.getMaxPlaylistPosition() } returns 4
        coEvery { dao.insertPlaylist(any()) } returns 17L

        val id = repository.createPlaylist("Road trip")

        assertEquals(17L, id)
        coVerify {
            dao.insertPlaylist(match { it.name == "Road trip" && it.position == 5 })
        }
    }

    @Test
    fun `create first playlist starts at position zero`() = runTest(dispatcher) {
        coEvery { dao.getMaxPlaylistPosition() } returns null

        repository.createPlaylist("First")

        coVerify { dao.insertPlaylist(match { it.position == 0 }) }
    }

    @Test
    fun `batch add uses one lookup and sequential positions`() = runTest(dispatcher) {
        coEvery { dao.getMaxSongPosition(7L) } returns 2
        val songs = listOf(song("/music/a.mp3"), song("/music/b.mp3"))

        repository.addSongsToPlaylist(7L, songs)

        coVerify(exactly = 1) { dao.getMaxSongPosition(7L) }
        coVerify(exactly = 1) {
            dao.insertPlaylistSongs(
                match {
                    it == listOf(
                        PlaylistSongEntity(7L, "/music/a.mp3", 3),
                        PlaylistSongEntity(7L, "/music/b.mp3", 4)
                    )
                }
            )
        }
    }

    @Test
    fun `song reorder renumbers from zero`() = runTest(dispatcher) {
        repository.updatePlaylistSongsOrder(
            playlistId = 9L,
            songs = listOf(song("/music/b.mp3"), song("/music/a.mp3"))
        )

        coVerify {
            dao.updatePlaylistSongsOrder(
                listOf(
                    PlaylistSongEntity(9L, "/music/b.mp3", 0),
                    PlaylistSongEntity(9L, "/music/a.mp3", 1)
                )
            )
        }
    }

    @Test
    fun `playlist flow maps entities to domain models`() = runTest(dispatcher) {
        every { dao.getAllPlaylists() } returns flowOf(
            listOf(PlaylistEntity(id = 3L, name = "Focus", position = 1, createdAt = 20L))
        )

        assertEquals(
            listOf(Playlist(id = 3L, name = "Focus", position = 1, createdAt = 20L)),
            repository.getAllPlaylists().first()
        )
    }

    @Test
    fun `song flow preserves playlist order and media fields`() = runTest(dispatcher) {
        every { dao.getSongsInPlaylist(3L) } returns flowOf(
            listOf(
                PlaylistSongResult(
                    path = "/music/a.mp3",
                    contentUri = "content://audio/a",
                    title = "A",
                    artist = "Artist",
                    duration = 1_000L,
                    albumArtPath = null,
                    position = 0
                )
            )
        )

        assertEquals(listOf(song("/music/a.mp3")), repository.getSongsInPlaylist(3L).first())
    }

    private fun song(path: String) = Song(
        path = path,
        contentUri = "content://audio/${path.substringAfterLast('/').substringBeforeLast('.')}",
        title = path.substringAfterLast('/').substringBeforeLast('.').uppercase(),
        artist = "Artist",
        duration = 1_000L,
        albumArtPath = null
    )
}
