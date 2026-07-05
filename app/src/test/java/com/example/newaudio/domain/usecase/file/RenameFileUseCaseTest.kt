package com.example.newaudio.domain.usecase.file

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.fake.FakeSettingsRepository
import com.example.newaudio.fake.FakeVideoMarkerRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RenameFileUseCaseTest {

    private val songDao = mockk<SongDao>(relaxed = true)
    private val videoDao = mockk<VideoDao>(relaxed = true)
    private val application = mockk<Application>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val settingsRepository = FakeSettingsRepository()
    private val videoMarkerRepository = FakeVideoMarkerRepository()

    @Before
    fun setUp() {
        mockkObject(SafTreeAccess)
    }

    @After
    fun tearDown() {
        unmockkObject(SafTreeAccess)
    }

    @Test
    fun `renaming video updates video marker path`() = runTest {
        val treeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies")
        val tree = SafTreeAccess.TreeInfo(
            treeUri = treeUri,
            treeDocId = "primary:Movies",
            baseFsPath = "/storage/emulated/0/Movies"
        )
        val oldPath = "/storage/emulated/0/Movies/clip.mp4"
        val newPath = "/storage/emulated/0/Movies/renamed.mp4"
        val oldDocUri = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3AMovies/document/primary%3AMovies%2Fclip.mp4"
        )
        val renamedUri = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3AMovies/document/primary%3AMovies%2Frenamed.mp4"
        )
        every { application.contentResolver } returns contentResolver
        every { SafTreeAccess.parseTree(treeUri.toString()) } returns tree
        every { SafTreeAccess.hasPersistedWritePermission(contentResolver, treeUri) } returns true
        every { SafTreeAccess.documentUriForFsPath(tree, oldPath) } returns oldDocUri
        every { SafTreeAccess.queryDisplayName(contentResolver, renamedUri) } returns "renamed.mp4"
        every { SafTreeAccess.normalizeFsPath(oldPath) } returns oldPath
        every { SafTreeAccess.joinFs("/storage/emulated/0/Movies", "renamed.mp4") } returns newPath
        every { SafTreeAccess.renameDocument(contentResolver, oldDocUri, "renamed.mp4") } returns renamedUri
        settingsRepository.setVideoFolderPath(treeUri.toString())

        val result = buildUseCase().invoke(videoFileItem(oldPath), "renamed.mp4")

        assertTrue(result)
        coVerify {
            videoDao.updatePath(
                oldPath = oldPath,
                newPath = newPath,
                newContentUri = newPath,
                newParentPath = "/storage/emulated/0/Movies",
                newFilename = "renamed.mp4"
            )
        }
        assertTrue(videoMarkerRepository.updatedVideoPaths.contains(oldPath to newPath))
    }

    private fun buildUseCase(): RenameFileUseCase {
        return RenameFileUseCase(
            songDao = songDao,
            videoDao = videoDao,
            application = application,
            getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepository),
            videoMarkerRepository = videoMarkerRepository
        )
    }

    private fun videoFileItem(path: String): FileItem.VideoFile {
        val video = Video(
            path = path,
            contentUri = "content://video/clip",
            title = "clip",
            duration = 1_000L,
            thumbnailUri = null,
            width = 1920,
            height = 1080
        )
        return FileItem.VideoFile(
            name = "clip.mp4",
            path = path,
            videoId = 1L,
            video = video
        )
    }
}
