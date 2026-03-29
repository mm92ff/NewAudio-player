package com.example.newaudio.di

import com.example.newaudio.data.audio.AndroidAudioEffectController
import com.example.newaudio.domain.audio.AudioEffectController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindAudioEffectController(
        impl: AndroidAudioEffectController
    ): AudioEffectController
}