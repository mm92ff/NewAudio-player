package com.example.newaudio.data.media.mapping

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import java.io.File
import java.util.Locale
import javax.inject.Inject

/**
 * Maps domain media to Media3 items and provides the canonical media-type
 * classification used by playback components.
 *
 * An explicit type stored in metadata always wins over a file extension. The
 * extension is only a fallback for older or externally supplied queue items.
 */
class Media3ItemMapper @Inject constructor() {
    enum class MediaType { AUDIO, VIDEO }

    companion object {
        const val MEDIA_TYPE_KEY = "com.example.newaudio.MEDIA_TYPE"
        private const val MEDIA_TYPE_AUDIO = "audio"
        private const val MEDIA_TYPE_VIDEO = "video"

        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "oga", "opus", "wma"
        )
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "3gp", "3gpp"
        )
    }

    fun toMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title.takeIf { it.isNotBlank() } ?: fallbackTitle(song.path, "Unknown Title"))
            .setArtist(song.artist)
            .setExtras(Bundle().apply { putString(MEDIA_TYPE_KEY, MEDIA_TYPE_AUDIO) })
            .build()

        return MediaItem.Builder()
            .setUri(playableUri(song.contentUri, song.path).toUri())
            .setMediaId(song.path)
            .setMediaMetadata(metadata)
            .build()
    }

    fun toMediaItem(video: Video): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(video.title.takeIf { it.isNotBlank() } ?: fallbackTitle(video.path, "Unknown Video"))
            .setExtras(Bundle().apply { putString(MEDIA_TYPE_KEY, MEDIA_TYPE_VIDEO) })
            .build()

        return MediaItem.Builder()
            .setUri(playableUri(video.contentUri, video.path).toUri())
            .setMediaId(video.path)
            .setMediaMetadata(metadata)
            .build()
    }

    fun toSong(mediaItem: MediaItem, librarySong: Song? = null): Song {
        return librarySong ?: Song(
            path = mediaItem.mediaId,
            contentUri = mediaItem.localConfiguration?.uri?.toString() ?: mediaItem.mediaId,
            title = mediaItem.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
                ?: fallbackTitle(mediaItem.mediaId, "Unknown Title"),
            artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown Artist",
            duration = 0L,
            albumArtPath = null
        )
    }

    fun toVideo(mediaItem: MediaItem, libraryVideo: Video? = null): Video {
        return libraryVideo ?: Video(
            path = mediaItem.mediaId,
            contentUri = mediaItem.localConfiguration?.uri?.toString() ?: mediaItem.mediaId,
            title = mediaItem.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
                ?: fallbackTitle(mediaItem.mediaId, "Unknown Video"),
            duration = 0L,
            thumbnailUri = null
        )
    }

    fun declaredMediaType(mediaItem: MediaItem): MediaType? {
        return when (mediaItem.mediaMetadata.extras?.getString(MEDIA_TYPE_KEY)) {
            MEDIA_TYPE_AUDIO -> MediaType.AUDIO
            MEDIA_TYPE_VIDEO -> MediaType.VIDEO
            else -> null
        }
    }

    fun extensionMediaType(path: String): MediaType? {
        val extension = path.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
        return when (extension) {
            in VIDEO_EXTENSIONS -> MediaType.VIDEO
            in AUDIO_EXTENSIONS -> MediaType.AUDIO
            else -> null
        }
    }

    fun mediaType(mediaItem: MediaItem): MediaType? {
        return declaredMediaType(mediaItem) ?: extensionMediaType(mediaItem.mediaId)
    }

    fun isVideo(mediaItem: MediaItem): Boolean {
        return mediaType(mediaItem) == MediaType.VIDEO
    }

    private fun fallbackTitle(path: String, unknownTitle: String): String {
        return File(path).nameWithoutExtension.ifBlank { unknownTitle }
    }

    private fun playableUri(contentUri: String, mediaPath: String): String {
        val candidate = contentUri.trim()
        val mediaFile = File(mediaPath)
        if (candidate.isBlank()) return mediaPath
        if (!candidate.startsWith("/")) return candidate

        val contentFile = File(candidate)
        return if (mediaFile.exists() &&
            (!contentFile.exists() || contentFile.absolutePath != mediaFile.absolutePath)
        ) {
            mediaPath
        } else {
            candidate
        }
    }
}
