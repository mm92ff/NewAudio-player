package com.example.newaudio.domain.usecase.file

import android.app.Application
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class MoveFileUseCase @Inject constructor(
    private val application: Application,
    private val songDao: SongDao,
    private val getUserSettingsUseCase: GetUserSettingsUseCase
) {
    suspend operator fun invoke(
        fileItem: FileItem,
        sourceParentPath: String,
        targetParentPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (fileItem !is FileItem.AudioFile) return@withContext false

        val cr = application.contentResolver
        val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()
        val tree = settings?.musicFolderPath?.let { SafTreeAccess.parseTree(it) }
            ?: return@withContext false

        if (!SafTreeAccess.hasPersistedWritePermission(cr, tree.treeUri)) {
            Timber.tag("MoveFileUseCase").e("No persisted WRITE permission for treeUri=${tree.treeUri}")
            return@withContext false
        }

        val srcDoc = SafTreeAccess.documentUriForFsPath(tree, fileItem.path)
        val srcParentDoc = SafTreeAccess.documentUriForFsPath(tree, sourceParentPath)
        val tgtParentDoc = SafTreeAccess.documentUriForFsPath(tree, targetParentPath)

        if (srcDoc == null || srcParentDoc == null || tgtParentDoc == null) {
            Timber.tag("MoveFileUseCase").e("Move outside SAF tree not supported. src=${fileItem.path}, srcParent=$sourceParentPath, tgtParent=$targetParentPath, base=${tree.baseFsPath}")
            return@withContext false
        }

        try {
            val newUri = SafTreeAccess.moveDocumentBestEffort(
                cr = cr,
                tree = tree,
                srcDocUri = srcDoc,
                srcParentDocUri = srcParentDoc,
                targetParentDocUri = tgtParentDoc
            ) ?: return@withContext false

            val actualName = SafTreeAccess.queryDisplayName(cr, newUri) ?: fileItem.name

            val oldPath = SafTreeAccess.normalizeFsPath(fileItem.path)
            val targetParent = SafTreeAccess.normalizeFsPath(targetParentPath)
            val newPath = SafTreeAccess.joinFs(targetParent, actualName)

            songDao.updatePath(
                oldPath = oldPath,
                newPath = newPath,
                newParentPath = targetParent,
                newFilename = actualName
            )

            true
        } catch (e: Exception) {
            Timber.tag("MoveFileUseCase").e(e, "SAF move failed for ${fileItem.path}")
            false
        }
    }
}
