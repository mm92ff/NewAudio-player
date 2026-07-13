package com.example.newaudio.domain.usecase.file

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SafeStorageOperationsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val resolver = mockk<ContentResolver>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private lateinit var tree: SafTreeAccess.TreeInfo
    private lateinit var targetDirectoryUri: Uri
    private val destinationUri = Uri.parse("content://documents/document/destination")

    @Before
    fun setUp() {
        mockkObject(SafTreeAccess)
        val root = temporaryFolder.root.absolutePath
        val treeUri = Uri.parse("content://documents/tree/root")
        tree = SafTreeAccess.TreeInfo(treeUri, "root:", root)
        targetDirectoryUri = Uri.parse("content://documents/tree/root/document/root")
        every { context.contentResolver } returns resolver
        every { SafTreeAccess.containsFsPath(tree, any()) } returns true
        every { SafTreeAccess.hasPersistedWritePermission(resolver, treeUri) } returns true
        every { SafTreeAccess.documentUriForFsPath(tree, root) } returns targetDirectoryUri
        every { SafTreeAccess.listChildren(resolver, tree, targetDirectoryUri) } returns emptyList()
        every {
            SafTreeAccess.createDocument(resolver, targetDirectoryUri, any(), any())
        } returns destinationUri
        every { SafTreeAccess.queryDisplayName(resolver, destinationUri) } returns "target.mp3"
        every { SafTreeAccess.deleteRecursively(resolver, tree, destinationUri) } returns true
    }

    @After
    fun tearDown() {
        unmockkObject(SafTreeAccess)
    }

    @Test
    fun `null output stream fails and removes partial destination`() {
        val source = temporaryFolder.newFile("source.mp3").apply { writeText("audio") }
        val destination = File(temporaryFolder.root, "target.mp3")
        every { resolver.openOutputStream(destinationUri, "w") } returns null

        val result = SafeStorageOperations(context).copyVerified(source, destination, tree)

        assertNull(result)
        assertTrue(source.exists())
        verify(exactly = 1) { SafTreeAccess.deleteRecursively(resolver, tree, destinationUri) }
    }

    @Test
    fun `provider assigned filename and uri are returned after byte verification`() {
        val bytes = "verified audio".toByteArray()
        val source = temporaryFolder.newFile("source.mp3").apply { writeBytes(bytes) }
        val destination = File(temporaryFolder.root, "target.mp3")
        every { SafTreeAccess.queryDisplayName(resolver, destinationUri) } returns "target (1).mp3"
        every { resolver.openOutputStream(destinationUri, "w") } returns ByteArrayOutputStream()
        every { resolver.openInputStream(destinationUri) } returns ByteArrayInputStream(bytes)

        val result = SafeStorageOperations(context).copyVerified(source, destination, tree)

        assertEquals("target (1).mp3", result?.destination?.name)
        assertEquals(destinationUri.toString(), result?.contentUri)
    }

    @Test
    fun `stream failure removes partial destination and preserves source`() {
        val source = temporaryFolder.newFile("large.mp3").apply { writeBytes(ByteArray(8_192) { 1 }) }
        val destination = File(temporaryFolder.root, "target-large.mp3")
        every { resolver.openOutputStream(destinationUri, "w") } returns object : OutputStream() {
            private var written = 0
            override fun write(value: Int) {
                if (written++ >= 1_024) throw IOException("provider stopped")
            }
        }

        val result = SafeStorageOperations(context).copyVerified(source, destination, tree)

        assertNull(result)
        assertTrue(source.exists())
        verify(exactly = 1) { SafTreeAccess.deleteRecursively(resolver, tree, destinationUri) }
    }
}
