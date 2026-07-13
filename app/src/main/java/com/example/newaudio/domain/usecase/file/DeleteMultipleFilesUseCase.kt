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
import java.io.File
import javax.inject.Inject

class DeleteMultipleFilesUseCase @Inject constructor(
    private val songDao: SongDao,
    private val videoDao: VideoDao,
    private val storage: SafeStorageOperations,
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    private val videoMarkerRepository: IVideoMarkerRepository,
    private val transactionRunner: DatabaseTransactionRunner
) {
    /**
     * Deletes multiple files/folders and cleans up the DB in batches.
     * Returns a per-item result so physical and database failures remain distinguishable.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend operator fun invoke(parentPath: String, items: List<FileItem>): FileOperationResult = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext FileOperationResult.success(0)

        val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()

        val musicTree = settings?.musicFolderPath?.let { SafTreeAccess.parseTree(it) }
        val videoTree = settings?.videoFolderPath?.let { SafTreeAccess.parseTree(it) }

        // Lists for batch DB cleanup
        val successfulAudioFilePaths = mutableListOf<String>()
        val successfulVideoFilePaths = mutableListOf<String>()
        val successfulFolderPaths = mutableListOf<String>()
        val failures = mutableListOf<FileOperationFailure>()
        val physicallyDeletedItems = mutableListOf<FileItem>()

        // 1. Physical deletion (iterative, as filesystem operations are atomic)
        for (item in items) {
            val success = storage.delete(File(item.path), treeForItem(item, musicTree, videoTree))
            if (success) {
                physicallyDeletedItems += item
                when (item) {
                    is FileItem.AudioFile -> successfulAudioFilePaths.add(item.path)
                    is FileItem.VideoFile -> successfulVideoFilePaths.add(item.path)
                    is FileItem.Folder -> successfulFolderPaths.add(item.path)
                    is FileItem.OtherFile -> {}
                }
            } else {
                failures += FileOperationFailure(item.path, FileOperationFailureReason.DELETE_FAILED)
            }
        }

        // 2. Database cleanup (batch!)
        try {
            transactionRunner.run {
                if (successfulAudioFilePaths.isNotEmpty()) {
                    songDao.deleteByPaths(successfulAudioFilePaths)
                }
                if (successfulVideoFilePaths.isNotEmpty()) {
                    successfulVideoFilePaths.forEach { path ->
                        videoMarkerRepository.deleteMarkersForVideo(path)
                    }
                    videoDao.deleteByPaths(successfulVideoFilePaths)
                }

                for (folderPath in successfulFolderPaths) {
                    videoMarkerRepository.deleteMarkersForFolder(folderPath)
                    songDao.deleteByFolder(folderPath)
                    videoDao.deleteByFolder(folderPath)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up DB after multiple delete")
            failures += physicallyDeletedItems.map { item ->
                FileOperationFailure(item.path, FileOperationFailureReason.DATABASE_UPDATE_FAILED)
            }
            return@withContext FileOperationResult(0, failures)
        }

        return@withContext FileOperationResult(physicallyDeletedItems.size, failures)
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
                .firstOrNull { tree -> SafTreeAccess.containsFsPath(tree, item.path) }
            is FileItem.OtherFile -> musicTree ?: videoTree
        }
    }

}
