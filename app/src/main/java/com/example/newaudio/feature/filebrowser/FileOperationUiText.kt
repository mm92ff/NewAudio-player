package com.example.newaudio.feature.filebrowser

import com.example.newaudio.R
import com.example.newaudio.domain.usecase.file.FileOperationFailureReason
import com.example.newaudio.domain.usecase.file.FileOperationResult
import com.example.newaudio.util.UiText

internal fun FileOperationResult.toErrorUiText(): UiText {
    val resource = when (failures.firstOrNull()?.reason) {
        FileOperationFailureReason.INVALID_REQUEST -> R.string.file_operation_invalid
        FileOperationFailureReason.DESTINATION_EXISTS -> R.string.file_destination_exists
        FileOperationFailureReason.COPY_FAILED -> R.string.copy_failed
        FileOperationFailureReason.RENAME_FAILED -> R.string.file_rename_failed
        FileOperationFailureReason.DELETE_FAILED -> R.string.file_delete_failed
        FileOperationFailureReason.DATABASE_UPDATE_FAILED -> R.string.file_database_update_failed
        FileOperationFailureReason.ROLLBACK_FAILED -> R.string.file_rollback_failed
        FileOperationFailureReason.SOURCE_DELETE_FAILED -> R.string.file_source_delete_failed
        FileOperationFailureReason.MEDIA_SCAN_FAILED -> R.string.file_media_scan_failed
        null -> R.string.error_loading
    }
    return UiText.StringResource(resource)
}
