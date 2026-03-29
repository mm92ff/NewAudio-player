package com.example.newaudio.domain.usecase.file

import android.app.Application
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class DeleteMultipleFilesUseCase @Inject constructor(
    private val songDao: SongDao,
    private val application: Application,
    private val getUserSettingsUseCase: GetUserSettingsUseCase
) {
    /**
     * Deletes multiple files/folders and cleans up the DB in batches.
     * @return true if ALL were successful, false if at least one failed.
     */
    suspend operator fun invoke(parentPath: String, items: List<FileItem>): Boolean = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext true

        val cr = application.contentResolver
        val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()

        // TreeInfo is inferred here based on the return type of parseTree
        val tree = settings?.musicFolderPath?.let { SafTreeAccess.parseTree(it) }

        // Lists for batch DB cleanup
        val successfulFilePaths = mutableListOf<String>()
        val successfulFolderPaths = mutableListOf<String>()
        var allSuccess = true

        // 1. Physical deletion (iterative, as filesystem operations are atomic)
        for (item in items) {
            val success = deletePhysical(item, cr, tree)
            if (success) {
                if (item is FileItem.Folder) {
                    successfulFolderPaths.add(item.path)
                } else {
                    successfulFilePaths.add(item.path)
                }
            } else {
                allSuccess = false
            }
        }

        // 2. Database cleanup (batch!)
        try {
            if (successfulFilePaths.isNotEmpty()) {
                songDao.deleteByPaths(successfulFilePaths)
            }

            for (folderPath in successfulFolderPaths) {
                songDao.deleteByFolder(folderPath)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up DB after multiple delete")
        }

        return@withContext allSuccess
    }

    private fun deletePhysical(
        item: FileItem,
        cr: android.content.ContentResolver,
        // ✅ FIX: Type explicitly set to TreeInfo (instead of Tree)
        tree: com.example.newaudio.domain.usecase.file.SafTreeAccess.TreeInfo?
    ): Boolean {
        // A. Try via SAF (Scoped Storage)
        if (tree != null && SafTreeAccess.hasPersistedWritePermission(cr, tree.treeUri)) {
            val docUri = SafTreeAccess.documentUriForFsPath(tree, item.path)
            if (docUri != null) {
                return try {
                    SafTreeAccess.deleteRecursively(cr, tree, docUri)
                } catch (e: Exception) {
                    Timber.e(e, "SAF delete failed for ${item.path}")
                    false
                }
            }
        }

        // B. Fallback (File API)
        return try {
            val file = File(item.path)
            if (item is FileItem.Folder) {
                file.exists() && file.deleteRecursively()
            } else {
                file.exists() && file.delete()
            }
        } catch (e: Exception) {
            Timber.e(e, "Legacy delete failed for ${item.path}")
            false
        }
    }
}