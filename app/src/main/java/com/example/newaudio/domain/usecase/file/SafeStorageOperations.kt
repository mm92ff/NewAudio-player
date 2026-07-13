package com.example.newaudio.domain.usecase.file

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class SafeStorageOperations @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val resolver get() = context.contentResolver

    data class CopyResult(val destination: File, val contentUri: String)

    data class RenameResult(
        val uri: Uri,
        val contentUri: String,
        val oldName: String,
        val actualName: String
    )

    fun destinationExists(destination: File, targetTree: SafTreeAccess.TreeInfo?): Boolean {
        if (destination.exists()) return true
        val tree = writableTreeForPath(targetTree, destination.parent.orEmpty()) ?: return false
        val parentUri = SafTreeAccess.documentUriForFsPath(tree, destination.parent.orEmpty()) ?: return false
        return SafTreeAccess.findChild(resolver, tree, parentUri, destination.name) != null
    }

    fun copyVerified(
        source: File,
        destination: File,
        sourceTree: SafTreeAccess.TreeInfo?,
        targetTree: SafTreeAccess.TreeInfo? = sourceTree
    ): CopyResult? {
        val readableSourceTree = readableTreeForPath(sourceTree, source.absolutePath)
        val writableTargetTree = writableTreeForPath(targetTree, destination.parent.orEmpty())

        return when {
            writableTargetTree != null -> copyToSaf(
                source = source,
                destination = destination,
                sourceTree = readableSourceTree,
                targetTree = writableTargetTree
            )
            !source.exists() && readableSourceTree != null -> copyFromSafToFile(
                source = source,
                destination = destination,
                sourceTree = readableSourceTree
            )
            else -> copyLegacy(source, destination)
        }
    }

    fun rename(file: File, requestedName: String, sourceTree: SafTreeAccess.TreeInfo?): RenameResult? {
        val tree = writableTreeForPath(sourceTree, file.absolutePath)
        if (tree != null) {
            val documentUri = SafTreeAccess.documentUriForFsPath(tree, file.absolutePath) ?: return null
            val oldName = SafTreeAccess.queryDisplayName(resolver, documentUri) ?: file.name
            val renamedUri = SafTreeAccess.renameDocument(resolver, documentUri, requestedName) ?: return null
            val actualName = SafTreeAccess.queryDisplayName(resolver, renamedUri) ?: requestedName
            return RenameResult(renamedUri, renamedUri.toString(), oldName, actualName)
        }

        val destination = File(file.parentFile, requestedName)
        if (!file.exists() || destination.exists() || !file.renameTo(destination)) return null
        return RenameResult(
            uri = Uri.fromFile(destination),
            contentUri = destination.absolutePath,
            oldName = file.name,
            actualName = destination.name
        )
    }

    fun renameDocument(documentUri: Uri, requestedName: String): Uri? =
        SafTreeAccess.renameDocument(resolver, documentUri, requestedName)

    fun rollbackRename(result: RenameResult, renamedPath: String): Boolean =
        if (result.uri.scheme == ContentResolverScheme.CONTENT) {
            renameDocument(result.uri, result.oldName) != null
        } else {
            val renamedFile = File(renamedPath)
            renamedFile.renameTo(File(renamedFile.parentFile, result.oldName))
        }

    fun delete(file: File, sourceTree: SafTreeAccess.TreeInfo?): Boolean {
        val tree = writableTreeForPath(sourceTree, file.absolutePath)
        if (tree != null) {
            val documentUri = SafTreeAccess.documentUriForFsPath(tree, file.absolutePath)
            if (documentUri != null) {
                return runCatching { SafTreeAccess.deleteRecursively(resolver, tree, documentUri) }
                    .getOrDefault(false)
            }
        }
        return runCatching {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }.getOrDefault(false)
    }

    private fun copyToSaf(
        source: File,
        destination: File,
        sourceTree: SafTreeAccess.TreeInfo?,
        targetTree: SafTreeAccess.TreeInfo
    ): CopyResult? {
        val sourceEntry = sourceEntry(source, sourceTree) ?: return null
        val targetParentUri = SafTreeAccess.documentUriForFsPath(
            targetTree,
            destination.parent.orEmpty()
        ) ?: return null
        val existingChildren = SafTreeAccess.listChildren(resolver, targetTree, targetParentUri)
            ?: return null
        if (existingChildren.any { it.name == destination.name }) {
            return null
        }

        val destinationUri = SafTreeAccess.createDocument(
            resolver,
            targetParentUri,
            if (sourceEntry.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else getMimeType(destination),
            destination.name
        ) ?: return null

        return try {
            val actualName = SafTreeAccess.queryDisplayName(resolver, destinationUri) ?: destination.name
            val copied = if (sourceEntry.isDirectory) {
                copyDirectoryToSaf(sourceEntry, sourceTree, targetTree, destinationUri)
            } else {
                copyFileToDocument(sourceEntry, destinationUri)
            }
            if (!copied) {
                SafTreeAccess.deleteRecursively(resolver, targetTree, destinationUri)
                null
            } else {
                CopyResult(File(destination.parentFile, actualName), destinationUri.toString())
            }
        } catch (error: Exception) {
            Timber.e(error, "SAF copy failed: ${source.path} -> ${destination.path}")
            SafTreeAccess.deleteRecursively(resolver, targetTree, destinationUri)
            null
        }
    }

    private fun copyDirectoryToSaf(
        source: SourceEntry,
        sourceTree: SafTreeAccess.TreeInfo?,
        targetTree: SafTreeAccess.TreeInfo,
        destinationDirectoryUri: Uri
    ): Boolean {
        val children = sourceChildren(source, sourceTree) ?: return false
        for (child in children) {
            val existingChildren = SafTreeAccess.listChildren(
                resolver,
                targetTree,
                destinationDirectoryUri
            ) ?: return false
            if (existingChildren.any { it.name == child.name }) {
                return false
            }
            val childDestinationUri = SafTreeAccess.createDocument(
                resolver,
                destinationDirectoryUri,
                if (child.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else child.mimeType,
                child.name
            ) ?: return false
            val actualName = SafTreeAccess.queryDisplayName(resolver, childDestinationUri) ?: child.name
            if (actualName != child.name) return false

            val copied = if (child.isDirectory) {
                copyDirectoryToSaf(child, sourceTree, targetTree, childDestinationUri)
            } else {
                copyFileToDocument(child, childDestinationUri)
            }
            if (!copied) return false
        }
        return true
    }

    private fun copyFileToDocument(source: SourceEntry, destinationUri: Uri): Boolean {
        val input = openSourceInputStream(source) ?: return false
        val copiedBytes = input.use { sourceStream ->
            val output = resolver.openOutputStream(destinationUri, "w") ?: return false
            output.use { targetStream ->
                sourceStream.copyTo(targetStream).also { targetStream.flush() }
            }
        }
        val destinationBytes = resolver.openInputStream(destinationUri)?.use { it.countBytes() }
        return (source.size == null || source.size == copiedBytes) && destinationBytes == copiedBytes
    }

    private fun copyFromSafToFile(
        source: File,
        destination: File,
        sourceTree: SafTreeAccess.TreeInfo
    ): CopyResult? {
        val sourceEntry = sourceEntry(source, sourceTree) ?: return null
        if (destination.exists()) return null
        return try {
            if (!copySourceToFile(sourceEntry, sourceTree, destination)) {
                destination.deleteRecursively()
                null
            } else {
                CopyResult(destination, destination.absolutePath)
            }
        } catch (error: Exception) {
            Timber.e(error, "SAF-to-file copy failed: ${source.path} -> ${destination.path}")
            destination.deleteRecursively()
            null
        }
    }

    private fun copySourceToFile(
        source: SourceEntry,
        sourceTree: SafTreeAccess.TreeInfo?,
        destination: File
    ): Boolean {
        if (source.isDirectory) {
            if (!destination.mkdir()) return false
            val children = sourceChildren(source, sourceTree) ?: return false
            return children.all { child ->
                copySourceToFile(child, sourceTree, File(destination, child.name))
            }
        }

        destination.parentFile?.let { if (!it.exists() && !it.mkdirs()) return false }
        val copiedBytes = openSourceInputStream(source)?.use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        } ?: return false
        return (source.size == null || source.size == copiedBytes) && destination.length() == copiedBytes
    }

    private fun copyLegacy(source: File, destination: File): CopyResult? {
        return try {
            val verified = if (source.isDirectory) {
                source.copyRecursively(destination, overwrite = false) && verifyDirectoryCopy(source, destination)
            } else {
                FileInputStream(source).use { input ->
                    FileOutputStream(destination).use { output -> input.copyTo(output) }
                }
                source.length() == destination.length()
            }
            if (verified) {
                CopyResult(destination, destination.absolutePath)
            } else {
                destination.deleteRecursively()
                null
            }
        } catch (error: Exception) {
            Timber.e(error, "Legacy copy failed: ${source.path} -> ${destination.path}")
            destination.deleteRecursively()
            null
        }
    }

    private fun sourceEntry(source: File, tree: SafTreeAccess.TreeInfo?): SourceEntry? {
        tree?.let { readableTree ->
            val sourceUri = SafTreeAccess.documentUriForFsPath(readableTree, source.absolutePath)
            val document = sourceUri?.let { SafTreeAccess.queryDocument(resolver, it) }
            if (document != null) {
                return SourceEntry(
                    file = null,
                    document = document,
                    name = document.name,
                    mimeType = document.mimeType,
                    size = document.size
                )
            }
        }
        if (source.exists()) {
            return SourceEntry(
                file = source,
                document = null,
                name = source.name,
                mimeType = if (source.isDirectory) {
                    DocumentsContract.Document.MIME_TYPE_DIR
                } else {
                    getMimeType(source)
                },
                size = source.takeUnless(File::isDirectory)?.length()
            )
        }
        return null
    }

    private fun sourceChildren(
        source: SourceEntry,
        sourceTree: SafTreeAccess.TreeInfo?
    ): List<SourceEntry>? {
        source.file?.let { directory ->
            val children = directory.listFiles() ?: return null
            return children.mapNotNull { sourceEntry(it, sourceTree) }
                .takeIf { it.size == children.size }
        }
        val tree = sourceTree ?: return null
        val document = source.document ?: return null
        return SafTreeAccess.listChildren(resolver, tree, document.uri)?.map { child ->
            SourceEntry(
                file = null,
                document = child,
                name = child.name,
                mimeType = child.mimeType,
                size = child.size
            )
        }
    }

    private fun openSourceInputStream(source: SourceEntry): InputStream? = when {
        source.file != null && source.file.canRead() -> FileInputStream(source.file)
        source.document != null -> resolver.openInputStream(source.document.uri)
        else -> null
    }

    private fun readableTreeForPath(
        tree: SafTreeAccess.TreeInfo?,
        path: String
    ): SafTreeAccess.TreeInfo? = tree?.takeIf {
        SafTreeAccess.containsFsPath(it, path) && resolver.persistedUriPermissions.any { permission ->
            permission.uri == it.treeUri && permission.isReadPermission
        }
    }

    private fun writableTreeForPath(
        tree: SafTreeAccess.TreeInfo?,
        path: String
    ): SafTreeAccess.TreeInfo? = tree?.takeIf {
        SafTreeAccess.containsFsPath(it, path) && SafTreeAccess.hasPersistedWritePermission(resolver, it.treeUri)
    }

    private fun InputStream.countBytes(): Long {
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read < 0) return total
            total += read
        }
    }

    private fun verifyDirectoryCopy(source: File, destination: File): Boolean {
        val sourceEntries = source.walkTopDown().map { it.relativeTo(source).path to it }.toMap()
        val destinationEntries = destination.walkTopDown().map { it.relativeTo(destination).path to it }.toMap()
        if (sourceEntries.keys != destinationEntries.keys) return false
        return sourceEntries.all { (path, sourceEntry) ->
            val destinationEntry = destinationEntries.getValue(path)
            sourceEntry.isDirectory == destinationEntry.isDirectory &&
                (sourceEntry.isDirectory || sourceEntry.length() == destinationEntry.length())
        }
    }

    private fun getMimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"

    private data class SourceEntry(
        val file: File?,
        val document: SafTreeAccess.DocumentEntry?,
        val name: String,
        val mimeType: String,
        val size: Long?
    ) {
        val isDirectory: Boolean
            get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private object ContentResolverScheme {
        const val CONTENT = "content"
    }
}
