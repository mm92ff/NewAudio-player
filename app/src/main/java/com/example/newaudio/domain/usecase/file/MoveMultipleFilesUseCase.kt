package com.example.newaudio.domain.usecase.file

import android.app.Application
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.repository.IMediaScannerRepository
import com.example.newaudio.domain.repository.IVideoMarkerRepository
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
    private val videoDao: VideoDao,
    private val application: Application,
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    private val mediaScannerRepository: IMediaScannerRepository,
    private val videoMarkerRepository: IVideoMarkerRepository
) {
    suspend operator fun invoke(items: List<FileItem>, sourceParent: String, targetParent: String): Boolean = withContext(Dispatchers.IO) {
        var allSuccess = true
        val cr = application.contentResolver
        val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()
        val musicTree = settings?.musicFolderPath?.let { SafTreeAccess.parseTree(it) }
        val videoTree = settings?.videoFolderPath?.let { SafTreeAccess.parseTree(it) }

        for (item in items) {
            val sourceFile = File(item.path)
            val destFile = File(targetParent, sourceFile.name)
            val tree = treeForItem(item, musicTree, videoTree)

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
                    when (item) {
                        is FileItem.AudioFile -> {
                            // Update path in DB (preserves playcounts, ratings, etc.)
                            songDao.updatePath(
                                oldPath = item.path,
                                newPath = destFile.absolutePath,
                                newContentUri = destFile.absolutePath,
                                newParentPath = targetParent,
                                newFilename = destFile.name
                            )
                        }
                        is FileItem.VideoFile -> {
                            videoDao.updatePath(
                                oldPath = item.path,
                                newPath = destFile.absolutePath,
                                newContentUri = destFile.absolutePath,
                                newParentPath = targetParent,
                                newFilename = destFile.name
                            )
                            videoMarkerRepository.updateVideoPath(
                                oldPath = item.path,
                                newPath = destFile.absolutePath
                            )
                        }
                        is FileItem.Folder -> {
                            preserveMovedVideoRows(
                                oldFolderPath = item.path,
                                newFolderPath = destFile.absolutePath
                            )
                            // Folder: delete DB entries & rescan
                            songDao.deleteByFolder(item.path)
                            videoDao.deleteByFolder(item.path)
                            scanFolderRecursively(destFile)
                        }
                        is FileItem.OtherFile -> Unit
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

    private suspend fun preserveMovedVideoRows(oldFolderPath: String, newFolderPath: String) {
        val normalizedOld = oldFolderPath.trimEnd('/')
        val normalizedNew = newFolderPath.trimEnd('/')
        videoDao.getAllVideosInTree(normalizedOld).forEach { video ->
            val suffix = video.path.removePrefix(normalizedOld).trimStart('/')
            val newPath = if (suffix.isBlank()) normalizedNew else "$normalizedNew/$suffix"
            val newParent = File(newPath).parent ?: normalizedNew
            val newFilename = File(newPath).name

            videoDao.updatePath(
                oldPath = video.path,
                newPath = newPath,
                newContentUri = newPath,
                newParentPath = newParent,
                newFilename = newFilename
            )
        }
        videoMarkerRepository.updateVideoFolderPath(normalizedOld, normalizedNew)
    }

    private suspend fun scanFolderRecursively(folder: File) {
        folder.walk().forEach { file ->
            if (!file.isFile) return@forEach

            val scannerCall: (suspend (String) -> Unit)? = when {
                isAudioFile(file.name) -> mediaScannerRepository::scanSingleFile
                isVideoFile(file.name) -> mediaScannerRepository::scanSingleVideoFile
                else -> null
            }

            if (scannerCall != null) {
                try {
                    scannerCall(file.absolutePath)
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

    private fun isVideoFile(filename: String): Boolean {
        val lower = filename.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".m4v") || lower.endsWith(".mkv") ||
            lower.endsWith(".webm") || lower.endsWith(".avi") || lower.endsWith(".mov") ||
            lower.endsWith(".3gp")
    }

    private fun treeForItem(
        item: FileItem,
        musicTree: SafTreeAccess.TreeInfo?,
        videoTree: SafTreeAccess.TreeInfo?
    ): SafTreeAccess.TreeInfo? {
        return when (item) {
            is FileItem.AudioFile -> musicTree
            is FileItem.VideoFile -> videoTree
            is FileItem.Folder -> listOf(videoTree, musicTree)
                .filterNotNull()
                .firstOrNull { tree -> SafTreeAccess.documentUriForFsPath(tree, item.path) != null }
            is FileItem.OtherFile -> musicTree ?: videoTree
        }
    }

    // --- SAF helper methods (duplicated from Copy/Delete UseCases for self-containment) ---

    private fun copyPhysical(
        source: File,
        dest: File,
        cr: android.content.ContentResolver,
        tree: com.example.newaudio.domain.usecase.file.SafTreeAccess.TreeInfo?
    ): Boolean {
        // A. SAF Copy
        if (!source.isDirectory && tree != null && SafTreeAccess.hasPersistedWritePermission(cr, tree.treeUri)) {
            try {
                val targetDirUri = SafTreeAccess.documentUriForFsPath(tree, dest.parent ?: "")
                if (targetDirUri != null) {
                    val targetDirDoc = DocumentFile.fromTreeUri(application, targetDirUri)
                    if (targetDirDoc != null && targetDirDoc.canWrite()) {
                        val mimeType = getMimeType(dest)
                        val destDoc = targetDirDoc.createFile(mimeType, dest.name)
                        val destUri = destDoc?.uri
                        if (destDoc != null && destUri != null) {
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
