package com.example.newaudio.domain.usecase.file

import com.example.newaudio.data.database.DatabaseTransactionRunner
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.repository.IVideoMarkerRepository
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

class DeleteFileUseCase @Inject constructor(
    private val songDao: SongDao,
    private val videoDao: VideoDao,
    private val storage: SafeStorageOperations,
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    private val videoMarkerRepository: IVideoMarkerRepository,
    private val transactionRunner: DatabaseTransactionRunner
) {
    @Suppress("UNUSED_PARAMETER")
    suspend operator fun invoke(
        parentPath: String,
        fileItem: FileItem
    ): FileOperationResult = withContext(Dispatchers.IO) {
        val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()
        val musicTree = settings?.musicFolderPath?.let(SafTreeAccess::parseTree)
        val videoTree = settings?.videoFolderPath?.let(SafTreeAccess::parseTree)
        val tree = treeForItem(fileItem, musicTree, videoTree)

        if (!storage.delete(File(fileItem.path), tree)) {
            return@withContext FileOperationResult.failure(
                fileItem.path,
                FileOperationFailureReason.DELETE_FAILED
            )
        }

        try {
            transactionRunner.run {
                when (fileItem) {
                    is FileItem.AudioFile -> songDao.deleteByPath(fileItem.path)
                    is FileItem.VideoFile -> {
                        videoMarkerRepository.deleteMarkersForVideo(fileItem.path)
                        videoDao.deleteByPath(fileItem.path)
                    }
                    is FileItem.Folder -> {
                        videoMarkerRepository.deleteMarkersForFolder(fileItem.path)
                        songDao.deleteByFolder(fileItem.path)
                        videoDao.deleteByFolder(fileItem.path)
                    }
                    is FileItem.OtherFile -> Unit
                }
            }
            FileOperationResult.success(1)
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Database cleanup failed after delete: ${fileItem.path}")
            FileOperationResult.failure(
                fileItem.path,
                FileOperationFailureReason.DATABASE_UPDATE_FAILED
            )
        }
    }

    internal fun treeForItem(
        item: FileItem,
        musicTree: SafTreeAccess.TreeInfo?,
        videoTree: SafTreeAccess.TreeInfo?
    ): SafTreeAccess.TreeInfo? = listOf(musicTree, videoTree)
        .filterNotNull()
        .firstOrNull { tree -> SafTreeAccess.containsFsPath(tree, item.path) }

    private companion object {
        const val TAG = "DeleteFileUseCase"
    }
}
