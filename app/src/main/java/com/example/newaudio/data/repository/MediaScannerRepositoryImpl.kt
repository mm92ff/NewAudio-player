package com.example.newaudio.data.repository

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.example.newaudio.data.database.AppDatabase
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.SongEntity
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.data.database.VideoEntity
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.repository.IMediaScannerRepository
import com.example.newaudio.util.FileHashUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaScannerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val songDao: SongDao,
    private val videoDao: VideoDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IMediaScannerRepository {

    companion object {
        private const val TAG = "MediaScanner"
        private const val BATCH_INSERT_SIZE = 250
    }

    private val scanIdCounter = AtomicLong(0L)
    private val scanningAtomic = AtomicBoolean(false)

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: Flow<Boolean> = _isScanning.asStateFlow()

    override suspend fun scanDirectory(rootPath: String) {
        withContext(ioDispatcher) {
            val scanId = scanIdCounter.incrementAndGet()

            if (!scanningAtomic.compareAndSet(false, true)) {
                Timber.tag(TAG).w("SKIP scanId=$scanId (already scanning). requestedRoot=$rootPath")
                return@withContext
            }
            _isScanning.value = true

            try {
                val resolvedRoot = resolveToAbsolutePathIfPossible(rootPath)?.trimEnd('/')
                Timber.tag(TAG).d("START scanId=$scanId root=$resolvedRoot")

                val collection = getAudioCollectionUri()
                val projection = buildProjection()
                val (selection, selectionArgs) = buildSmartSelection(resolvedRoot)
                val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

                val seenPaths = HashSet<String>(4096)
                val scannedSongs = ArrayList<SongEntity>(2048)
                var cursorRows = 0

                context.contentResolver.query(
                    collection, projection, selection, selectionArgs, sortOrder
                )?.use { cursor ->
                    cursorRows = cursor.count

                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                    val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

                    val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    } else -1

                    while (cursor.moveToNext()) {
                        val displayName = cursor.getString(displayNameColumn) ?: "unknown"

                        var absolutePath = if (dataColumn >= 0) cursor.getString(dataColumn) else null

                        if (absolutePath.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && relativePathColumn >= 0) {
                            val relativePath = cursor.getString(relativePathColumn)
                            if (relativePath != null) {
                                absolutePath = buildAbsolutePath(relativePath, displayName)
                            }
                        }

                        if (absolutePath.isNullOrBlank()) continue
                        if (!seenPaths.add(absolutePath)) continue

                        val id = cursor.getLong(idColumn)
                        val file = File(absolutePath)

                        val title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                        val artist = cursor.getString(artistColumn) ?: "<unknown>"
                        val album = cursor.getString(albumColumn) ?: "<unknown>"
                        val duration = cursor.getLong(durationColumn)
                        val size = cursor.getLong(sizeColumn)
                        val dateModified = cursor.getLong(modifiedColumn)
                        val albumId = cursor.getLong(albumIdColumn)

                        val contentUri = ContentUris.withAppendedId(collection, id)
                        val albumArtUri = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()

                        // Calculate hash (fast-hash)
                        val fileHash = FileHashUtils.calculateFastHash(absolutePath)

                        scannedSongs.add(
                            SongEntity(
                                path = absolutePath,
                                contentUri = contentUri.toString(),
                                title = title,
                                artist = artist,
                                album = album,
                                duration = duration,
                                albumArtPath = albumArtUri,
                                parentPath = file.parent ?: "",
                                filename = displayName,
                                lastModified = dateModified,
                                size = size,
                                fileHash = fileHash
                            )
                        )
                    }
                }

                if (scannedSongs.isNotEmpty()) {
                    appDatabase.withTransaction {
                        scannedSongs.chunked(BATCH_INSERT_SIZE).forEach { chunk ->
                            songDao.insertAll(chunk)
                        }
                    }
                }
                Timber.tag(TAG).d("END scanId=$scanId cursorRows=$cursorRows inserted=${scannedSongs.size}")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "CRITICAL scan failure scanId=$scanId")
            } finally {
                scanningAtomic.set(false)
                _isScanning.value = false
            }
        }
    }

    override suspend fun scanSingleFile(path: String) {
        withContext(ioDispatcher) {
            try {
                Timber.tag(TAG).d("scanSingleFile path=$path")
                val file = File(path)
                if (!file.exists()) return@withContext

                val realContentUri = findMediaStoreUriSmart(file) ?: Uri.fromFile(file).toString()
                val meta = extractMetadataInternal(path)

                val entity = SongEntity(
                    path = file.absolutePath,
                    contentUri = realContentUri,
                    title = meta["Title"]?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                    artist = meta["Artist"] ?: "",
                    album = meta["Album"] ?: "",
                    duration = meta["Duration"]?.toLongOrNull() ?: 0L,
                    albumArtPath = null,
                    parentPath = file.parent ?: "",
                    filename = file.name,
                    lastModified = file.lastModified(),
                    size = file.length(),
                    fileHash = FileHashUtils.calculateFastHash(file.absolutePath)
                )

                appDatabase.withTransaction {
                    songDao.insert(entity)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error scanning single file")
            }
        }
    }

    override suspend fun scanVideoDirectory(rootPath: String) {
        withContext(ioDispatcher) {
            val scanId = scanIdCounter.incrementAndGet()

            if (!scanningAtomic.compareAndSet(false, true)) {
                Timber.tag(TAG).w("SKIP video scanId=$scanId (already scanning). requestedRoot=$rootPath")
                return@withContext
            }
            _isScanning.value = true

            try {
                val resolvedRoot = resolveToAbsolutePathIfPossible(rootPath)?.trimEnd('/')
                Timber.tag(TAG).d("START video scanId=$scanId root=$resolvedRoot")

                val collection = getVideoCollectionUri()
                val projection = buildVideoProjection()
                val (selection, selectionArgs) = buildVideoSelection(resolvedRoot)
                val sortOrder = "${MediaStore.Video.Media.TITLE} ASC"

                val seenPaths = HashSet<String>(4096)
                val scannedVideos = ArrayList<VideoEntity>(1024)
                var cursorRows = 0

                context.contentResolver.query(
                    collection, projection, selection, selectionArgs, sortOrder
                )?.use { cursor ->
                    cursorRows = cursor.count

                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                    val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                    val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)

                    val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                    } else -1

                    while (cursor.moveToNext()) {
                        val displayName = cursor.getString(displayNameColumn) ?: "unknown"
                        var absolutePath = if (dataColumn >= 0) cursor.getString(dataColumn) else null

                        if (absolutePath.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && relativePathColumn >= 0) {
                            val relativePath = cursor.getString(relativePathColumn)
                            if (relativePath != null) {
                                absolutePath = buildAbsolutePath(relativePath, displayName)
                            }
                        }

                        if (absolutePath.isNullOrBlank()) continue
                        if (!seenPaths.add(absolutePath)) continue

                        val id = cursor.getLong(idColumn)
                        val file = File(absolutePath)
                        val contentUri = ContentUris.withAppendedId(collection, id)

                        scannedVideos.add(
                            VideoEntity(
                                path = absolutePath,
                                contentUri = contentUri.toString(),
                                title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                                duration = cursor.getLong(durationColumn),
                                thumbnailUri = null,
                                parentPath = file.parent ?: "",
                                filename = displayName,
                                lastModified = cursor.getLong(modifiedColumn),
                                size = cursor.getLong(sizeColumn),
                                width = if (widthColumn >= 0) cursor.getInt(widthColumn) else 0,
                                height = if (heightColumn >= 0) cursor.getInt(heightColumn) else 0,
                                fileHash = FileHashUtils.calculateFastHash(absolutePath)
                            )
                        )
                    }
                }

                if (scannedVideos.isNotEmpty()) {
                    appDatabase.withTransaction {
                        scannedVideos.chunked(BATCH_INSERT_SIZE).forEach { chunk ->
                            videoDao.insertAll(chunk)
                        }
                    }
                }
                Timber.tag(TAG).d("END video scanId=$scanId cursorRows=$cursorRows inserted=${scannedVideos.size}")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "CRITICAL video scan failure scanId=$scanId")
            } finally {
                scanningAtomic.set(false)
                _isScanning.value = false
            }
        }
    }

    override suspend fun scanSingleVideoFile(path: String) {
        withContext(ioDispatcher) {
            try {
                Timber.tag(TAG).d("scanSingleVideoFile path=$path")
                val file = File(path)
                if (!file.exists()) return@withContext

                val realContentUri = findVideoMediaStoreUriSmart(file) ?: Uri.fromFile(file).toString()
                val meta = extractVideoMetadataInternal(path)

                val entity = VideoEntity(
                    path = file.absolutePath,
                    contentUri = realContentUri,
                    title = meta["Title"]?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                    duration = meta["Duration"]?.toLongOrNull() ?: 0L,
                    thumbnailUri = null,
                    parentPath = file.parent ?: "",
                    filename = file.name,
                    lastModified = file.lastModified(),
                    size = file.length(),
                    width = meta["Width"]?.toIntOrNull() ?: 0,
                    height = meta["Height"]?.toIntOrNull() ?: 0,
                    fileHash = FileHashUtils.calculateFastHash(file.absolutePath)
                )

                appDatabase.withTransaction {
                    videoDao.insert(entity)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error scanning single video file")
            }
        }
    }

    private fun findMediaStoreUriSmart(file: File): String? {
        val collection = getAudioCollectionUri()
        val projection = arrayOf(MediaStore.Audio.Media._ID)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(file.name)
                val expectedRelativePath = convertToRelativePath(file.parent ?: "")

                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.RELATIVE_PATH),
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val relPathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)

                    while (cursor.moveToNext()) {
                        val dbRelativePath = cursor.getString(relPathCol)
                        if (expectedRelativePath == null || dbRelativePath.equals(expectedRelativePath, ignoreCase = true) || dbRelativePath.startsWith(expectedRelativePath)) {
                            val id = cursor.getLong(idCol)
                            return ContentUris.withAppendedId(collection, id).toString()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w("Smart URI lookup failed: ${e.message}")
            }
        }

        val selectionLegacy = "${MediaStore.Audio.Media.DATA} = ?"
        val argsLegacy = arrayOf(file.absolutePath)

        return try {
            context.contentResolver.query(collection, projection, selectionLegacy, argsLegacy, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    ContentUris.withAppendedId(collection, id).toString()
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun findVideoMediaStoreUriSmart(file: File): String? {
        val collection = getVideoCollectionUri()
        val projection = arrayOf(MediaStore.Video.Media._ID)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(file.name)
                val expectedRelativePath = convertToRelativePath(file.parent ?: "")

                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.RELATIVE_PATH),
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val relPathCol = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)

                    while (cursor.moveToNext()) {
                        val dbRelativePath = cursor.getString(relPathCol)
                        if (expectedRelativePath == null || dbRelativePath.equals(expectedRelativePath, ignoreCase = true) || dbRelativePath.startsWith(expectedRelativePath)) {
                            val id = cursor.getLong(idCol)
                            return ContentUris.withAppendedId(collection, id).toString()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w("Smart video URI lookup failed: ${e.message}")
            }
        }

        val selectionLegacy = "${MediaStore.Video.Media.DATA} = ?"
        val argsLegacy = arrayOf(file.absolutePath)

        return try {
            context.contentResolver.query(collection, projection, selectionLegacy, argsLegacy, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    ContentUris.withAppendedId(collection, id).toString()
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildProjection(): Array<String> {
        val base = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            base.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }
        return base.toTypedArray()
    }

    private fun buildVideoProjection(): Array<String> {
        val base = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            base.add(MediaStore.Video.Media.RELATIVE_PATH)
        }
        return base.toTypedArray()
    }

    private fun buildAbsolutePath(relativePath: String, displayName: String): String {
        val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        return "$storageRoot/$relativePath$displayName"
    }

    private fun buildSmartSelection(rootAbsPathOrNull: String?): Pair<String, Array<String>?> {
        val baseSelection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        if (rootAbsPathOrNull.isNullOrBlank()) return baseSelection to null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = convertToRelativePath(rootAbsPathOrNull)
            if (relativePath != null) {
                return "$baseSelection AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" to arrayOf("$relativePath%")
            }
        }
        return "$baseSelection AND ${MediaStore.Audio.Media.DATA} LIKE ?" to arrayOf("$rootAbsPathOrNull/%")
    }

    private fun buildVideoSelection(rootAbsPathOrNull: String?): Pair<String?, Array<String>?> {
        if (rootAbsPathOrNull.isNullOrBlank()) return null to null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = convertToRelativePath(rootAbsPathOrNull)
            if (relativePath != null) {
                return "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?" to arrayOf("$relativePath%")
            }
        }
        return "${MediaStore.Video.Media.DATA} LIKE ?" to arrayOf("$rootAbsPathOrNull/%")
    }

    private fun convertToRelativePath(absolutePath: String): String? {
        val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        if (absolutePath.startsWith(storageRoot)) {
            return absolutePath.removePrefix(storageRoot).trimStart('/').ifEmpty { null }?.let {
                if (it.endsWith("/")) it else "$it/"
            }
        }
        return null
    }

    private fun extractMetadataInternal(path: String): Map<String, String> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            mapOf(
                "Title" to (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""),
                "Artist" to (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""),
                "Album" to (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""),
                "Duration" to (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0")
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w("Metadata extraction failed for $path")
            emptyMap()
        } finally {
            try { retriever.release() } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to release MediaMetadataRetriever (non-fatal)")
            }
        }
    }

    private fun extractVideoMetadataInternal(path: String): Map<String, String> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            mapOf(
                "Title" to (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""),
                "Duration" to (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0"),
                "Width" to (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "0"),
                "Height" to (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "0")
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w("Video metadata extraction failed for $path")
            emptyMap()
        } finally {
            try { retriever.release() } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to release MediaMetadataRetriever (non-fatal)")
            }
        }
    }

    private fun getAudioCollectionUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun getVideoCollectionUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun resolveToAbsolutePathIfPossible(input: String): String? {
        if (input.isBlank()) return null
        if (input.startsWith("/")) return input
        try {
            val uri = input.toUri()
            if (uri.scheme != "content") return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !DocumentsContract.isTreeUri(uri)) return null

            val docId = DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(":", limit = 2)
            if (parts.size != 2) return null
            val volume = parts[0]
            val rel = parts[1].trimStart('/')

            return when (volume.lowercase()) {
                "primary" -> "/storage/emulated/0/$rel"
                else -> "/storage/$volume/$rel"
            }
        } catch (_: Exception) { return null }
    }
}
