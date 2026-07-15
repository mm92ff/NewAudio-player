package com.example.newaudio.data.backup

import com.example.newaudio.domain.repository.ImportFailure

internal class PlaylistBackupException(
    val failure: ImportFailure,
    cause: Throwable? = null
) : Exception(failure.name, cause)
