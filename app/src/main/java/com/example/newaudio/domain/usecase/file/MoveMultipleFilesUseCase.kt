package com.example.newaudio.domain.usecase.file

import android.app.Application
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.repository.IMediaScannerRepository
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

class MoveMultipleFilesUseCase @Inject constructor(
    private val songDao: SongDao,
    private val application: Application,
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    private val mediaScannerRepository: IMediaScannerRepository
) {
    suspend operator fun invoke(items: List<FileItem>, sourceParent: String, targetParent: String): Boolean = withContext(Dispatchers.IO) {
        var allSuccess = true
        val cr = application.contentResolver
        val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()
        val tree = settings?.musicFolderPath?.let { SafTreeAccess.parseTree(it) }

        for (item in items) {
            val sourceFile = File(item.path)
            val destFile = File(targetParent, sourceFile.name)

            // Skip if destination already exists
            if (destFile.exists()) {
                allSuccess = false
                continue
            }

            // 1. Physical move
            // Strategy A: Atomic rename (fast, often only works within the same filesystem)
            var moveSuccess = sourceFile.renameTo(destFile)

            // Strategy B: Copy & Delete (fallback for SD card / cross-filesystem)
            if (!moveSuccess) {
                // Copy with SAF support
                val copySuccess = copyPhysical(sourceFile, destFile, cr, tree)
                if (copySuccess) {
                    // If copy successful -> delete source
                    if (deletePhysical(sourceFile, cr, tree)) {
                        moveSuccess = true
                    } else {
                        // Copy exists but original could not be deleted -> inconsistent state
                        // Leave moveSuccess as false so the user sees an error
                        Timber.e("Move incomplete: Copied but failed to delete source ${sourceFile.path}")
                    }
                }
            }

            // 2. Datenbank Update
            if (moveSuccess) {
                try {
                    if (item is FileItem.AudioFile) {
                        // Update path in DB (preserves playcounts, ratings, etc.)
                        songDao.updatePath(
                            oldPath = item.path,
                            newPath = destFile.absolutePath,
                            newParentPath = targetParent,
                            newFilename = destFile.name
                        )
                    } else if (item is FileItem.Folder) {
                        // Folder: delete DB entries & rescan
                        songDao.deleteByFolder(item.path)
                        scanFolderRecursively(destFile)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "DB update failed for move: ${item.path}")
                }
            } else {
                allSuccess = false
            }
        }
        return@withContext allSuccess
    }

    private suspend fun scanFolderRecursively(folder: File) {
        folder.walk().forEach { file ->
            if (file.isFile && isAudioFile(file.name)) {
                try {
                    mediaScannerRepository.scanSingleFile(file.absolutePath)
                } catch (e: Exception) {
                    Timber.e("Scan failed for ${file.name}")
                }
            }
        }
    }

    private fun isAudioFile(filename: String): Boolean {
        val lower = filename.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".flac") || lower.endsWith(".wav") || lower.endsWith(".ogg")
    }

    // --- SAF helper methods (duplicated from Copy/Delete UseCases for self-containment) ---

    private fun copyPhysical(
        source: File,
        dest: File,
        cr: android.content.ContentResolver,
        tree: com.example.newaudio.domain.usecase.file.SafTreeAccess.TreeInfo?
    ): Boolean {
        // A. SAF Copy
        if (tree != null && SafTreeAccess.hasPersistedWritePermission(cr, tree.treeUri)) {
            try {
                val targetDirUri = SafTreeAccess.documentUriForFsPath(tree, dest.parent ?: "")
                if (targetDirUri != null) {
                    val targetDirDoc = DocumentFile.fromTreeUri(application, targetDirUri)
                    if (targetDirDoc != null && targetDirDoc.canWrite()) {
                        val mimeType = getMimeType(dest)
                        val destDoc = targetDirDoc.createFile(mimeType, dest.name)
                        val destUri = destDoc?.uri
                        if (destDoc != null && destUri != null) {
                            if (source.isDirectory) {
                                // Copying folders via SAF is very complex (recursively creating DocumentFiles).
                                // Simple fallback: only try File API for folders here,
                                // or would need a complex recursive implementation.
                                // For now: folder copy via SAF is NOT implemented here,
                                // as it is out of scope. If 'source' is a folder, fall back.
                                return false
                            }

                            FileInputStream(source).use { input ->
                                cr.openOutputStream(destUri)?.use { output ->
                                    input.copyTo(output)
                                }
                            }
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "SAF copy failed")
            }
        }

        // B. Legacy File API
        return try {
            if (source.isDirectory) {
                source.copyRecursively(dest, overwrite = false)
            } else {
                FileInputStream(source).use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "Legacy copy failed")
            false
        }
    }

    private fun deletePhysical(
        file: File,
        cr: android.content.ContentResolver,
        tree: com.example.newaudio.domain.usecase.file.SafTreeAccess.TreeInfo?
    ): Boolean {
        // A. SAF Delete
        if (tree != null && SafTreeAccess.hasPersistedWritePermission(cr, tree.treeUri)) {
            val docUri = SafTreeAccess.documentUriForFsPath(tree, file.absolutePath)
            if (docUri != null) {
                return try {
                    SafTreeAccess.deleteRecursively(cr, tree, docUri)
                } catch (e: Exception) {
                    false
                }
            }
        }

        // B. Legacy Delete
        return try {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (e: Exception) {
            false
        }
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
    }
}