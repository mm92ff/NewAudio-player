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

class MoveMultipleFilesUseCase @Inject constructor(
    private val songDao: SongDao,
    private val videoDao: VideoDao,
    private val storage: SafeStorageOperations,
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    private val videoMarkerRepository: IVideoMarkerRepository,
    private val transactionRunner: DatabaseTransactionRunner
) {
    @Suppress("UNUSED_PARAMETER")
    suspend operator fun invoke(
        items: List<FileItem>,
        sourceParent: String,
        targetParent: String
    ): FileOperationResult = withContext(Dispatchers.IO) {
        var completedItems = 0
        val failures = mutableListOf<FileOperationFailure>()
        val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()
        val musicTree = settings?.musicFolderPath?.let(SafTreeAccess::parseTree)
        val videoTree = settings?.videoFolderPath?.let(SafTreeAccess::parseTree)

        for (item in items) {
            val source = File(item.path)
            val requestedDestination = File(targetParent, source.name)
            val sourceTree = treeForPath(source.absolutePath, musicTree, videoTree)
            val targetTree = treeForPath(requestedDestination.parent.orEmpty(), musicTree, videoTree)

            if (storage.destinationExists(requestedDestination, targetTree)) {
                failures += FileOperationFailure(item.path, FileOperationFailureReason.DESTINATION_EXISTS)
                continue
            }

            // Raw java.io.File renames bypass the persisted SAF grant and cannot provide a
            // trustworthy destination content URI. Use it only for entirely legacy paths.
            val renamed = sourceTree == null && targetTree == null && source.renameTo(requestedDestination)
            val copy = if (renamed) {
                null
            } else {
                storage.copyVerified(source, requestedDestination, sourceTree, targetTree)
            }
            if (!renamed && copy == null) {
                failures += FileOperationFailure(item.path, FileOperationFailureReason.COPY_FAILED)
                continue
            }

            val moveKind = if (renamed) PhysicalMoveKind.RENAMED else PhysicalMoveKind.COPIED
            val actualDestination = copy?.destination ?: requestedDestination
            val destinationContentUri = copy?.contentUri ?: actualDestination.absolutePath

            try {
                updateDatabase(item, actualDestination, destinationContentUri, targetTree)
            } catch (error: Exception) {
                Timber.e(error, "Database update failed for move: ${item.path}")
                val rolledBack = rollbackPhysicalMove(source, actualDestination, moveKind, targetTree)
                failures += FileOperationFailure(
                    item.path,
                    if (rolledBack) {
                        FileOperationFailureReason.DATABASE_UPDATE_FAILED
                    } else {
                        FileOperationFailureReason.ROLLBACK_FAILED
                    }
                )
                continue
            }

            if (moveKind == PhysicalMoveKind.COPIED && !storage.delete(source, sourceTree)) {
                // The verified destination remains authoritative and the source remains as a safe duplicate.
                failures += FileOperationFailure(item.path, FileOperationFailureReason.SOURCE_DELETE_FAILED)
            } else {
                completedItems++
            }
        }

        FileOperationResult(completedItems, failures)
    }

    private suspend fun updateDatabase(
        item: FileItem,
        destination: File,
        contentUri: String,
        targetTree: SafTreeAccess.TreeInfo?
    ) {
        transactionRunner.run {
            when (item) {
                is FileItem.AudioFile -> updateSongPath(item.path, destination, contentUri)
                is FileItem.VideoFile -> updateVideoPath(item.path, destination, contentUri)
                is FileItem.Folder -> updateFolderPaths(item.path, destination.absolutePath, targetTree)
                is FileItem.OtherFile -> Unit
            }
        }
    }

    private suspend fun updateSongPath(
        oldPath: String,
        destination: File,
        contentUri: String = destination.absolutePath
    ) {
        songDao.updatePath(
            oldPath = oldPath,
            newPath = destination.absolutePath,
            newContentUri = contentUri,
            newParentPath = destination.parent.orEmpty(),
            newFilename = destination.name
        )
    }

    private suspend fun updateVideoPath(
        oldPath: String,
        destination: File,
        contentUri: String = destination.absolutePath
    ) {
        videoDao.updatePath(
            oldPath = oldPath,
            newPath = destination.absolutePath,
            newContentUri = contentUri,
            newParentPath = destination.parent.orEmpty(),
            newFilename = destination.name
        )
        videoMarkerRepository.updateVideoPath(oldPath, destination.absolutePath)
    }

    private suspend fun updateFolderPaths(
        oldFolderPath: String,
        newFolderPath: String,
        targetTree: SafTreeAccess.TreeInfo?
    ) {
        val normalizedOld = oldFolderPath.trimEnd('/', File.separatorChar)
        val normalizedNew = newFolderPath.trimEnd('/', File.separatorChar)

        songDao.getAllSongsInTree(normalizedOld).forEach { song ->
            val destination = File(replacePathPrefix(song.path, normalizedOld, normalizedNew))
            updateSongPath(song.path, destination, contentUriFor(destination, targetTree))
        }
        videoDao.getAllVideosInTree(normalizedOld).forEach { video ->
            val destination = File(replacePathPrefix(video.path, normalizedOld, normalizedNew))
            updateVideoPath(video.path, destination, contentUriFor(destination, targetTree))
        }
    }

    private fun contentUriFor(
        destination: File,
        targetTree: SafTreeAccess.TreeInfo?
    ): String = targetTree
        ?.let { tree -> SafTreeAccess.documentUriForFsPath(tree, destination.absolutePath) }
        ?.toString()
        ?: destination.absolutePath

    private fun replacePathPrefix(path: String, oldPrefix: String, newPrefix: String): String {
        val suffix = path.removePrefix(oldPrefix).trimStart('/', File.separatorChar)
        return if (suffix.isEmpty()) newPrefix else File(newPrefix, suffix).absolutePath
    }

    private fun rollbackPhysicalMove(
        source: File,
        destination: File,
        kind: PhysicalMoveKind,
        tree: SafTreeAccess.TreeInfo?
    ): Boolean {
        val rolledBack = when (kind) {
            PhysicalMoveKind.RENAMED -> destination.renameTo(source)
            PhysicalMoveKind.COPIED -> storage.delete(destination, tree)
        }
        if (!rolledBack) Timber.e("Could not roll back failed move: ${source.path} -> ${destination.path}")
        return rolledBack
    }

    private fun treeForPath(
        path: String,
        musicTree: SafTreeAccess.TreeInfo?,
        videoTree: SafTreeAccess.TreeInfo?
    ): SafTreeAccess.TreeInfo? = listOf(musicTree, videoTree)
        .filterNotNull()
        .firstOrNull { SafTreeAccess.containsFsPath(it, path) }

    private enum class PhysicalMoveKind { RENAMED, COPIED }
}
