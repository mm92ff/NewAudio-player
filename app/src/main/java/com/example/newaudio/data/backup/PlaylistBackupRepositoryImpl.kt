package com.example.newaudio.data.backup

import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IPlaylistBackupRepository
import com.example.newaudio.domain.repository.ImportResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistBackupRepositoryImpl @Inject constructor(
    private val exporter: PlaylistBackupExporter,
    private val importer: PlaylistBackupImporter
) : IPlaylistBackupRepository {

    override suspend fun exportPlaylists(
        filePath: String,
        userPreferences: UserPreferences
    ): Boolean = exporter.export(filePath, userPreferences)

    override suspend fun importPlaylists(filePath: String): ImportResult =
        importer.import(filePath)
}
