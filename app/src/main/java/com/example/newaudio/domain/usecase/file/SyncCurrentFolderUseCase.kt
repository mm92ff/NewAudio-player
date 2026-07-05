package com.example.newaudio.domain.usecase.file

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import androidx.room.withTransaction
import com.example.newaudio.data.database.AppDatabase
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.SongEntity
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.data.database.VideoEntity
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.MediaBrowserMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.URI
import javax.inject.Inject

class SyncCurrentFolderUseCase @Inject constructor(
    private val songDao: SongDao,
    private val videoDao: VideoDao,
    private val appDatabase: AppDatabase,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val supportedAudioExtensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "aac", "opus", "wma")
    private val supportedVideoExtensions = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp")

    private companion object {
        const val TAG = "SyncFolder"
        const val DELETE_CHUNK_SIZE = 500
        const val INSERT_CHUNK_SIZE = 250
    }

    suspend operator fun invoke(
        folderPath: String,
        mode: MediaBrowserMode = MediaBrowserMode.MUSIC
    ) = withContext(ioDispatcher) {
        val targetPath = folderPath.removeSuffix("/")
        Timber.tag(TAG).d("--- FINAL SYNC: $targetPath mode=$mode ---")

        val rootFile = File(targetPath)
        if (!rootFile.exists() || !rootFile.isDirectory) {
            Timber.tag(TAG).e("Folder does not exist: $targetPath")
            return@withContext
        }

        val extensions = when (mode) {
            MediaBrowserMode.MUSIC -> supportedAudioExtensions
            MediaBrowserMode.VIDEO -> supportedVideoExtensions
        }

        val fsFiles = mutableListOf<File>()
        rootFile.walkTopDown().forEach { file ->
            if (file.isFile && extensions.contains(file.extension.lowercase())) {
                fsFiles.add(file)
            }
        }

        val fsMap = fsFiles.associateBy { it.safeCanonicalPath() }
        Timber.tag(TAG).d("FileSystem found: ${fsMap.size} files")

        val dbSongs = if (mode == MediaBrowserMode.MUSIC) {
            songDao.getAllSongsInTree(targetPath)
        } else {
            emptyList()
        }
        val dbVideos = if (mode == MediaBrowserMode.VIDEO) {
            videoDao.getAllVideosInTree(targetPath)
        } else {
            emptyList()
        }
        val dbPaths = when (mode) {
            MediaBrowserMode.MUSIC -> dbSongs.map { it.path }
            MediaBrowserMode.VIDEO -> dbVideos.map { it.path }
        }
        val dbPathSet = dbPaths.map { File(it).safeCanonicalPath() }.toHashSet()
        Timber.tag(TAG).d("Database knows: ${dbPathSet.size} files")

        val pathsToDelete = dbPaths
            .filter { path -> !fsMap.containsKey(File(path).safeCanonicalPath()) }

        val newFiles = fsFiles.filter { file -> !dbPathSet.contains(file.safeCanonicalPath()) }
        val contentUriRepairs = when (mode) {
            MediaBrowserMode.MUSIC -> dbSongs
                .filter { entity -> File(entity.path).safeCanonicalPath() in fsMap }
                .filter { entity -> entity.contentUri.needsFilePathRepair(entity.path) }
                .map { entity -> entity.path to File(entity.path).absolutePath }
            MediaBrowserMode.VIDEO -> dbVideos
                .filter { entity -> File(entity.path).safeCanonicalPath() in fsMap }
                .filter { entity -> entity.contentUri.needsFilePathRepair(entity.path) }
                .map { entity -> entity.path to File(entity.path).absolutePath }
        }

        if (pathsToDelete.isEmpty() && newFiles.isEmpty() && contentUriRepairs.isEmpty()) {
            Timber.tag(TAG).d("No DB changes needed.")
            return@withContext
        }

        appDatabase.withTransaction {
            if (contentUriRepairs.isNotEmpty()) {
                Timber.tag(TAG).d("Repairing ${contentUriRepairs.size} stale contentUri values")
                contentUriRepairs.forEach { (path, contentUri) ->
                    when (mode) {
                        MediaBrowserMode.MUSIC -> songDao.updateContentUri(path, contentUri)
                        MediaBrowserMode.VIDEO -> videoDao.updateContentUri(path, contentUri)
                    }
                }
            }

            if (pathsToDelete.isNotEmpty()) {
                Timber.tag(TAG).d("Deleting ${pathsToDelete.size} missing files (bulk)")
                pathsToDelete.chunked(DELETE_CHUNK_SIZE).forEach { chunk ->
                    when (mode) {
                        MediaBrowserMode.MUSIC -> songDao.deleteByPaths(chunk)
                        MediaBrowserMode.VIDEO -> videoDao.deleteByPaths(chunk)
                    }
                }
            }

            if (newFiles.isNotEmpty()) {
                Timber.tag(TAG).i("Processing ${newFiles.size} NEW files...")
                newFiles.chunked(INSERT_CHUNK_SIZE).forEach { chunk ->
                    when (mode) {
                        MediaBrowserMode.MUSIC -> songDao.insertAll(chunk.map { safeCreateSongEntity(it) })
                        MediaBrowserMode.VIDEO -> videoDao.insertAll(chunk.map { safeCreateVideoEntity(it) })
                    }
                }
                Timber.tag(TAG).d("SUCCESS: Inserted ${newFiles.size} files (bulk)")
            }
        }

        if (newFiles.isNotEmpty()) {
            val pathsToScan = newFiles.map { it.absolutePath }.toTypedArray()
            MediaScannerConnection.scanFile(context, pathsToScan, null, null)
        }
    }

    private fun File.safeCanonicalPath(): String =
        try { canonicalPath } catch (_: Exception) { absolutePath }

    private fun String.needsFilePathRepair(path: String): Boolean {
        if (isBlank()) return true

        val contentFile = when {
            startsWith("/") -> File(this)
            startsWith("file:") -> runCatching { File(URI(this)) }.getOrNull()
            else -> null
        } ?: return false

        val targetFile = File(path)
        if (!targetFile.exists()) return false

        val contentPath = contentFile.safeCanonicalPath()
        val targetPath = targetFile.safeCanonicalPath()
        return contentPath != targetPath || !contentFile.exists()
    }

    private fun safeCreateSongEntity(file: File): SongEntity {
        val metadata = try {
            extractAudioMetadata(file)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Metadata crash for ${file.name}, using fallback.")
            AudioMetadata(
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

    private fun safeCreateVideoEntity(file: File): VideoEntity {
        val metadata = try {
            extractVideoMetadata(file)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Video metadata crash for ${file.name}, using fallback.")
            VideoMetadata(
                title = file.nameWithoutExtension,
                duration = 0L,
                width = 0,
                height = 0
            )
        }

        val parentPath = file.parent ?: ""
        return VideoEntity(
            path = file.absolutePath,
            contentUri = file.absolutePath,
            title = metadata.title,
            duration = metadata.duration,
            thumbnailUri = null,
            parentPath = parentPath,
            filename = file.name,
            lastModified = file.lastModified(),
            size = file.length(),
            width = metadata.width,
            height = metadata.height
        )
    }

    private data class AudioMetadata(val title: String, val artist: String, val album: String, val duration: Long)
    private data class VideoMetadata(val title: String, val duration: Long, val width: Int, val height: Int)

    private fun extractAudioMetadata(file: File): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "<Unknown>"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "<Unknown>"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            AudioMetadata(title, artist, album, duration)
        } finally {
            try { retriever.release() } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to release MediaMetadataRetriever (non-fatal)")
            }
        }
    }

    private fun extractVideoMetadata(file: File): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0

            VideoMetadata(title, duration, width, height)
        } finally {
            try { retriever.release() } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to release MediaMetadataRetriever (non-fatal)")
            }
        }
    }
}
