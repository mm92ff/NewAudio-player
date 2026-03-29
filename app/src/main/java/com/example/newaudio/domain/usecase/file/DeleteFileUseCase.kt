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

class DeleteFileUseCase @Inject constructor(
    private val songDao: SongDao,
    private val application: Application,
    private val getUserSettingsUseCase: GetUserSettingsUseCase
) {
    suspend operator fun invoke(parentPath: String, fileItem: FileItem): Boolean = withContext(Dispatchers.IO) {
        val cr = application.contentResolver

        val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()
        val tree = settings?.musicFolderPath?.let { SafTreeAccess.parseTree(it) }

        // Wenn kein Tree gesetzt ist: fallback wie vorher (kann scheitern unter Scoped Storage)
        if (tree == null) {
            return@withContext legacyDeleteFallback(fileItem)
        }

        if (!SafTreeAccess.hasPersistedWritePermission(cr, tree.treeUri)) {
            Timber.tag("DeleteFileUseCase").e("No persisted WRITE permission for treeUri=${tree.treeUri}")
            return@withContext legacyDeleteFallback(fileItem)
        }

        val docUri = SafTreeAccess.documentUriForFsPath(tree, fileItem.path)
        if (docUri == null) {
            // outside tree -> fallback
            return@withContext legacyDeleteFallback(fileItem)
        }

        try {
            val ok = SafTreeAccess.deleteRecursively(cr, tree, docUri)
            if (!ok) return@withContext false

            when (fileItem) {
                is FileItem.AudioFile -> songDao.deleteByPath(fileItem.path)
                is FileItem.Folder -> songDao.deleteByFolder(fileItem.path)
                is FileItem.OtherFile -> { /* nichts in DB */ }
            }

            true
        } catch (e: Exception) {
            Timber.tag("DeleteFileUseCase").e(e, "SAF delete failed for ${fileItem.path}")
            false
        }
    }

    private suspend fun legacyDeleteFallback(fileItem: FileItem): Boolean {
        return try {
            val file = File(fileItem.path)
            val ok = if (fileItem is FileItem.Folder) {
                file.exists() && file.deleteRecursively()
            } else {
                file.exists() && file.delete()
            }

            if (ok) {
                when (fileItem) {
                    is FileItem.AudioFile -> songDao.deleteByPath(fileItem.path)
                    is FileItem.Folder -> songDao.deleteByFolder(fileItem.path)
                    is FileItem.OtherFile -> {}
                }
            }
            ok
        } catch (e: Exception) {
            Timber.tag("DeleteFileUseCase").e(e, "legacyDeleteFallback failed for ${fileItem.path}")
            false
        }
    }
}
