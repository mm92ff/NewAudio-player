package com.example.newaudio.domain.usecase.file

import android.app.Application
import android.provider.DocumentsContract
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

enum class CreateFolderResult {
    SUCCESS,
    INVALID_NAME,
    ALREADY_EXISTS,
    FAILED
}

class CreateFolderUseCase @Inject constructor(
    private val application: Application,
    private val getUserSettingsUseCase: GetUserSettingsUseCase
) {
    suspend operator fun invoke(parentPath: String, folderName: String): CreateFolderResult = withContext(Dispatchers.IO) {
        val sanitizedName = folderName.trim()
        if (sanitizedName.isBlank() || sanitizedName.any { it in invalidCharacters }) {
            return@withContext CreateFolderResult.INVALID_NAME
        }

        val parent = File(parentPath)
        val target = File(parent, sanitizedName)
        if (target.exists()) {
            return@withContext CreateFolderResult.ALREADY_EXISTS
        }

        val cr = application.contentResolver
        val settings = runCatching { getUserSettingsUseCase().first() }.getOrNull()
        val tree = treeForParent(
            parentPath = parentPath,
            musicTree = settings?.musicFolderPath?.let { SafTreeAccess.parseTree(it) },
            videoTree = settings?.videoFolderPath?.let { SafTreeAccess.parseTree(it) }
        )

        if (tree != null && SafTreeAccess.hasPersistedWritePermission(cr, tree.treeUri)) {
            val parentDocUri = SafTreeAccess.documentUriForFsPath(tree, parentPath)
            if (parentDocUri != null) {
                val created = runCatching {
                    DocumentsContract.createDocument(
                        cr,
                        parentDocUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        sanitizedName
                    )
                }.getOrNull()

                if (created != null) {
                    return@withContext CreateFolderResult.SUCCESS
                }
            }
        }

        return@withContext try {
            if (target.mkdirs()) {
                CreateFolderResult.SUCCESS
            } else if (target.exists()) {
                CreateFolderResult.ALREADY_EXISTS
            } else {
                CreateFolderResult.FAILED
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create folder: ${target.path}")
            CreateFolderResult.FAILED
        }
    }

    internal fun treeForParent(
        parentPath: String,
        musicTree: SafTreeAccess.TreeInfo?,
        videoTree: SafTreeAccess.TreeInfo?
    ): SafTreeAccess.TreeInfo? {
        return listOf(videoTree, musicTree)
            .filterNotNull()
            .firstOrNull { tree -> SafTreeAccess.containsFsPath(tree, parentPath) }
    }

    private companion object {
        val invalidCharacters = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    }
}
