package com.example.newaudio.di

import com.example.newaudio.data.repository.MediaStoreObserverRepositoryImpl
import com.example.newaudio.domain.repository.IMediaStoreObserverRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ObserverModule {

    @Binds
    @Singleton
    abstract fun bindMediaStoreObserverRepository(
        mediaStoreObserverRepositoryImpl: MediaStoreObserverRepositoryImpl
    ): IMediaStoreObserverRepository
}