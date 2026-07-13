package com.example.newaudio.domain.usecase.file

import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafTreeAccessProviderTest {
    @Test
    fun createListRenameAndDeleteThroughDocumentsContractProvider() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = targetContext.contentResolver
        val authority = "${targetContext.packageName}.test.documents"
        val treeUri = DocumentsContract.buildTreeDocumentUri(authority, ROOT_ID)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, ROOT_ID)
        val tree = SafTreeAccess.TreeInfo(treeUri, ROOT_ID, "/virtual")

        SafTreeAccess.listChildren(resolver, tree, rootUri).orEmpty().forEach { child ->
            SafTreeAccess.deleteRecursively(resolver, tree, child.uri)
        }

        val folderUri = SafTreeAccess.createDocument(
            resolver,
            rootUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            "100% Mix_1"
        )
        assertNotNull(folderUri)
        val fileUri = SafTreeAccess.createDocument(
            resolver,
            requireNotNull(folderUri),
            "audio/mpeg",
            "Grüsse 01.mp3"
        )
        assertNotNull(fileUri)
        val bytes = "provider-data".toByteArray()
        resolver.openOutputStream(requireNotNull(fileUri), "w")!!.use { output ->
            ByteArrayInputStream(bytes).copyTo(output)
        }

        val children = SafTreeAccess.listChildren(resolver, tree, requireNotNull(folderUri))
        assertEquals(listOf("Grüsse 01.mp3"), children?.map { it.name })
        assertArrayEquals(bytes, resolver.openInputStream(fileUri)!!.use { it.readBytes() })

        val renamedUri = SafTreeAccess.renameDocument(resolver, fileUri, "Neu %_ Datei.mp3")
        assertEquals("Neu %_ Datei.mp3", SafTreeAccess.queryDisplayName(resolver, requireNotNull(renamedUri)))
        assertTrue(SafTreeAccess.deleteRecursively(resolver, tree, folderUri))
        assertNull(SafTreeAccess.findChild(resolver, tree, rootUri, "100% Mix_1"))
    }

    private companion object {
        const val ROOT_ID = "root"
    }
}
