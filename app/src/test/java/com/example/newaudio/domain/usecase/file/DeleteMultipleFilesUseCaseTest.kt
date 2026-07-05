package com.example.newaudio.domain.usecase.file

import android.app.Application
import android.content.ContentResolver
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.fake.FakeSettingsRepository
import com.example.newaudio.fake.FakeVideoMarkerRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DeleteMultipleFilesUseCaseTest {

    private val songDao = mockk<SongDao>(relaxed = true)
    private val videoDao = mockk<VideoDao>(relaxed = true)
    private val application = mockk<Application>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val settingsRepository = FakeSettingsRepository()
    private val videoMarkerRepository = FakeVideoMarkerRepository()

    @Test
    fun `deleting multiple video files removes video entries in batch`() = runTest {
        every { application.contentResolver } returns contentResolver
        val tempDir = Files.createTempDirectory("newaudio-delete-multiple-video").toFile()
        val first = File(tempDir, "first.mp4").apply { writeText("video") }
        val second = File(tempDir, "second.mkv").apply { writeText("video") }
        val items = listOf(videoFileItem(first), videoFileItem(second))
        val useCase = buildUseCase()

        try {
            val result = useCase(tempDir.absolutePath, items)

            assertTrue(result)
            coVerify(exactly = 1) {
                videoDao.deleteByPaths(listOf(first.absolutePath, second.absolutePath))
            }
            coVerify(exactly = 0) { songDao.deleteByPaths(any()) }
            assertTrue(videoMarkerRepository.deletedVideos.contains(first.absolutePath))
            assertTrue(videoMarkerRepository.deletedVideos.contains(second.absolutePath))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `deleting multiple folders removes audio and video entries below folders`() = runTest {
        every { application.contentResolver } returns contentResolver
        val tempDir = Files.createTempDirectory("newaudio-delete-multiple-folder").toFile()
        val firstFolder = File(tempDir, "first").apply {
            mkdirs()
            File(this, "clip.mp4").writeText("video")
            val nested = File(this, "nested").apply { mkdirs() }
            File(nested, "deep-clip.mkv").writeText("video")
        }
        val secondFolder = File(tempDir, "second").apply {
            mkdirs()
            File(this, "song.mp3").writeText("audio")
            val nested = File(this, "nested").apply { mkdirs() }
            File(nested, "deep-song.flac").writeText("audio")
        }
        val items = listOf(
            FileItem.Folder(firstFolder.name, firstFolder.absolutePath),
            FileItem.Folder(secondFolder.name, secondFolder.absolutePath)
        )
        val useCase = buildUseCase()

        try {
            val result = useCase(tempDir.absolutePath, items)

            assertTrue(result)
            assertTrue(!firstFolder.exists())
            assertTrue(!secondFolder.exists())
            coVerify(exactly = 1) { songDao.deleteByFolder(firstFolder.absolutePath) }
            coVerify(exactly = 1) { videoDao.deleteByFolder(firstFolder.absolutePath) }
            coVerify(exactly = 1) { songDao.deleteByFolder(secondFolder.absolutePath) }
            coVerify(exactly = 1) { videoDao.deleteByFolder(secondFolder.absolutePath) }
            assertTrue(videoMarkerRepository.deletedFolders.contains(firstFolder.absolutePath))
            assertTrue(videoMarkerRepository.deletedFolders.contains(secondFolder.absolutePath))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun buildUseCase(): DeleteMultipleFilesUseCase {
        return DeleteMultipleFilesUseCase(
            songDao = songDao,
            videoDao = videoDao,
            application = application,
            getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepository),
            videoMarkerRepository = videoMarkerRepository
        )
    }

    @Test
    fun `deleting multiple audio files removes song entries in batch`() = runTest {
        every { application.contentResolver } returns contentResolver
        val tempDir = Files.createTempDirectory("newaudio-delete-multiple-audio").toFile()
        val first = File(tempDir, "first.mp3").apply { writeText("audio") }
        val second = File(tempDir, "second.flac").apply { writeText("audio") }
        val items = listOf(audioFileItem(first), audioFileItem(second))
        val useCase = buildUseCase()

        try {
            val result = useCase(tempDir.absolutePath, items)

            assertTrue(result)
            coVerify(exactly = 1) {
                songDao.deleteByPaths(listOf(first.absolutePath, second.absolutePath))
            }
            coVerify(exactly = 0) { videoDao.deleteByPaths(any()) }
            assertTrue(videoMarkerRepository.deletedVideos.isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun videoFileItem(file: File): FileItem.VideoFile {
        val path = file.absolutePath
        val video = Video(
            path = path,
            contentUri = "content://media/external/video/media/${path.hashCode().toLong()}",
            title = file.nameWithoutExtension,
            duration = 1_000L,
            thumbnailUri = null,
            width = 1920,
            height = 1080
        )
        return FileItem.VideoFile(
            name = file.name,
            path = path,
            videoId = path.hashCode().toLong(),
            video = video
        )
    }

    private fun audioFileItem(file: File): FileItem.AudioFile {
        val path = file.absolutePath
        val song = Song(
            path = path,
            contentUri = path,
            title = file.nameWithoutExtension,
            artist = "Artist",
            duration = 1_000L,
            albumArtPath = null
        )
        return FileItem.AudioFile(
            name = file.name,
            path = path,
            songId = path.hashCode().toLong(),
            song = song
        )
    }
}
