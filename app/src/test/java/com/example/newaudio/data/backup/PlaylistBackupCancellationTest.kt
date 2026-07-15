package com.example.newaudio.data.backup

import com.example.newaudio.data.database.DatabaseTransactionRunner
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.data.database.dao.PlaylistDao
import com.example.newaudio.data.database.dao.VideoMarkerDao
import com.example.newaudio.data.database.dao.VideoPlaylistDao
import com.example.newaudio.domain.model.UserPreferences
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlaylistBackupCancellationTest {
    private val dispatcher = StandardTestDispatcher()

    @Test(expected = CancellationException::class)
    fun `exporter does not swallow cancellation`() = runTest(dispatcher) {
        val playlistDao = mockk<PlaylistDao>()
        everyPlaylistFlowCancels(playlistDao)
        PlaylistBackupExporter(
            playlistDao = playlistDao,
            videoPlaylistDao = mockk(relaxed = true),
            videoMarkerDao = mockk(relaxed = true),
            destination = mockk(relaxed = true),
            ioDispatcher = dispatcher
        ).export("content://backup/export", UserPreferences.default())
    }

    @Test(expected = CancellationException::class)
    fun `importer does not swallow cancellation`() = runTest(dispatcher) {
        val source = mockk<PlaylistBackupSource>()
        io.mockk.every { source.readText(any(), any()) } throws CancellationException("cancel")
        PlaylistBackupImporter(
            playlistDao = mockk(relaxed = true),
            videoPlaylistDao = mockk(relaxed = true),
            videoDao = mockk<VideoDao>(relaxed = true),
            videoMarkerDao = mockk(relaxed = true),
            transactionRunner = object : DatabaseTransactionRunner {
                override suspend fun <T> run(block: suspend () -> T): T = block()
            },
            source = source,
            validator = PlaylistImportValidator(),
            ioDispatcher = dispatcher
        ).import("content://backup/import")
    }

    private fun everyPlaylistFlowCancels(dao: PlaylistDao) {
        io.mockk.every { dao.getAllPlaylists() } returns flow {
            throw CancellationException("cancel")
        }
    }
}
