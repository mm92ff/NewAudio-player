package com.example.newaudio.domain.usecase.file

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Build
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

    fun queryDisplayName(cr: ContentResolver, docUri: Uri): String? {
        return queryString(cr, docUri, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    }

    fun queryMimeType(cr: ContentResolver, docUri: Uri): String? {
        return queryString(cr, docUri, DocumentsContract.Document.COLUMN_MIME_TYPE)
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

    /**
     * SAF Move, fallback to Copy+Delete if moveDocument is not supported.
     * Return: Uri of the new document, or null on failure.
     */
    fun moveDocumentBestEffort(
        cr: ContentResolver,
        tree: TreeInfo,
        srcDocUri: Uri,
        srcParentDocUri: Uri,
        targetParentDocUri: Uri
    ): Uri? {
        // 1) Try native move (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val moved = runCatching {
                DocumentsContract.moveDocument(cr, srcDocUri, srcParentDocUri, targetParentDocUri)
            }.getOrNull()
            if (moved != null) return moved
        }

        // 2) Fallback: copy + delete
        val name = queryDisplayName(cr, srcDocUri) ?: return null
        val mime = cr.getType(srcDocUri)
            ?: queryMimeType(cr, srcDocUri)
            ?: "application/octet-stream"

        val created = runCatching {
            DocumentsContract.createDocument(cr, targetParentDocUri, mime, name)
        }.getOrNull() ?: return null

        val copied = copyUri(cr, srcDocUri, created)
        if (!copied) return null

        val deleted = runCatching { DocumentsContract.deleteDocument(cr, srcDocUri) }.getOrDefault(false)
        if (!deleted) {
            // If delete fails, we now have 2 copies -> better to report failure.
            Timber.tag(TAG).w("Copy succeeded but delete failed for $srcDocUri")
            return null
        }

        return created
    }

    private fun copyUri(cr: ContentResolver, from: Uri, to: Uri): Boolean {
        return runCatching {
            cr.openInputStream(from)?.use { input ->
                cr.openOutputStream(to, "w")?.use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                    true
                } ?: false
            } ?: false
        }.getOrDefault(false)
    }

    private fun queryString(cr: ContentResolver, uri: Uri, column: String): String? {
        return cr.query(uri, arrayOf(column), null, null, null)?.use { c: Cursor ->
            val idx = c.getColumnIndex(column)
            if (idx == -1) return null
            if (!c.moveToFirst()) return null
            c.getString(idx)
        }
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
        p = p.replace("/sdcard/", "/storage/emulated/0/")
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
