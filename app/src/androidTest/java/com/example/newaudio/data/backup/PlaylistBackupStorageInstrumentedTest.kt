package com.example.newaudio.data.backup

import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.newaudio.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistBackupStorageInstrumentedTest {

    @Test
    fun contentUriRoundTripsThroughDocumentsProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val authority = "${context.packageName}.test.documents"
        val treeUri = DocumentsContract.buildTreeDocumentUri(authority, ROOT_ID)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, ROOT_ID)
        val fileUri = DocumentsContract.createDocument(
            context.contentResolver,
            rootUri,
            "application/json",
            "playlist-backup-${System.nanoTime()}.json"
        )
        assertNotNull(fileUri)

        try {
            val payload = """{"version":4,"playlists":[]}"""
            AndroidPlaylistBackupDestination(context).writeText(requireNotNull(fileUri).toString(), payload)

            assertEquals(
                payload,
                AndroidPlaylistBackupSource(context).readText(
                    fileUri.toString(),
                    Constants.Security.MAX_IMPORT_BYTES
                )
            )
        } finally {
            DocumentsContract.deleteDocument(context.contentResolver, requireNotNull(fileUri))
        }
    }

    private companion object {
        const val ROOT_ID = "root"
    }
}
