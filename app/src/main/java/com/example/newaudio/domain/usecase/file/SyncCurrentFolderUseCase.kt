package com.example.newaudio.domain.usecase.file

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import androidx.room.withTransaction
import com.example.newaudio.data.database.AppDatabase
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.SongEntity
import com.example.newaudio.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class SyncCurrentFolderUseCase @Inject constructor(
    private val songDao: SongDao,
    private val appDatabase: AppDatabase,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val supportedExtensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "aac", "opus", "wma")

    private companion object {
        const val TAG = "SyncFolder"
        const val DELETE_CHUNK_SIZE = 500   // SQLite bind limit safe
        const val INSERT_CHUNK_SIZE = 250
    }

    suspend operator fun invoke(folderPath: String) = withContext(ioDispatcher) {
        val targetPath = folderPath.removeSuffix("/")
        Timber.tag(TAG).d("--- FINAL SYNC: $targetPath ---")

        val rootFile = File(targetPath)
        if (!rootFile.exists() || !rootFile.isDirectory) {
            Timber.tag(TAG).e("Folder does not exist: $targetPath")
            return@withContext
        }

        // 1) FILESYSTEM
        val fsFiles = mutableListOf<File>()
        rootFile.walkTopDown().forEach { file ->
            if (file.isFile && supportedExtensions.contains(file.extension.lowercase())) {
                fsFiles.add(file)
            }
        }

        val fsMap = fsFiles.associateBy { it.safeCanonicalPath() }
        Timber.tag(TAG).d("FileSystem found: ${fsMap.size} files")

        // 2) DATABASE (Tree)
        val dbSongs = songDao.getAllSongsInTree(targetPath)
        val dbPaths = dbSongs.map { File(it.path).safeCanonicalPath() }.toHashSet()
        Timber.tag(TAG).d("Database knows: ${dbPaths.size} files")

        // 3) DELETE (in DB but not on disk)
        val pathsToDelete = dbSongs
            .filter { entity -> !fsMap.containsKey(File(entity.path).safeCanonicalPath()) }
            .map { it.path } // original path for delete

        // 4) INSERT (on disk but not in DB)
        val newFiles = fsFiles.filter { file -> !dbPaths.contains(file.safeCanonicalPath()) }

        // Prepare entities (outside Tx -> keeps the transaction short)
        val newEntities = if (newFiles.isNotEmpty()) {
            Timber.tag(TAG).i("Processing ${newFiles.size} NEW files...")
            newFiles.map { safeCreateSongEntity(it) }
        } else {
            emptyList()
        }

        if (pathsToDelete.isEmpty() && newEntities.isEmpty()) {
            Timber.tag(TAG).d("No DB changes needed.")
            return@withContext
        }

        // ✅ 5) ONE TRANSACTION: bulk delete + bulk insert
        appDatabase.withTransaction {
            if (pathsToDelete.isNotEmpty()) {
                Timber.tag(TAG).d("Deleting ${pathsToDelete.size} missing files (bulk)")
                pathsToDelete.chunked(DELETE_CHUNK_SIZE).forEach { chunk ->
                    songDao.deleteByPaths(chunk)
                }
            }

            if (newEntities.isNotEmpty()) {
                newEntities.chunked(INSERT_CHUNK_SIZE).forEach { chunk ->
                    songDao.insertAll(chunk)
                }
                Timber.tag(TAG).d("SUCCESS: Inserted ${newEntities.size} files (bulk)")
            }
        }

        // Notify MediaScanner in the background (new files only)
        if (newFiles.isNotEmpty()) {
            val pathsToScan = newFiles.map { it.absolutePath }.toTypedArray()
            MediaScannerConnection.scanFile(context, pathsToScan, null, null)
        }
    }

    private fun File.safeCanonicalPath(): String =
        try { canonicalPath } catch (_: Exception) { absolutePath }

    private fun safeCreateSongEntity(file: File): SongEntity {
        val metadata = try {
            extractMetadata(file)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Metadata crash for ${file.name}, using fallback.")
            SimpleMetadata(
                title = file.nameWithoutExtension,
                artist = "<Unknown>",
                album = "<Unknown>",
                duration = 0L
            )
        }

        val parentPath = file.parent ?: ""
        return SongEntity(
            path = file.absolutePath,
            contentUri = file.absolutePath,
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            duration = metadata.duration,
            albumArtPath = null,
            parentPath = parentPath,
            filename = file.name,
            lastModified = file.lastModified(),
            size = file.length()
        )
    }

    data class SimpleMetadata(val title: String, val artist: String, val album: String, val duration: Long)

    private fun extractMetadata(file: File): SimpleMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "<Unknown>"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "<Unknown>"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L

            SimpleMetadata(title, artist, album, duration)
        } finally {
            try { retriever.release() } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to release MediaMetadataRetriever (non-fatal)")
            }
        }
    }
}
