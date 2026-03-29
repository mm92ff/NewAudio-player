package com.example.newaudio.domain.usecase.file

import android.app.Application
import android.provider.DocumentsContract
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class RenameFileUseCase @Inject constructor(
    private val songDao: SongDao,
    private val application: Application,
    private val getUserSettingsUseCase: GetUserSettingsUseCase
) {
    suspend operator fun invoke(fileItem: FileItem, newName: String): Boolean {
        if (fileItem !is FileItem.AudioFile) {
            Timber.tag("RenameFileUseCase").e("Attempted to rename non-audio file item.")
            return false
        }
        if (newName.isBlank()) return false

        return withContext(Dispatchers.IO) {
            val cr = application.contentResolver

            val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()
            val tree = settings?.musicFolderPath?.let { SafTreeAccess.parseTree(it) }
            if (tree == null) {
                Timber.tag("RenameFileUseCase").e("No SAF tree configured (musicFolderPath is blank).")
                return@withContext false
            }
            if (!SafTreeAccess.hasPersistedWritePermission(cr, tree.treeUri)) {
                Timber.tag("RenameFileUseCase").e("No persisted WRITE permission for treeUri=${tree.treeUri}")
                return@withContext false
            }

            val docUri = SafTreeAccess.documentUriForFsPath(tree, fileItem.path)
            if (docUri == null) {
                Timber.tag("RenameFileUseCase").e("File not inside SAF tree. path=${fileItem.path}, base=${tree.baseFsPath}")
                return@withContext false
            }

            try {
                val newUri = DocumentsContract.renameDocument(cr, docUri, newName) ?: run {
                    Timber.tag("RenameFileUseCase").e("renameDocument returned null for $docUri")
                    return@withContext false
                }

                val actualName = SafTreeAccess.queryDisplayName(cr, newUri) ?: newName

                val oldPath = SafTreeAccess.normalizeFsPath(fileItem.path)
                val parent = oldPath.substringBeforeLast('/', missingDelimiterValue = "")
                if (parent.isBlank()) return@withContext false

                val newPath = SafTreeAccess.joinFs(parent, actualName)

                songDao.updatePath(
                    oldPath = oldPath,
                    newPath = newPath,
                    newParentPath = parent,
                    newFilename = actualName
                )

                true
            } catch (e: Exception) {
                Timber.tag("RenameFileUseCase").e(e, "SAF rename failed for ${fileItem.path}")
                false
            }
        }
    }
}
