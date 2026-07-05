package com.example.newaudio.domain.usecase.file

import com.example.newaudio.data.database.DirectSubFolderSongCount
import com.example.newaudio.data.database.DirectSubFolderVideoCount
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.MediaBrowserMode
import com.example.newaudio.domain.repository.IFolderOrderRepository
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.fake.FakeSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GetSortedFileTreeUseCaseTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val songDao = mockk<SongDao>()
    private val videoDao = mockk<VideoDao>(relaxed = true)
    private val folderOrderRepository = mockk<IFolderOrderRepository>()
    private val settingsRepository = FakeSettingsRepository()

    @Test
    fun `includes empty filesystem folders without database media entries`() = runTest {
        val parent = temporaryFolder.newFolder("music")
        parent.resolve("EmptyFolder").mkdirs()
        parent.resolve(".HiddenFolder").mkdirs()
        val parentPath = parent.absolutePath.toBrowserPath()

        every { folderOrderRepository.observeFolderOrder(parentPath) } returns flowOf(null)
        every { songDao.observeSubFolders(parentPath) } returns flowOf(emptyList())
        every { songDao.observeSongsInFolderMinimal(parentPath) } returns flowOf(emptyList())

        val items = buildUseCase()(parentPath, MediaBrowserMode.MUSIC)
            .first { items -> items.any { it.name == "EmptyFolder" } }

        assertTrue(items.any { it is FileItem.Folder && it.name == "EmptyFolder" })
        assertFalse(items.any { it.name == ".HiddenFolder" })
    }

    @Test
    fun `merges filesystem folders with database folders without duplicates`() = runTest {
        val parent = temporaryFolder.newFolder("music")
        parent.resolve("Existing").mkdirs()
        val parentPath = parent.absolutePath.toBrowserPath()

        settingsRepository.setShowFolderSongCount(true)
        every { folderOrderRepository.observeFolderOrder(parentPath) } returns flowOf(null)
        every { songDao.observeAllSubFolderSongCounts(parentPath) } returns flowOf(
            listOf(DirectSubFolderSongCount(path = "$parentPath/Existing", songCount = 2))
        )
        every { songDao.observeSongsInFolderMinimal(parentPath) } returns flowOf(emptyList())

        val items = buildUseCase()(parentPath, MediaBrowserMode.MUSIC)
            .first { items -> items.any { it.name == "Existing" } }
        val folders = items.filterIsInstance<FileItem.Folder>().filter { it.name == "Existing" }

        assertEquals(1, folders.size)
        assertEquals(2, folders.single().mediaCount)
    }

    @Test
    fun `uses video counts for folder media count in video mode`() = runTest {
        val parent = temporaryFolder.newFolder("movies")
        parent.resolve("Training").mkdirs()
        val parentPath = parent.absolutePath.toBrowserPath()

        settingsRepository.setShowFolderSongCount(true)
        every { folderOrderRepository.observeFolderOrder(parentPath) } returns flowOf(null)
        every { videoDao.observeAllSubFolderVideoCounts(parentPath) } returns flowOf(
            listOf(DirectSubFolderVideoCount(path = "$parentPath/Training", videoCount = 2))
        )
        every { videoDao.observeVideosInFolderMinimal(parentPath) } returns flowOf(emptyList())

        val items = buildUseCase()(parentPath, MediaBrowserMode.VIDEO)
            .first { items -> items.any { it.name == "Training" } }
        val folders = items.filterIsInstance<FileItem.Folder>().filter { it.name == "Training" }

        assertEquals(1, folders.size)
        assertEquals(2, folders.single().mediaCount)
    }

    private fun buildUseCase(): GetSortedFileTreeUseCase {
        return GetSortedFileTreeUseCase(
            songDao = songDao,
            videoDao = videoDao,
            folderOrderRepository = folderOrderRepository,
            getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepository),
            dispatcher = UnconfinedTestDispatcher()
        )
    }

    private fun String.toBrowserPath(): String = replace('\\', '/')
}
