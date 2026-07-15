package com.example.newaudio.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.newaudio.data.database.AppDatabase
import com.example.newaudio.data.database.PlaylistEntity
import com.example.newaudio.data.database.RoomDatabaseTransactionRunner
import com.example.newaudio.data.database.SongEntity
import com.example.newaudio.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistBackupImporterIntegrationTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importWritesPlaylistAndResolvedSongThroughRealRoomDaos() = runBlocking {
        database.songDao().insertAll(listOf(songEntity()))
        val importer = importerFor(
            """
            {
              "version": 4,
              "playlists": [
                {
                  "name": "Integration",
                  "createdAt": 20,
                  "songs": [
                    {
                      "path": "/music/a.mp3",
                      "title": "A",
                      "artist": "Artist",
                      "size": 1234,
                      "fileHash": "hash-a"
                    }
                  ]
                }
              ],
              "videoPlaylists": [],
              "videoMarkers": []
            }
            """.trimIndent()
        )

        val result = importer.import("memory://valid")

        assertTrue(result.isSuccess)
        assertEquals(1, result.playlistsImported)
        assertEquals(1, result.songsFound)
        val playlist = database.playlistDao().getAllPlaylists().first().single()
        assertEquals("Integration", playlist.name)
        assertEquals(
            listOf("/music/a.mp3"),
            database.playlistDao().getSongsInPlaylist(playlist.id).first().map { it.path }
        )
    }

    @Test
    fun roomTransactionRunnerRollsBackAllWritesAfterFailure() = runBlocking {
        val runner = RoomDatabaseTransactionRunner(database)

        try {
            runner.run {
                database.playlistDao().insertPlaylist(
                    PlaylistEntity(name = "Must roll back", position = 0, createdAt = 1L)
                )
                throw ForcedRollback()
            }
        } catch (_: ForcedRollback) {
            // Expected: the assertion below verifies the transaction boundary.
        }

        assertTrue(database.playlistDao().getAllPlaylists().first().isEmpty())
    }

    private fun importerFor(json: String) = PlaylistBackupImporter(
        playlistDao = database.playlistDao(),
        videoPlaylistDao = database.videoPlaylistDao(),
        videoDao = database.videoDao(),
        videoMarkerDao = database.videoMarkerDao(),
        transactionRunner = RoomDatabaseTransactionRunner(database),
        source = object : PlaylistBackupSource {
            override fun readText(location: String, maxBytes: Long): String {
                check(json.toByteArray().size <= maxBytes)
                return json
            }
        },
        validator = PlaylistImportValidator(),
        ioDispatcher = Dispatchers.IO
    )

    private fun songEntity() = SongEntity(
        path = "/music/a.mp3",
        contentUri = "content://audio/a",
        title = "A",
        artist = "Artist",
        album = "Album",
        duration = 1_000L,
        albumArtPath = null,
        parentPath = "/music",
        filename = "a.mp3",
        lastModified = 10L,
        size = 1_234L,
        fileHash = "hash-a"
    )

    private class ForcedRollback : RuntimeException()
}
