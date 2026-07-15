package com.example.newaudio.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.newaudio.domain.repository.ImportFailure
import com.example.newaudio.util.Constants
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistBackupStorageTest {
    private val resolver = mockk<ContentResolver>()
    private val context = mockk<Context> {
        every { contentResolver } returns resolver
    }

    @Test
    fun `absolute file path round trips without uri normalization`() {
        val file = File.createTempFile("playlist-backup", ".json").apply { deleteOnExit() }
        val destination = AndroidPlaylistBackupDestination(context)
        val source = AndroidPlaylistBackupSource(context)

        destination.writeText(file.absolutePath, "{\"version\":4}")

        assertEquals("{\"version\":4}", source.readText(file.absolutePath, 1_024L))
    }

    @Test
    fun `content uri reads exact limit and rejects one byte above`() {
        val uri = Uri.parse("content://backup/import")
        val source = AndroidPlaylistBackupSource(context)
        every { resolver.openInputStream(uri) } returnsMany listOf(
            ByteArrayInputStream(ByteArray(Constants.Security.MAX_IMPORT_BYTES.toInt())),
            ByteArrayInputStream(ByteArray(Constants.Security.MAX_IMPORT_BYTES.toInt() + 1))
        )

        assertEquals(
            Constants.Security.MAX_IMPORT_BYTES.toInt(),
            source.readText(uri.toString(), Constants.Security.MAX_IMPORT_BYTES).length
        )
        val failure = try {
            source.readText(uri.toString(), Constants.Security.MAX_IMPORT_BYTES)
            null
        } catch (error: PlaylistBackupException) {
            error.failure
        }
        assertEquals(ImportFailure.TOO_LARGE, failure)
    }

    @Test
    fun `content uri destination writes and flushes utf8 payload`() {
        val uri = Uri.parse("content://backup/export")
        val output = ByteArrayOutputStream()
        every { resolver.openOutputStream(uri) } returns output

        AndroidPlaylistBackupDestination(context).writeText(uri.toString(), "Grüezi")

        assertTrue(output.toByteArray().contentEquals("Grüezi".toByteArray(Charsets.UTF_8)))
    }
}
