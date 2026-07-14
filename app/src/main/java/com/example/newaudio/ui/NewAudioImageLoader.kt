package com.example.newaudio.ui

import android.content.Context
import coil.ImageLoader
import coil.decode.VideoFrameDecoder

/**
 * Process-wide preview loader shared by the browser and benchmark-only cache control.
 * Local file/content sources are memory cached by Coil but are not copied into its
 * network-source disk cache, so warm benchmark state must be established in-process.
 */
object NewAudioImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: ImageLoader.Builder(context.applicationContext)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
            .also { instance = it }
    }
}
