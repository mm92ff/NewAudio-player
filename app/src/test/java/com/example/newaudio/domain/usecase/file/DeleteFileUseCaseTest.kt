package com.example.newaudio.domain.usecase.file

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class DeleteFileUseCaseTest {

    private val songDao = mockk<SongDao>(relaxed = true)
    private val videoDao = mockk<VideoDao>(relaxed = true)
    private val application = mockk<Application>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val settingsRepository = FakeSettingsRepository()
    private val videoMarkerRepository = FakeVideoMarkerRepository()

    @Test
    fun `deleting video file removes video entry from database`() = runTest {
        every { application.contentResolver } returns contentResolver
        val tempDir = Files.createTempDirectory("newaudio-delete-video").toFile()
        val videoFile = File(tempDir, "clip.mp4").apply { writeText("video") }
        val fileItem = videoFileItem(videoFile)
        val useCase = buildUseCase()

        try {
            val result = useCase(tempDir.absolutePath, fileItem)

            assertTrue(result)
            coVerify(exactly = 1) { videoDao.deleteByPath(videoFile.absolutePath) }
            coVerify(exactly = 0) { songDao.deleteByPath(videoFile.absolutePath) }
            assertTrue(videoMarkerRepository.deletedVideos.contains(videoFile.absolutePath))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `deleting folder removes audio and video entries below folder`() = runTest {
        every { application.contentResolver } returns contentResolver
        val tempDir = Files.createTempDirectory("newaudio-delete-folder").toFile()
        File(tempDir, "clip.mp4").writeText("video")
        val nestedFolder = File(tempDir, "nested").apply { mkdirs() }
        File(nestedFolder, "track.mp3").writeText("audio")
        File(nestedFolder, "deep-clip.mkv").writeText("video")
        val folderItem = FileItem.Folder(
            name = tempDir.name,
            path = tempDir.absolutePath
        )
        val useCase = buildUseCase()

        try {
            val result = useCase(requireNotNull(tempDir.parentFile).absolutePath, folderItem)

            assertTrue(result)
            assertTrue(!tempDir.exists())
            coVerify(exactly = 1) { songDao.deleteByFolder(tempDir.absolutePath) }
            coVerify(exactly = 1) { videoDao.deleteByFolder(tempDir.absolutePath) }
            assertTrue(videoMarkerRepository.deletedFolders.contains(tempDir.absolutePath))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun buildUseCase(): DeleteFileUseCase {
        return DeleteFileUseCase(
            songDao = songDao,
            videoDao = videoDao,
            application = application,
            getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepository),
            videoMarkerRepository = videoMarkerRepository
        )
    }

    @Test
    fun `deleting audio file removes song entry from database`() = runTest {
        every { application.contentResolver } returns contentResolver
        val tempDir = Files.createTempDirectory("newaudio-delete-audio").toFile()
        val audioFile = File(tempDir, "track.mp3").apply { writeText("audio") }
        val fileItem = audioFileItem(audioFile)
        val useCase = buildUseCase()

        try {
            val result = useCase(tempDir.absolutePath, fileItem)

            assertTrue(result)
            coVerify(exactly = 1) { songDao.deleteByPath(audioFile.absolutePath) }
            coVerify(exactly = 0) { videoDao.deleteByPath(audioFile.absolutePath) }
            assertTrue(videoMarkerRepository.deletedVideos.isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `folder under video root resolves to video tree`() {
        val musicTree = SafTreeAccess.TreeInfo(
            treeUri = Uri.parse("content://trees/music"),
            treeDocId = "primary:Music",
            baseFsPath = "/storage/emulated/0/Music"
        )
        val videoTree = SafTreeAccess.TreeInfo(
            treeUri = Uri.parse("content://trees/video"),
            treeDocId = "primary:Movies",
            baseFsPath = "/storage/emulated/0/Movies"
        )

        val selectedTree = buildUseCase().treeForItem(
            item = FileItem.Folder(
                name = "sub",
                path = "/storage/emulated/0/Movies/sub"
            ),
            musicTree = musicTree,
            videoTree = videoTree
        )

        assertEquals(videoTree, selectedTree)
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
