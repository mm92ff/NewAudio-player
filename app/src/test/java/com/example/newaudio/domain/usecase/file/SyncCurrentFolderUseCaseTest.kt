package com.example.newaudio.domain.usecase.file

import androidx.room.Room
import com.example.newaudio.data.database.AppDatabase
import com.example.newaudio.data.database.VideoEntity
import com.example.newaudio.domain.model.MediaBrowserMode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class SyncCurrentFolderUseCaseTest {

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
    fun `sync repairs moved video content uri when path exists but content uri points to old missing file`() = runTest(dispatcher) {
        val root = Files.createTempDirectory("sync-video-root").toFile().apply { deleteOnExit() }
        val subFolder = File(root, "sub").apply { mkdirs() }
        val movedVideo = File(subFolder, "fight.mp4").apply { writeText("video") }
        val oldVideoPath = File(root, "fight.mp4").absolutePath

        database.videoDao().insert(
            VideoEntity(
                path = movedVideo.absolutePath,
                contentUri = oldVideoPath,
                title = "fight",
                duration = 1_000L,
                thumbnailUri = null,
                parentPath = subFolder.absolutePath,
                filename = movedVideo.name,
                lastModified = movedVideo.lastModified(),
                size = movedVideo.length(),
                width = 1920,
                height = 1080
            )
        )

        buildUseCase().invoke(root.absolutePath, MediaBrowserMode.VIDEO)
        dispatcher.scheduler.advanceUntilIdle()

        val repaired = database.videoDao().getVideoByPath(movedVideo.absolutePath)
        assertEquals(movedVideo.absolutePath, repaired?.contentUri)
    }

    private fun buildUseCase(): SyncCurrentFolderUseCase {
        return SyncCurrentFolderUseCase(
            songDao = database.songDao(),
            videoDao = database.videoDao(),
            appDatabase = database,
            context = RuntimeEnvironment.getApplication(),
            ioDispatcher = dispatcher
        )
    }
}
