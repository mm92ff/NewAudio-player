package com.example.newaudio.data.media.library

import androidx.media3.common.MediaItem
import com.example.newaudio.data.database.AppDatabase
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.SongMinimal
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.data.database.VideoMinimal
import com.example.newaudio.data.media.mapping.Media3ItemMapper
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.repository.IMediaScannerRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * IO-dispatched boundary for media scans and database lookups used by
 * playback. Media type resolution prefers explicit metadata, then a confirmed
 * database type, then the filename extension. Mapping falls back to Media3
 * metadata when an item is no longer present in the library.
 */
@Singleton
class MediaLibraryRepository @Inject constructor(
    private val mediaScannerRepository: IMediaScannerRepository,
    private val songDao: SongDao,
    private val videoDao: VideoDao,
    private val appDatabase: AppDatabase,
    private val itemMapper: Media3ItemMapper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun getSongCount(): Int = withContext(ioDispatcher) {
        songDao.countAllSongs()
    }

    suspend fun getVideoCount(): Int = withContext(ioDispatcher) {
        videoDao.countAllVideos()
    }

    suspend fun ensureSongAndGetParentPath(songPath: String): String? = withContext(ioDispatcher) {
        var song = songDao.getSongByPath(songPath)
        if (song == null) {
            mediaScannerRepository.scanSingleFile(songPath)
            song = songDao.getSongByPath(songPath)
        }
        song?.parentPath ?: File(songPath).parent
    }

    suspend fun getSongsInFolder(parentPath: String): List<Song> = withContext(ioDispatcher) {
        songDao.observeSongsInFolderMinimal(parentPath)
            .firstOrNull()
            .orEmpty()
            .map { it.toLibrarySong() }
    }

    suspend fun getVideosInFolder(parentPath: String): List<Video> = withContext(ioDispatcher) {
        videoDao.observeVideosInFolderMinimal(parentPath)
            .firstOrNull()
            .orEmpty()
            .map { it.toLibraryVideo() }
    }

    suspend fun resolveMediaType(mediaItem: MediaItem): Media3ItemMapper.MediaType? {
        itemMapper.declaredMediaType(mediaItem)?.let { return it }
        return withContext(ioDispatcher) {
            when {
                videoDao.getVideoByPath(mediaItem.mediaId) != null -> Media3ItemMapper.MediaType.VIDEO
                songDao.getSongByPath(mediaItem.mediaId) != null -> Media3ItemMapper.MediaType.AUDIO
                else -> itemMapper.extensionMediaType(mediaItem.mediaId)
            }
        }
    }

    suspend fun mapSongs(mediaItems: List<MediaItem>): List<Song> = withContext(ioDispatcher) {
        mediaItems.map { item ->
            itemMapper.toSong(item, songDao.getSongByPath(item.mediaId)?.toDomainModel())
        }
    }

    suspend fun mapVideos(mediaItems: List<MediaItem>): List<Video> = withContext(ioDispatcher) {
        mediaItems.map { item ->
            itemMapper.toVideo(item, videoDao.getVideoByPath(item.mediaId)?.toDomainModel())
        }
    }

    suspend fun clearDatabase() = withContext(ioDispatcher) {
        appDatabase.clearAllTables()
    }

    private fun SongMinimal.toLibrarySong(): Song = Song(
        path = path,
        contentUri = contentUri,
        title = title.takeIf { it.isNotBlank() }
            ?: File(path).nameWithoutExtension.ifBlank { "Unknown Title" },
        artist = artist,
        duration = duration,
        albumArtPath = albumArtPath
    )

    private fun VideoMinimal.toLibraryVideo(): Video = Video(
        path = path,
        contentUri = contentUri,
        title = title.takeIf { it.isNotBlank() }
            ?: File(path).nameWithoutExtension.ifBlank { "Unknown Video" },
        duration = duration,
        thumbnailUri = thumbnailUri,
        width = width,
        height = height
    )
}
