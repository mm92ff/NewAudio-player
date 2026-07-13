package com.example.newaudio.domain.usecase.file

import android.app.Application
import com.example.newaudio.data.database.DatabaseTransactionRunner
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.fake.FakeSettingsRepository
import com.example.newaudio.fake.FakeVideoMarkerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MoveMultipleFilesUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val songDao = mockk<SongDao>(relaxed = true)
    private val videoDao = mockk<VideoDao>(relaxed = true)
    private val application = mockk<Application>(relaxed = true) {
        every { contentResolver.persistedUriPermissions } returns emptyList()
    }
    private val settingsRepository = FakeSettingsRepository()
    private val videoMarkerRepository = FakeVideoMarkerRepository()
    private val transactionRunner = object : DatabaseTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    private fun useCase() = MoveMultipleFilesUseCase(
        songDao = songDao,
        videoDao = videoDao,
        storage = SafeStorageOperations(application),
        getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepository),
        videoMarkerRepository = videoMarkerRepository,
        transactionRunner = transactionRunner
    )

    @Test
    fun `moving video file updates video dao path`() = runTest {
        val sourceParent = tempFolder.newFolder("source")
        val targetParent = tempFolder.newFolder("target")
        val source = File(sourceParent, "fight.mp4").apply { writeText("video") }
        val item = videoFileItem(source)

        val result = useCase()(listOf(item), sourceParent.absolutePath, targetParent.absolutePath)

        val moved = File(targetParent, source.name)
        assertTrue(result.isSuccess)
        assertTrue(moved.exists())
        coVerify(exactly = 1) {
            videoDao.updatePath(
                oldPath = source.absolutePath,
                newPath = moved.absolutePath,
                newContentUri = moved.absolutePath,
                newParentPath = targetParent.absolutePath,
                newFilename = source.name
            )
        }
        coVerify(exactly = 0) { songDao.updatePath(any(), any(), any(), any(), any()) }
        assertTrue(videoMarkerRepository.updatedVideoPaths.contains(source.absolutePath to moved.absolutePath))
    }

    @Test
    fun `moving audio file still updates song dao path`() = runTest {
        val sourceParent = tempFolder.newFolder("source")
        val targetParent = tempFolder.newFolder("target")
        val source = File(sourceParent, "track.mp3").apply { writeText("audio") }
        val item = audioFileItem(source)

        val result = useCase()(listOf(item), sourceParent.absolutePath, targetParent.absolutePath)

        val moved = File(targetParent, source.name)
        assertTrue(result.isSuccess)
        assertTrue(moved.exists())
        coVerify(exactly = 1) {
            songDao.updatePath(
                oldPath = source.absolutePath,
                newPath = moved.absolutePath,
                newContentUri = moved.absolutePath,
                newParentPath = targetParent.absolutePath,
                newFilename = source.name
            )
        }
        coVerify(exactly = 0) { videoDao.updatePath(any(), any(), any(), any(), any()) }
        assertTrue(videoMarkerRepository.updatedVideoPaths.isEmpty())
    }

    @Test
    fun `moving folder preserves indexed rows instead of deleting and rescanning`() = runTest {
        val sourceParent = tempFolder.newFolder("source-parent")
        val targetParent = tempFolder.newFolder("target-parent")
        val sourceFolder = File(sourceParent, "folder").apply { mkdirs() }
        File(sourceFolder, "track.mp3").writeText("audio")
        File(sourceFolder, "clip.mp4").writeText("video")
        val nestedFolder = File(sourceFolder, "nested").apply { mkdirs() }
        File(nestedFolder, "deep-track.flac").writeText("audio")
        File(nestedFolder, "deep-clip.mkv").writeText("video")
        val item = FileItem.Folder(
            name = sourceFolder.name,
            path = sourceFolder.absolutePath,
            mediaCount = 0
        )

        val result = useCase()(listOf(item), sourceParent.absolutePath, targetParent.absolutePath)

        val movedFolder = File(targetParent, sourceFolder.name)
        assertTrue(result.isSuccess)
        assertTrue(movedFolder.exists())
        assertFalse(sourceFolder.exists())
        assertTrue(File(movedFolder, "track.mp3").exists())
        assertTrue(File(movedFolder, "clip.mp4").exists())
        assertTrue(File(movedFolder, "nested/deep-track.flac").exists())
        assertTrue(File(movedFolder, "nested/deep-clip.mkv").exists())
        coVerify(exactly = 0) { songDao.deleteByFolder(any()) }
        coVerify(exactly = 0) { videoDao.deleteByFolder(any()) }
    }

    @Test
    fun `move returns false when destination exists`() = runTest {
        val sourceParent = tempFolder.newFolder("source")
        val targetParent = tempFolder.newFolder("target")
        val source = File(sourceParent, "fight.mp4").apply { writeText("video") }
        File(targetParent, source.name).writeText("existing")
        val item = videoFileItem(source)

        val result = useCase()(listOf(item), sourceParent.absolutePath, targetParent.absolutePath)

        assertFalse(result.isSuccess)
        assertEquals(FileOperationFailureReason.DESTINATION_EXISTS, result.failures.single().reason)
        coVerify(exactly = 0) { videoDao.updatePath(any(), any(), any(), any(), any()) }
        assertTrue(videoMarkerRepository.updatedVideoPaths.isEmpty())
    }

    @Test
    fun `database failure rolls physical rename back`() = runTest {
        val sourceParent = tempFolder.newFolder("source-db-failure")
        val targetParent = tempFolder.newFolder("target-db-failure")
        val source = File(sourceParent, "track.mp3").apply { writeText("audio") }
        coEvery { songDao.updatePath(any(), any(), any(), any(), any()) } throws IllegalStateException("db")

        val result = useCase()(listOf(audioFileItem(source)), sourceParent.absolutePath, targetParent.absolutePath)

        assertFalse(result.isSuccess)
        assertEquals(FileOperationFailureReason.DATABASE_UPDATE_FAILED, result.failures.single().reason)
        assertTrue(source.exists())
        assertFalse(File(targetParent, source.name).exists())
    }

    @Test
    fun `source delete failure returns specific partial failure after verified copy`() = runTest {
        val targetParent = tempFolder.newFolder("target-delete-failure")
        val missingSource = File(tempFolder.root, "provider-only.mp3")
        val destination = File(targetParent, missingSource.name)
        val storage = mockk<SafeStorageOperations>()
        every { storage.destinationExists(destination, null) } returns false
        every { storage.copyVerified(missingSource, destination, null) } returns
            SafeStorageOperations.CopyResult(destination, "content://provider/copied")
        every { storage.delete(missingSource, null) } returns false
        val useCase = MoveMultipleFilesUseCase(
            songDao,
            videoDao,
            storage,
            GetUserSettingsUseCase(settingsRepository),
            videoMarkerRepository,
            transactionRunner
        )

        val result = useCase(listOf(audioFileItem(missingSource)), tempFolder.root.absolutePath, targetParent.absolutePath)

        assertFalse(result.isSuccess)
        assertEquals(FileOperationFailureReason.SOURCE_DELETE_FAILED, result.failures.single().reason)
        coVerify {
            songDao.updatePath(
                oldPath = missingSource.absolutePath,
                newPath = destination.absolutePath,
                newContentUri = "content://provider/copied",
                newParentPath = targetParent.absolutePath,
                newFilename = missingSource.name
            )
        }
    }

    private fun videoFileItem(file: File): FileItem.VideoFile {
        val video = Video(
            path = file.absolutePath,
            contentUri = file.toURI().toString(),
            title = file.nameWithoutExtension,
            duration = 1_000L,
            thumbnailUri = null,
            width = 1920,
            height = 1080
        )
        return FileItem.VideoFile(
            name = file.name,
            path = file.absolutePath,
            videoId = file.absolutePath.hashCode().toLong(),
            video = video
        )
    }

    private fun audioFileItem(file: File): FileItem.AudioFile {
        val song = Song(
            path = file.absolutePath,
            contentUri = file.toURI().toString(),
            title = file.nameWithoutExtension,
            artist = "Artist",
            duration = 1_000L,
            albumArtPath = null
        )
        return FileItem.AudioFile(
            name = file.name,
            path = file.absolutePath,
            songId = file.absolutePath.hashCode().toLong(),
            song = song
        )
    }
}
