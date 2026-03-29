package com.example.newaudio.domain.usecase.file

import com.example.newaudio.domain.model.FileItem
import java.io.File
import javax.inject.Inject

class CopyFileUseCase @Inject constructor() {
    suspend operator fun invoke(fileItem: FileItem, targetParentPath: String): Boolean {
        return try {
            val sourceFile = File(fileItem.path)
            val destFile = File(targetParentPath, sourceFile.name)
            
            sourceFile.copyTo(destFile, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }
}
