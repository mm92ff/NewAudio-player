package com.example.newaudio.di

import com.example.newaudio.data.media.controller.AndroidMediaControllerFactory
import com.example.newaudio.data.media.controller.MediaControllerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Dependency bindings for the Media3 controller connection boundary. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MediaControllerModule {
    @Binds
    @Singleton
    abstract fun bindMediaControllerFactory(
        impl: AndroidMediaControllerFactory
    ): MediaControllerFactory
}
