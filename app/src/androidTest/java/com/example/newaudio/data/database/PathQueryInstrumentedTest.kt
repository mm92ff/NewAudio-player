package com.example.newaudio.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathQueryInstrumentedTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun percentAndUnderscoreAreTreatedAsLiteralPathCharacters() = runBlocking {
        val dao = database.songDao()
        dao.insertAll(
            listOf(
                song("/music/100%/one.mp3"),
                song("/music/100Hits/two.mp3"),
                song("/music/Mix_1/three.mp3"),
                song("/music/MixA1/four.mp3")
            )
        )

        dao.deleteByFolder("/music/100%")

        assertEquals(
            setOf("/music/100Hits/two.mp3", "/music/Mix_1/three.mp3", "/music/MixA1/four.mp3"),
            dao.getAllPaths().toSet()
        )
        assertEquals(1, dao.getSongCountInTreeFlow("/music/Mix_1").first())
        assertEquals(1, dao.getSongCountInTreeFlow("/music/MixA1").first())
    }

    private fun song(path: String) = SongEntity(
        path = path,
        contentUri = path,
        title = path.substringAfterLast('/'),
        artist = "Artist",
        album = "Album",
        duration = 1_000L,
        albumArtPath = null,
        parentPath = path.substringBeforeLast('/'),
        filename = path.substringAfterLast('/'),
        lastModified = 1L,
        size = 1L
    )
}
