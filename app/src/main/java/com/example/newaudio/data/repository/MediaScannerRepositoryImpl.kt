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

                        val title = cursor.getString(titleColumn) ?: file.nameWithoutExtension
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
                    title = meta["Title"] ?: file.nameWithoutExtension,
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

    private fun getAudioCollectionUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
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
