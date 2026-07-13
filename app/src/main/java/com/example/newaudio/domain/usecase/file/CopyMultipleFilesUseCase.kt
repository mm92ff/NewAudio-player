package com.example.newaudio.domain.usecase.file

import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.repository.IMediaScannerRepository
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

class CopyMultipleFilesUseCase @Inject constructor(
    private val storage: SafeStorageOperations,
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    private val mediaScannerRepository: IMediaScannerRepository
) {
    suspend operator fun invoke(
        items: List<FileItem>,
        targetPath: String
    ): FileOperationResult = withContext(Dispatchers.IO) {
        var completedItems = 0
        val failures = mutableListOf<FileOperationFailure>()
        val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()
        val musicTree = settings?.musicFolderPath?.let(SafTreeAccess::parseTree)
        val videoTree = settings?.videoFolderPath?.let(SafTreeAccess::parseTree)

        for (item in items) {
            val source = File(item.path)
            val destination = File(targetPath, source.name)
            val sourceTree = treeForPath(source.absolutePath, musicTree, videoTree)
            val targetTree = treeForPath(destination.parent.orEmpty(), musicTree, videoTree)

            if (storage.destinationExists(destination, targetTree)) {
                failures += FileOperationFailure(item.path, FileOperationFailureReason.DESTINATION_EXISTS)
                continue
            }

            val copy = storage.copyVerified(source, destination, sourceTree, targetTree)
            if (copy == null) {
                failures += FileOperationFailure(item.path, FileOperationFailureReason.COPY_FAILED)
                continue
            }

            try {
                when (item) {
                    is FileItem.AudioFile -> mediaScannerRepository.scanSingleFile(copy.destination.absolutePath)
                    is FileItem.VideoFile -> mediaScannerRepository.scanSingleVideoFile(copy.destination.absolutePath)
                    is FileItem.Folder -> scanFolderRecursively(copy.destination)
                    is FileItem.OtherFile -> Unit
                }
                completedItems++
            } catch (error: Exception) {
                Timber.e(error, "Failed to scan copied item: ${copy.destination.absolutePath}")
                failures += FileOperationFailure(item.path, FileOperationFailureReason.MEDIA_SCAN_FAILED)
            }
        }

        FileOperationResult(completedItems, failures)
    }

    private suspend fun scanFolderRecursively(folder: File) {
        folder.walkTopDown().filter(File::isFile).forEach { file ->
            when {
                isAudioFile(file.name) -> mediaScannerRepository.scanSingleFile(file.absolutePath)
                isVideoFile(file.name) -> mediaScannerRepository.scanSingleVideoFile(file.absolutePath)
            }
        }
    }

    private fun isAudioFile(filename: String): Boolean =
        filename.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

    private fun isVideoFile(filename: String): Boolean =
        filename.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    private fun treeForPath(
        path: String,
        musicTree: SafTreeAccess.TreeInfo?,
        videoTree: SafTreeAccess.TreeInfo?
    ): SafTreeAccess.TreeInfo? = listOf(musicTree, videoTree)
        .filterNotNull()
        .firstOrNull { SafTreeAccess.containsFsPath(it, path) }

    private companion object {
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "flac", "wav", "ogg")
        val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp")
    }
}
