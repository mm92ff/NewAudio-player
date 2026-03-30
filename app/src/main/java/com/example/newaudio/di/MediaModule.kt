package com.example.newaudio.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped

// CHANGE: Changed InstallIn to ServiceComponent to scope Player to Service lifecycle
@Module
@InstallIn(ServiceComponent::class)
object MediaModule {

    @OptIn(UnstableApi::class)
    @Provides
    @ServiceScoped // CHANGE: Singleton -> ServiceScoped
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Configure load control with reasonable buffer durations for network streams
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, // 50000ms min buffer
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS, // 50000ms max buffer
                15000, // 15s min buffer for playback to start
                50000  // 50s min buffer for playback to resume after rebuffering
            )
            .build()

        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true) // Required for audio focus
            .setHandleAudioBecomingNoisy(true) // Required for headphone events
            .setLoadControl(loadControl) // Apply network-aware buffering
            .build()
    }
}
