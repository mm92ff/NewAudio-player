package com.example.newaudio.domain.usecase.media

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import com.example.newaudio.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class GetSongMetadataUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(songPath: String): Map<String, String?> = withContext(ioDispatcher) {
        val metadata = mutableMapOf<String, String?>()
        val retriever = MediaMetadataRetriever()
        try {
            // .toUri() handles both file:// and content:// (SAF) correctly
            retriever.setDataSource(context, songPath.toUri())
            val metadataKeys = listOf(
                MediaMetadataRetriever.METADATA_KEY_ALBUM to "Album",
                MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST to "Album Artist",
                MediaMetadataRetriever.METADATA_KEY_ARTIST to "Artist",
                MediaMetadataRetriever.METADATA_KEY_AUTHOR to "Author",
                MediaMetadataRetriever.METADATA_KEY_BITRATE to "Bitrate",
                MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER to "Track Number",
                MediaMetadataRetriever.METADATA_KEY_COMPOSER to "Composer",
                MediaMetadataRetriever.METADATA_KEY_DATE to "Date",
                MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER to "Disc Number",
                MediaMetadataRetriever.METADATA_KEY_DURATION to "Duration (ms)",
                MediaMetadataRetriever.METADATA_KEY_GENRE to "Genre",
                MediaMetadataRetriever.METADATA_KEY_MIMETYPE to "MIME Type",
                MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS to "Number of Tracks",
                MediaMetadataRetriever.METADATA_KEY_TITLE to "Title",
                MediaMetadataRetriever.METADATA_KEY_WRITER to "Writer",
                MediaMetadataRetriever.METADATA_KEY_YEAR to "Year"
            )

            for ((key, name) in metadataKeys) {
                metadata[name] = retriever.extractMetadata(key)
            }
        } catch (e: Exception) {
            Timber.tag("GetSongMetadataUseCase").e(e, "Failed to retrieve metadata for $songPath")
        } finally {
            retriever.release()
        }
        metadata
    }
}
