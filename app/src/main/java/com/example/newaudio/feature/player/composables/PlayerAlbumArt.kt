package com.example.newaudio.feature.player.composables

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.newaudio.R
import com.example.newaudio.ui.theme.Dimens
import com.example.newaudio.util.Constants
import com.example.newaudio.util.ArtworkDecodePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlayerAlbumArt(songPath: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var albumArt by remember(songPath) { mutableStateOf<ImageBitmap?>(null) }

    // Load album art asynchronously
    LaunchedEffect(songPath) {
        if (songPath == null) {
            albumArt = null
            return@LaunchedEffect
        }
        albumArt = withContext(Dispatchers.IO) {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, Uri.parse(songPath))
                val data = mmr.embeddedPicture
                if (data != null) {
                    decodeSampledBitmap(data, MAX_ALBUM_ART_SIZE)?.asImageBitmap()
                } else {
                    null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            } finally {
                try {
                    mmr.release()
                } catch (e: Exception) {
                    // Ignore release errors
                }
            }
        }
    }

    val art = albumArt
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = stringResource(R.string.player_album_art_content_description),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = stringResource(R.string.player_album_art_placeholder_icon),
                modifier = Modifier.size(Dimens.FullScreenPlayer_AlbumArtIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val MAX_ALBUM_ART_SIZE = 1_024

private fun decodeSampledBitmap(data: ByteArray, targetSize: Int): android.graphics.Bitmap? {
    if (data.size > Constants.Security.MAX_ARTWORK_BYTES) return null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, options)
    options.inSampleSize = ArtworkDecodePolicy.calculateInSampleSize(
        options.outWidth,
        options.outHeight,
        targetSize,
        targetSize
    )
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeByteArray(data, 0, data.size, options)
}
