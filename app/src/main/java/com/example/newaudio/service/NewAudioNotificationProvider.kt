package com.example.newaudio.service

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.example.newaudio.util.Constants
import com.example.newaudio.util.ArtworkDecodePolicy
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(UnstableApi::class)
class NewAudioNotificationProvider(private val context: Context) : MediaNotification.Provider {

    private val defaultProvider = DefaultMediaNotificationProvider(context)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastBitmap: Bitmap? = null
    private var mediaSession: MediaSession? = null
    private var artworkLoadJob: Job? = null
    
    // Max size for notification large icon to optimize performance
    private val MAX_ICON_SIZE = 512

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                val uri = player.mediaMetadata.artworkUri
                val data = player.mediaMetadata.artworkData
                
                if (uri != null) {
                    loadArtworkFromUri(uri)
                } else if (data != null) {
                    loadArtworkFromByteArray(data)
                } else {
                    replaceLastBitmap(null)
                }
            }
        }
    }

    private fun loadArtworkFromUri(uri: Uri) {
        artworkLoadJob?.cancel()
        artworkLoadJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val data = context.contentResolver.openInputStream(uri)?.use(::readArtworkBytes)
                val bitmap = data?.let(::decodeArtwork)
                withContext(Dispatchers.Main) { replaceLastBitmap(bitmap) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { replaceLastBitmap(null) }
            }
        }
    }

    private fun loadArtworkFromByteArray(data: ByteArray) {
        if (data.size > Constants.Security.MAX_ARTWORK_BYTES) {
            replaceLastBitmap(null)
            return
        }
        artworkLoadJob?.cancel()
        artworkLoadJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val bitmap = decodeArtwork(data)
                withContext(Dispatchers.Main) { replaceLastBitmap(bitmap) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { replaceLastBitmap(null) }
            }
        }
    }

    private fun readArtworkBytes(input: java.io.InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return output.toByteArray()
            total += read
            if (total > Constants.Security.MAX_ARTWORK_BYTES) return null
            output.write(buffer, 0, read)
        }
    }

    private fun decodeArtwork(data: ByteArray): Bitmap? {
        if (data.size > Constants.Security.MAX_ARTWORK_BYTES) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)
        options.inSampleSize = ArtworkDecodePolicy.calculateInSampleSize(
            options.outWidth,
            options.outHeight,
            MAX_ICON_SIZE,
            MAX_ICON_SIZE
        )
        options.inJustDecodeBounds = false
        val originalBitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: return null
        val scaledBitmap = scaleBitmapIfNeeded(originalBitmap)
        if (scaledBitmap !== originalBitmap) originalBitmap.recycle()
        val processedBitmap = applySmartGradientOverlay(scaledBitmap)
        if (processedBitmap !== scaledBitmap) scaledBitmap.recycle()
        return processedBitmap
    }

    
    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= MAX_ICON_SIZE && height <= MAX_ICON_SIZE) {
            return bitmap
        }
        
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        
        if (width > height) {
            newWidth = MAX_ICON_SIZE
            newHeight = (MAX_ICON_SIZE / ratio).toInt().coerceAtLeast(1)
        } else {
            newHeight = MAX_ICON_SIZE
            newWidth = (MAX_ICON_SIZE * ratio).toInt().coerceAtLeast(1)
        }
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun applySmartGradientOverlay(source: Bitmap): Bitmap {
        try {
            val width = source.width.toFloat()
            val height = source.height.toFloat()
            val mutableBitmap = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
            val canvas = Canvas(mutableBitmap)
            val colors = intArrayOf(
                Color.parseColor("#99000000"),
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.parseColor("#99000000")
            )
            val positions = floatArrayOf(0f, 0.3f, 0.7f, 1f)
            val shader = LinearGradient(0f, 0f, 0f, height, colors, positions, Shader.TileMode.CLAMP)
            val paint = Paint().apply {
                this.shader = shader
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width, height, paint)
            return mutableBitmap
        } catch (e: Exception) {
            return source
        }
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        
        if (this.mediaSession != mediaSession) {
            this.mediaSession?.player?.removeListener(playerListener)
            this.mediaSession = mediaSession
            mediaSession.player.addListener(playerListener)
        }

        val mediaNotification = defaultProvider.createNotification(
            mediaSession, 
            customLayout, 
            actionFactory, 
            onNotificationChangedCallback
        )
        
        val notification = mediaNotification.notification

        // Standard way to set the large icon if available
        if (lastBitmap != null) {
            notification.extras.putParcelable(NotificationCompat.EXTRA_LARGE_ICON, lastBitmap)
        }
        
        return mediaNotification
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: android.os.Bundle
    ): Boolean {
        return defaultProvider.handleCustomCommand(session, action, extras)
    }

    fun release() {
        artworkLoadJob?.cancel()
        serviceScope.cancel()
        mediaSession?.player?.removeListener(playerListener)
        replaceLastBitmap(null)
    }

    private fun replaceLastBitmap(bitmap: Bitmap?) {
        val previous = lastBitmap
        lastBitmap = bitmap
        if (previous != null && previous !== bitmap && !previous.isRecycled) {
            previous.recycle()
        }
    }
}
