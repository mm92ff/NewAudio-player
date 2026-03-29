package com.example.newaudio.domain.usecase.file

import com.example.newaudio.domain.repository.IFolderOrderRepository
import javax.inject.Inject

class SaveFolderOrderUseCase @Inject constructor(
    private val folderOrderRepository: IFolderOrderRepository
) {
    suspend operator fun invoke(folderPath: String, fileNames: List<String>) {
        folderOrderRepository.saveFolderOrder(folderPath, fileNames)
    }
}
