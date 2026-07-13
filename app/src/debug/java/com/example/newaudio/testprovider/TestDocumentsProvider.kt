package com.example.newaudio.testprovider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

class TestDocumentsProvider : DocumentsProvider() {
    private val rootDirectory: File
        get() = requireNotNull(context).filesDir.resolve("documents-provider-root").apply { mkdirs() }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: ROOT_COLUMNS)
        val row = cursor.newRow()
        cursor.columnNames.forEach { column ->
            when (column) {
                Root.COLUMN_ROOT_ID -> row.add(ROOT_ID)
                Root.COLUMN_DOCUMENT_ID -> row.add(ROOT_ID)
                Root.COLUMN_TITLE -> row.add("Test storage")
                Root.COLUMN_FLAGS -> row.add(Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
                Root.COLUMN_MIME_TYPES -> row.add("*/*")
                Root.COLUMN_AVAILABLE_BYTES -> row.add(rootDirectory.usableSpace)
                else -> row.add(null)
            }
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DOCUMENT_COLUMNS).also { cursor ->
            addDocumentRow(cursor, documentId, fileForId(documentId))
        }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor = MatrixCursor(projection ?: DOCUMENT_COLUMNS).also { cursor ->
        val parent = fileForId(parentDocumentId)
        parent.listFiles()?.sortedBy(File::getName)?.forEach { child ->
            addDocumentRow(cursor, idForFile(child), child)
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor = ParcelFileDescriptor.open(
        fileForId(documentId),
        ParcelFileDescriptor.parseMode(mode)
    )

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        requireSafeName(displayName)
        val parent = fileForId(parentDocumentId)
        val child = parent.resolve(displayName)
        if (child.exists()) throw FileNotFoundException("Document already exists")
        val created = if (mimeType == Document.MIME_TYPE_DIR) child.mkdir() else child.createNewFile()
        if (!created) throw FileNotFoundException("Could not create document")
        return idForFile(child)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        requireSafeName(displayName)
        val source = fileForId(documentId)
        val destination = source.parentFile?.resolve(displayName)
            ?: throw FileNotFoundException(documentId)
        if (destination.exists() || !source.renameTo(destination)) {
            throw FileNotFoundException("Could not rename document")
        }
        return idForFile(destination)
    }

    override fun deleteDocument(documentId: String) {
        if (documentId == ROOT_ID || !fileForId(documentId).deleteRecursively()) {
            throw FileNotFoundException("Could not delete document")
        }
    }

    override fun getDocumentType(documentId: String): String = mimeType(fileForId(documentId))

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = fileForId(parentDocumentId).canonicalFile
        val child = fileForId(documentId).canonicalFile
        return child != parent && child.path.startsWith(parent.path + File.separator)
    }

    private fun addDocumentRow(cursor: MatrixCursor, documentId: String, file: File) {
        val row = cursor.newRow()
        cursor.columnNames.forEach { column ->
            when (column) {
                Document.COLUMN_DOCUMENT_ID -> row.add(documentId)
                Document.COLUMN_DISPLAY_NAME -> row.add(if (documentId == ROOT_ID) "root" else file.name)
                Document.COLUMN_MIME_TYPE -> row.add(mimeType(file))
                Document.COLUMN_SIZE -> row.add(if (file.isFile) file.length() else 0L)
                Document.COLUMN_LAST_MODIFIED -> row.add(file.lastModified())
                Document.COLUMN_FLAGS -> row.add(
                    Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or
                        if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE
                )
                else -> row.add(null)
            }
        }
    }

    private fun mimeType(file: File): String =
        if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream"

    private fun fileForId(documentId: String): File {
        if (documentId == ROOT_ID) return rootDirectory
        if (!documentId.startsWith("$ROOT_ID/")) throw FileNotFoundException(documentId)
        val candidate = rootDirectory.resolve(documentId.removePrefix("$ROOT_ID/")).canonicalFile
        if (!candidate.path.startsWith(rootDirectory.canonicalPath + File.separator)) {
            throw FileNotFoundException(documentId)
        }
        return candidate
    }

    private fun idForFile(file: File): String {
        val relative = file.canonicalFile.relativeTo(rootDirectory.canonicalFile).invariantSeparatorsPath
        return if (relative == ".") ROOT_ID else "$ROOT_ID/$relative"
    }

    private fun requireSafeName(name: String) {
        if (name.isBlank() || name == "." || name == ".." || '/' in name || '\\' in name) {
            throw FileNotFoundException("Unsafe display name")
        }
    }

    private companion object {
        const val ROOT_ID = "root"
        val ROOT_COLUMNS = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_AVAILABLE_BYTES
        )
        val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS
        )
    }
}
