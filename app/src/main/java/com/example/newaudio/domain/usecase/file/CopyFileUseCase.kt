package com.example.newaudio.domain.usecase.file

import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.FileItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class CopyFileUseCase @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(fileItem: FileItem, targetParentPath: String): Boolean = withContext(ioDispatcher) {
        try {
            val sourceFile = File(fileItem.path)
            val destFile = File(targetParentPath, sourceFile.name)

            sourceFile.copyTo(destFile, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }
}
