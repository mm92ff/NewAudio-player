package com.example.newaudio.domain.usecase.file

import android.app.Application
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
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

class CopyMultipleFilesUseCase @Inject constructor(
    private val application: Application,
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    private val mediaScannerRepository: IMediaScannerRepository
) {
    suspend operator fun invoke(items: List<FileItem>, targetPath: String): Boolean = withContext(Dispatchers.IO) {
        var allSuccess = true

        val cr = application.contentResolver
        val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()
        val tree = settings?.musicFolderPath?.let { SafTreeAccess.parseTree(it) }

        for (item in items) {
            val sourceFile = File(item.path)
            val destFile = File(targetPath, sourceFile.name)

            // Prevents overwriting if the destination already exists (simple check)
            if (destFile.exists()) {
                Timber.w("Destination exists, skipping: ${destFile.path}")
                allSuccess = false
                continue
            }

            // 1. Physical copy (with SAF support)
            val copySuccess = copyPhysical(sourceFile, destFile, cr, tree)

            // 2. Scan (insert into DB) so it's immediately visible
            if (copySuccess) {
                if (item is FileItem.AudioFile) {
                    try {
                        mediaScannerRepository.scanSingleFile(destFile.absolutePath)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to scan copied file: ${destFile.absolutePath}")
                    }
                } else if (item is FileItem.Folder) {
                    scanFolderRecursively(destFile)
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

    private fun copyPhysical(
        source: File,
        dest: File,
        cr: android.content.ContentResolver,
        tree: com.example.newaudio.domain.usecase.file.SafTreeAccess.TreeInfo?
    ): Boolean {
        // A. SAF (Storage Access Framework) logic
        // Required for Android 10+ and SD cards
        if (tree != null && SafTreeAccess.hasPersistedWritePermission(cr, tree.treeUri)) {
            try {
                // 1. Get URI of the destination FOLDER
                val targetDirUri = SafTreeAccess.documentUriForFsPath(tree, dest.parent ?: "")

                if (targetDirUri != null) {
                    // 2. Wrap as DocumentFile to be able to call createFile()
                    val targetDirDoc = DocumentFile.fromTreeUri(application, targetDirUri)

                    if (targetDirDoc != null && targetDirDoc.canWrite()) {
                        val mimeType = getMimeType(dest)
                        // 3. Create new empty file via SAF
                        val destDoc = targetDirDoc.createFile(mimeType, dest.name)

                        val destUri = destDoc?.uri
                        if (destDoc != null && destUri != null) {
                            // 4. Copy data streams: FileRead -> SAFWrite
                            FileInputStream(source).use { input ->
                                cr.openOutputStream(destUri)?.use { output ->
                                    input.copyTo(output)
                                }
                            }
                            return true // Success via SAF!
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "SAF copy failed: ${source.path} -> ${dest.path}")
                // Fallthrough to fallback attempt below (unlikely that legacy works if SAF fails)
            }
        }

        // B. Fallback: Standard File IO (works only in internal storage or on old Androids)
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
            Timber.e(e, "Legacy copy failed: ${source.path} -> ${dest.path}")
            false
        }
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
    }
}