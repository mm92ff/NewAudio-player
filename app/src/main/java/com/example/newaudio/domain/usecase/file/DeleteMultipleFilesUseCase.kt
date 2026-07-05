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
import java.io.File
import javax.inject.Inject

class DeleteMultipleFilesUseCase @Inject constructor(
    private val songDao: SongDao,
    private val videoDao: VideoDao,
    private val application: Application,
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    private val videoMarkerRepository: IVideoMarkerRepository
) {
    /**
     * Deletes multiple files/folders and cleans up the DB in batches.
     * @return true if ALL were successful, false if at least one failed.
     */
    suspend operator fun invoke(parentPath: String, items: List<FileItem>): Boolean = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext true

        val cr = application.contentResolver
        val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()

        val musicTree = settings?.musicFolderPath?.let { SafTreeAccess.parseTree(it) }
        val videoTree = settings?.videoFolderPath?.let { SafTreeAccess.parseTree(it) }

        // Lists for batch DB cleanup
        val successfulAudioFilePaths = mutableListOf<String>()
        val successfulVideoFilePaths = mutableListOf<String>()
        val successfulFolderPaths = mutableListOf<String>()
        var allSuccess = true

        // 1. Physical deletion (iterative, as filesystem operations are atomic)
        for (item in items) {
            val success = deletePhysical(item, cr, treeForItem(item, musicTree, videoTree))
            if (success) {
                when (item) {
                    is FileItem.AudioFile -> successfulAudioFilePaths.add(item.path)
                    is FileItem.VideoFile -> successfulVideoFilePaths.add(item.path)
                    is FileItem.Folder -> successfulFolderPaths.add(item.path)
                    is FileItem.OtherFile -> {}
                }
            } else {
                allSuccess = false
            }
        }

        // 2. Database cleanup (batch!)
        try {
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
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up DB after multiple delete")
        }

        return@withContext allSuccess
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
