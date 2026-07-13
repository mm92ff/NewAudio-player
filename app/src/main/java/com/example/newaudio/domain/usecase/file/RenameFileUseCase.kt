package com.example.newaudio.domain.usecase.file

import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.data.database.DatabaseTransactionRunner
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
    private val storage: SafeStorageOperations,
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    private val videoMarkerRepository: IVideoMarkerRepository,
    private val transactionRunner: DatabaseTransactionRunner
) {
    suspend operator fun invoke(fileItem: FileItem, newName: String): FileOperationResult {
        if (fileItem !is FileItem.AudioFile && fileItem !is FileItem.VideoFile) {
            Timber.tag("RenameFileUseCase").e("Attempted to rename unsupported file item.")
            return FileOperationResult.failure(fileItem.path, FileOperationFailureReason.INVALID_REQUEST)
        }
        if (newName.isBlank()) {
            return FileOperationResult.failure(fileItem.path, FileOperationFailureReason.INVALID_REQUEST)
        }

        return withContext(Dispatchers.IO) {
            val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()
            val trees = listOfNotNull(
                settings?.musicFolderPath?.let(SafTreeAccess::parseTree),
                settings?.videoFolderPath?.let(SafTreeAccess::parseTree)
            )
            val tree = trees.firstOrNull { SafTreeAccess.containsFsPath(it, fileItem.path) }
            var renameResult: SafeStorageOperations.RenameResult? = null
            var renamedPath: String? = null
            try {
                val result = storage.rename(java.io.File(fileItem.path), newName, tree) ?: run {
                    Timber.tag("RenameFileUseCase").e("Storage rename failed for ${fileItem.path}")
                    return@withContext FileOperationResult.failure(
                        fileItem.path,
                        FileOperationFailureReason.RENAME_FAILED
                    )
                }
                renameResult = result
                val actualName = result.actualName

                val oldPath = SafTreeAccess.normalizeFsPath(fileItem.path)
                val parent = oldPath.substringBeforeLast('/', missingDelimiterValue = "")
                if (parent.isBlank()) {
                    storage.rollbackRename(result, fileItem.path)
                    return@withContext FileOperationResult.failure(
                        fileItem.path,
                        FileOperationFailureReason.INVALID_REQUEST
                    )
                }

                val newPath = SafTreeAccess.joinFs(parent, actualName)
                renamedPath = newPath

                transactionRunner.run {
                    when (fileItem) {
                        is FileItem.AudioFile -> songDao.updatePath(
                            oldPath = oldPath,
                            newPath = newPath,
                            newContentUri = result.contentUri,
                            newParentPath = parent,
                            newFilename = actualName
                        )
                        is FileItem.VideoFile -> {
                            videoDao.updatePath(
                                oldPath = oldPath,
                                newPath = newPath,
                                newContentUri = result.contentUri,
                                newParentPath = parent,
                                newFilename = actualName
                            )
                            videoMarkerRepository.updateVideoPath(oldPath, newPath)
                        }
                        else -> Unit
                    }
                }

                FileOperationResult.success(1)
            } catch (e: Exception) {
                Timber.tag("RenameFileUseCase").e(e, "SAF rename failed for ${fileItem.path}")
                val result = renameResult
                if (result == null) {
                    return@withContext FileOperationResult.failure(
                        fileItem.path,
                        FileOperationFailureReason.RENAME_FAILED
                    )
                }
                val rolledBack = renamedPath?.let { storage.rollbackRename(result, it) } == true
                FileOperationResult.failure(
                    fileItem.path,
                    if (rolledBack) {
                        FileOperationFailureReason.DATABASE_UPDATE_FAILED
                    } else {
                        FileOperationFailureReason.ROLLBACK_FAILED
                    }
                )
            }
        }
    }
}
