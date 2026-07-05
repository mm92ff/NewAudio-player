package com.example.newaudio.data.repository

import androidx.room.Room
import com.example.newaudio.data.database.AppDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class MediaScannerRepositoryImplTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `scanSingleVideoFile falls back to filename when metadata title is missing`() = runTest(dispatcher) {
        val root = Files.createTempDirectory("video-scan-root").toFile().apply { deleteOnExit() }
        val copiedVideo = File(root, "Copied Fight Clip.mp4").apply {
            writeBytes(byteArrayOf(0, 1, 2, 3))
            deleteOnExit()
        }

        buildRepository().scanSingleVideoFile(copiedVideo.absolutePath)
        dispatcher.scheduler.advanceUntilIdle()

        val video = database.videoDao().getVideoByPath(copiedVideo.absolutePath)
        assertEquals("Copied Fight Clip", video?.title)
    }

    @Test
    fun `scanSingleFile falls back to filename when metadata title is missing`() = runTest(dispatcher) {
        val root = Files.createTempDirectory("audio-scan-root").toFile().apply { deleteOnExit() }
        val copiedSong = File(root, "Copied Audio Clip.mp3").apply {
            writeBytes(byteArrayOf(0, 1, 2, 3))
            deleteOnExit()
        }

        buildRepository().scanSingleFile(copiedSong.absolutePath)
        dispatcher.scheduler.advanceUntilIdle()

        val song = database.songDao().getSongByPath(copiedSong.absolutePath)
        assertEquals("Copied Audio Clip", song?.title)
    }

    private fun buildRepository(): MediaScannerRepositoryImpl {
        return MediaScannerRepositoryImpl(
            context = RuntimeEnvironment.getApplication(),
            appDatabase = database,
            songDao = database.songDao(),
            videoDao = database.videoDao(),
            ioDispatcher = dispatcher
        )
    }
}
