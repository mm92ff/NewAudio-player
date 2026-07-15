package com.example.newaudio.fake

import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IPlaylistBackupRepository
import com.example.newaudio.domain.repository.ImportResult

class FakePlaylistBackupRepository : IPlaylistBackupRepository {
    var exportCalled = false
    var exportReturnValue = true
    var exportedPath: String? = null
    var exportedPreferences: UserPreferences? = null
    var importedPath: String? = null
    var importReturnPreferences: UserPreferences? = null
    var importShouldThrow = false
    var exportShouldThrow = false

    override suspend fun exportPlaylists(
        filePath: String,
        userPreferences: UserPreferences
    ): Boolean {
        if (exportShouldThrow) throw RuntimeException("Export failed")
        exportCalled = true
        exportedPath = filePath
        exportedPreferences = userPreferences
        return exportReturnValue
    }

    override suspend fun importPlaylists(filePath: String): ImportResult {
        if (importShouldThrow) throw RuntimeException("Import failed")
        importedPath = filePath
        return ImportResult(0, 0, 0, 0, importReturnPreferences)
    }
}
