package com.example.newaudio.domain.repository

import com.example.newaudio.domain.model.UserPreferences

interface IPlaylistBackupRepository {
    suspend fun exportPlaylists(filePath: String, userPreferences: UserPreferences): Boolean
    suspend fun importPlaylists(filePath: String): ImportResult
}

data class ImportResult(
    val playlistsImported: Int,
    val songsFound: Int,
    val songsFixed: Int,
    val songsNotFound: Int,
    val restoredPreferences: UserPreferences? = null,
    val failure: ImportFailure? = null
) {
    val isSuccess: Boolean get() = failure == null
}

enum class ImportFailure {
    NOT_FOUND,
    TOO_LARGE,
    INVALID_FORMAT,
    UNSUPPORTED_VERSION,
    LIMIT_EXCEEDED,
    IO_ERROR
}
