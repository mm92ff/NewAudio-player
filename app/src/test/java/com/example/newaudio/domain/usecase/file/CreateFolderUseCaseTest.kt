package com.example.newaudio.domain.usecase.file

import android.app.Application
import android.net.Uri
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.fake.FakeSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CreateFolderUseCaseTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val application = mockk<Application>(relaxed = true)
    private val settingsRepository = FakeSettingsRepository()

    private fun buildUseCase(): CreateFolderUseCase {
        every { application.contentResolver.persistedUriPermissions } returns emptyList()
        return CreateFolderUseCase(
            application = application,
            getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepository)
        )
    }

    @Test
    fun `creates folder with valid name`() = runTest {
        val parent = temporaryFolder.newFolder("parent")

        val result = buildUseCase()(parent.absolutePath, "Training")

        assertEquals(CreateFolderResult.SUCCESS, result)
        assertTrue(parent.resolve("Training").isDirectory)
    }

    @Test
    fun `trims folder name before creating`() = runTest {
        val parent = temporaryFolder.newFolder("parent")

        val result = buildUseCase()(parent.absolutePath, "  Training  ")

        assertEquals(CreateFolderResult.SUCCESS, result)
        assertTrue(parent.resolve("Training").isDirectory)
    }

    @Test
    fun `rejects blank folder name`() = runTest {
        val parent = temporaryFolder.newFolder("parent")

        val result = buildUseCase()(parent.absolutePath, "   ")

        assertEquals(CreateFolderResult.INVALID_NAME, result)
    }

    @Test
    fun `rejects invalid folder name characters`() = runTest {
        val parent = temporaryFolder.newFolder("parent")

        val result = buildUseCase()(parent.absolutePath, "Bad/Name")

        assertEquals(CreateFolderResult.INVALID_NAME, result)
    }

    @Test
    fun `returns already exists for existing folder`() = runTest {
        val parent = temporaryFolder.newFolder("parent")
        parent.resolve("Training").mkdirs()

        val result = buildUseCase()(parent.absolutePath, "Training")

        assertEquals(CreateFolderResult.ALREADY_EXISTS, result)
    }

    @Test
    fun `returns already exists for existing file`() = runTest {
        val parent = temporaryFolder.newFolder("parent")
        parent.resolve("Training").writeText("file")

        val result = buildUseCase()(parent.absolutePath, "Training")

        assertEquals(CreateFolderResult.ALREADY_EXISTS, result)
    }

    @Test
    fun `parent under video root resolves to video tree`() {
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

        val selectedTree = buildUseCase().treeForParent(
            parentPath = "/storage/emulated/0/Movies",
            musicTree = musicTree,
            videoTree = videoTree
        )

        assertEquals(videoTree, selectedTree)
    }
}
