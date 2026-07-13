package com.example.newaudio.domain.usecase.file

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import timber.log.Timber
import java.io.File

/**
 * Utility for SAF (Storage Access Framework) tree URIs.
 * We still work with real FS paths (/storage/...) but map them to document URIs.
 */
object SafTreeAccess {

    private const val TAG = "SafTreeAccess"

    data class TreeInfo(
        val treeUri: Uri,
        val treeDocId: String,   // e.g. "primary:" or "primary:Music"
        val baseFsPath: String   // e.g. "/storage/emulated/0" or "/storage/emulated/0/Music"
    )

    data class DocumentEntry(
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val size: Long?
    ) {
        val isDirectory: Boolean
            get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    fun parseTree(treeUriString: String): TreeInfo? {
        if (treeUriString.isBlank()) return null

        val treeUri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return null
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null

        val volume = treeDocId.substringBefore(':') // "primary" or "XXXX-XXXX"
        val rel = treeDocId.substringAfter(':', "") // "Music/.." or ""

        val volumeRoot = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        val baseFsPath = if (rel.isBlank()) volumeRoot else "$volumeRoot/$rel"

        return TreeInfo(
            treeUri = treeUri,
            treeDocId = treeDocId,
            baseFsPath = normalizeFsPath(baseFsPath)
        )
    }

    fun hasPersistedWritePermission(cr: ContentResolver, treeUri: Uri): Boolean {
        return cr.persistedUriPermissions.any { perm ->
            perm.uri == treeUri && perm.isWritePermission
        }
    }

    fun documentUriForFsPath(tree: TreeInfo, fsPathRaw: String): Uri? {
        val fsPath = normalizeFsPath(fsPathRaw)

        val base = tree.baseFsPath.removeSuffix("/")
        if (fsPath == base) {
            return DocumentsContract.buildDocumentUriUsingTree(tree.treeUri, tree.treeDocId)
        }
        if (!fsPath.startsWith("$base/")) return null

        val relative = fsPath.removePrefix("$base/").trim('/').replace(File.separatorChar, '/')
        if (relative.isBlank()) {
            return DocumentsContract.buildDocumentUriUsingTree(tree.treeUri, tree.treeDocId)
        }

        val childDocId = joinDocId(tree.treeDocId, relative)
        return DocumentsContract.buildDocumentUriUsingTree(tree.treeUri, childDocId)
    }

    fun containsFsPath(tree: TreeInfo, fsPathRaw: String): Boolean {
        val fsPath = normalizeFsPath(fsPathRaw)
        val base = tree.baseFsPath.removeSuffix("/")
        return fsPath == base || fsPath.startsWith("$base/")
    }

    fun queryDisplayName(cr: ContentResolver, docUri: Uri): String? {
        return queryString(cr, docUri, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    }

    fun renameDocument(cr: ContentResolver, docUri: Uri, newName: String): Uri? {
        return DocumentsContract.renameDocument(cr, docUri, newName)
    }

    fun queryMimeType(cr: ContentResolver, docUri: Uri): String? {
        return queryString(cr, docUri, DocumentsContract.Document.COLUMN_MIME_TYPE)
    }

    fun queryDocument(cr: ContentResolver, docUri: Uri): DocumentEntry? {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        return runCatching {
            cr.query(docUri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.toDocumentEntry(docUri)
            }
        }.getOrNull()
    }

    fun listChildren(cr: ContentResolver, tree: TreeInfo, parentUri: Uri): List<DocumentEntry>? {
        val parentId = runCatching { DocumentsContract.getDocumentId(parentUri) }.getOrNull()
            ?: return null
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree.treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        return runCatching {
            cr.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                if (idIndex < 0) return@use null
                buildList<DocumentEntry> {
                    while (cursor.moveToNext()) {
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(
                            tree.treeUri,
                            cursor.getString(idIndex)
                        )
                        cursor.toDocumentEntry(childUri)?.let { entry -> add(entry) }
                    }
                }
            }
        }.getOrElse {
            Timber.tag(TAG).e(it, "Could not list SAF children for $parentUri")
            null
        }
    }

    fun findChild(
        cr: ContentResolver,
        tree: TreeInfo,
        parentUri: Uri,
        displayName: String
    ): DocumentEntry? = listChildren(cr, tree, parentUri)?.firstOrNull { it.name == displayName }

    fun createDocument(
        cr: ContentResolver,
        parentUri: Uri,
        mimeType: String,
        displayName: String
    ): Uri? = runCatching {
        DocumentsContract.createDocument(cr, parentUri, mimeType, displayName)
    }.getOrElse {
        Timber.tag(TAG).e(it, "Could not create SAF document $displayName in $parentUri")
        null
    }

    fun isDirectory(cr: ContentResolver, docUri: Uri): Boolean {
        val mime = queryMimeType(cr, docUri) ?: return false
        return mime == DocumentsContract.Document.MIME_TYPE_DIR
    }

    /**
     * Deletes a file/folder recursively. (Folder: delete children first)
     */
    fun deleteRecursively(cr: ContentResolver, tree: TreeInfo, docUri: Uri): Boolean {
        val docId = runCatching { DocumentsContract.getDocumentId(docUri) }.getOrNull() ?: return false

        val mime = queryMimeType(cr, docUri)
        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree.treeUri, docId)
            cr.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { c ->
                val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                while (c.moveToNext()) {
                    val childId = c.getString(idIdx)
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(tree.treeUri, childId)
                    val ok = deleteRecursively(cr, tree, childUri)
                    if (!ok) return false
                }
            }
        }

        return runCatching { DocumentsContract.deleteDocument(cr, docUri) }.getOrDefault(false)
    }

    private fun queryString(cr: ContentResolver, uri: Uri, column: String): String? {
        return cr.query(uri, arrayOf(column), null, null, null)?.use { c: Cursor ->
            val idx = c.getColumnIndex(column)
            if (idx == -1) return null
            if (!c.moveToFirst()) return null
            c.getString(idx)
        }
    }

    private fun Cursor.toDocumentEntry(uri: Uri): DocumentEntry? {
        val nameIndex = getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeIndex = getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        if (nameIndex < 0 || mimeIndex < 0) return null
        val sizeIndex = getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        return DocumentEntry(
            uri = uri,
            name = getString(nameIndex) ?: return null,
            mimeType = getString(mimeIndex) ?: return null,
            size = sizeIndex.takeIf { it >= 0 && !isNull(it) }?.let(::getLong)
        )
    }

    private fun joinDocId(treeDocId: String, relative: String): String {
        // treeDocId: "primary:" or "primary:Music"
        val afterColon = treeDocId.substringAfter(':', "")
        return if (afterColon.isBlank()) {
            // Root tree: "primary:" + "Music/.."
            treeDocId + relative
        } else {
            // Subtree: "primary:Music" + "/" + "sub/.."
            "$treeDocId/$relative"
        }
    }

    fun normalizeFsPath(path: String): String {
        var p = path.trim()

        // Normalize common aliases
        p = p.replace("/sdcard/", "${Environment.getExternalStorageDirectory().path}/")
        p = p.replace("/storage/self/primary/", "/storage/emulated/0/")

        // Remove double slashes (rough pass)
        while (p.contains("//")) p = p.replace("//", "/")

        return p.removeSuffix("/")
    }

    fun joinFs(parent: String, child: String): String {
        val p = parent.removeSuffix("/")
        val c = child.trimStart('/')
        return "$p/$c"
    }
}
