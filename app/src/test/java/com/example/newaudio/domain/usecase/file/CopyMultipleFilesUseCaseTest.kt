package com.example.newaudio.domain.usecase.file

import android.app.Application
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.fake.FakeMediaScannerRepository
import com.example.newaudio.fake.FakeSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CopyMultipleFilesUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val application = mockk<Application>(relaxed = true) {
        every { contentResolver.persistedUriPermissions } returns emptyList()
    }
    private val settingsRepository = FakeSettingsRepository()
    private val scannerRepository = FakeMediaScannerRepository()

    private fun useCase() = CopyMultipleFilesUseCase(
        application = application,
        getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepository),
        mediaScannerRepository = scannerRepository
    )

    @Test
    fun `copying video file scans copied video`() = runTest {
        val source = tempFolder.newFile("fight.mp4").apply { writeText("video") }
        val target = tempFolder.newFolder("target")
        val item = videoFileItem(source)

        val result = useCase()(listOf(item), target.absolutePath)

        val copied = File(target, source.name)
        assertTrue(result)
        assertTrue(copied.exists())
        assertEquals(copied.absolutePath, scannerRepository.scanSingleVideoFileCalled)
        assertNull(scannerRepository.scanSingleFileCalled)
    }

    @Test
    fun `copying audio file still scans copied audio`() = runTest {
        val source = tempFolder.newFile("track.mp3").apply { writeText("audio") }
        val target = tempFolder.newFolder("target")
        val item = audioFileItem(source)

        val result = useCase()(listOf(item), target.absolutePath)

        val copied = File(target, source.name)
        assertTrue(result)
        assertTrue(copied.exists())
        assertEquals(copied.absolutePath, scannerRepository.scanSingleFileCalled)
        assertNull(scannerRepository.scanSingleVideoFileCalled)
    }

    @Test
    fun `copying folder scans audio and video files recursively`() = runTest {
        val sourceFolder = tempFolder.newFolder("source")
        File(sourceFolder, "track.mp3").writeText("audio")
        File(sourceFolder, "clip.mp4").writeText("video")
        val nestedFolder = File(sourceFolder, "nested").apply { mkdirs() }
        File(nestedFolder, "deep-track.flac").writeText("audio")
        File(nestedFolder, "deep-clip.mkv").writeText("video")
        val target = tempFolder.newFolder("target")
        val item = FileItem.Folder(
            name = sourceFolder.name,
            path = sourceFolder.absolutePath,
            mediaCount = 0
        )

        val result = useCase()(listOf(item), target.absolutePath)

        val copiedFolder = File(target, sourceFolder.name)
        assertTrue(result)
        assertTrue(copiedFolder.exists())
        assertTrue(File(copiedFolder, "track.mp3").exists())
        assertTrue(File(copiedFolder, "clip.mp4").exists())
        assertTrue(File(copiedFolder, "nested/deep-track.flac").exists())
        assertTrue(File(copiedFolder, "nested/deep-clip.mkv").exists())
        assertTrue(scannerRepository.scanSingleFileCalls.contains(File(copiedFolder, "track.mp3").absolutePath))
        assertTrue(scannerRepository.scanSingleFileCalls.contains(File(copiedFolder, "nested/deep-track.flac").absolutePath))
        assertTrue(scannerRepository.scanSingleVideoFileCalls.contains(File(copiedFolder, "clip.mp4").absolutePath))
        assertTrue(scannerRepository.scanSingleVideoFileCalls.contains(File(copiedFolder, "nested/deep-clip.mkv").absolutePath))
    }

    @Test
    fun `copy returns false when destination exists`() = runTest {
        val source = tempFolder.newFile("fight.mp4").apply { writeText("video") }
        val target = tempFolder.newFolder("target")
        File(target, source.name).writeText("existing")
        val item = videoFileItem(source)

        val result = useCase()(listOf(item), target.absolutePath)

        assertFalse(result)
        assertNull(scannerRepository.scanSingleVideoFileCalled)
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
