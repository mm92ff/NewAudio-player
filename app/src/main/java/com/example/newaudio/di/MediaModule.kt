package com.example.newaudio.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
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

        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true) // Required for audio focus
            .setHandleAudioBecomingNoisy(true) // Required for headphone events
            .build()
    }
}
