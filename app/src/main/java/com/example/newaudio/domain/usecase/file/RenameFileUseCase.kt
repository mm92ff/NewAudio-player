package com.example.newaudio.domain.usecase.file

import android.app.Application
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.repository.IVideoMarkerRepository
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class RenameFileUseCase @Inject constructor(
    private val songDao: SongDao,
    private val videoDao: VideoDao,
    private val application: Application,
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    private val videoMarkerRepository: IVideoMarkerRepository
) {
    suspend operator fun invoke(fileItem: FileItem, newName: String): Boolean {
        if (fileItem !is FileItem.AudioFile && fileItem !is FileItem.VideoFile) {
            Timber.tag("RenameFileUseCase").e("Attempted to rename unsupported file item.")
            return false
        }
        if (newName.isBlank()) return false

        return withContext(Dispatchers.IO) {
            val cr = application.contentResolver

            val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()
            val treePath = when (fileItem) {
                is FileItem.VideoFile -> settings?.videoFolderPath
                else -> settings?.musicFolderPath
            }
            val tree = treePath?.let { SafTreeAccess.parseTree(it) }
            if (tree == null) {
                Timber.tag("RenameFileUseCase").e("No SAF tree configured.")
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
                val newUri = SafTreeAccess.renameDocument(cr, docUri, newName) ?: run {
                    Timber.tag("RenameFileUseCase").e("renameDocument returned null for $docUri")
                    return@withContext false
                }

                val actualName = SafTreeAccess.queryDisplayName(cr, newUri) ?: newName

                val oldPath = SafTreeAccess.normalizeFsPath(fileItem.path)
                val parent = oldPath.substringBeforeLast('/', missingDelimiterValue = "")
                if (parent.isBlank()) return@withContext false

                val newPath = SafTreeAccess.joinFs(parent, actualName)

                when (fileItem) {
                    is FileItem.AudioFile -> songDao.updatePath(
                        oldPath = oldPath,
                        newPath = newPath,
                        newContentUri = newPath,
                        newParentPath = parent,
                        newFilename = actualName
                    )
                    is FileItem.VideoFile -> {
                        videoDao.updatePath(
                            oldPath = oldPath,
                            newPath = newPath,
                            newContentUri = newPath,
                            newParentPath = parent,
                            newFilename = actualName
                        )
                        videoMarkerRepository.updateVideoPath(oldPath, newPath)
                    }
                    else -> Unit
                }

                true
            } catch (e: Exception) {
                Timber.tag("RenameFileUseCase").e(e, "SAF rename failed for ${fileItem.path}")
                false
            }
        }
    }
}
