package com.example.newaudio.domain.usecase.file

data class FileOperationResult(
    val completedItems: Int,
    val failures: List<FileOperationFailure>
) {
    val isSuccess: Boolean get() = failures.isEmpty()

    companion object {
        fun success(completedItems: Int): FileOperationResult =
            FileOperationResult(completedItems, emptyList())

        fun failure(path: String, reason: FileOperationFailureReason): FileOperationResult =
            FileOperationResult(0, listOf(FileOperationFailure(path, reason)))
    }
}

data class FileOperationFailure(
    val path: String,
    val reason: FileOperationFailureReason
)

enum class FileOperationFailureReason {
    INVALID_REQUEST,
    DESTINATION_EXISTS,
    COPY_FAILED,
    RENAME_FAILED,
    DELETE_FAILED,
    DATABASE_UPDATE_FAILED,
    ROLLBACK_FAILED,
    SOURCE_DELETE_FAILED,
    MEDIA_SCAN_FAILED
}
