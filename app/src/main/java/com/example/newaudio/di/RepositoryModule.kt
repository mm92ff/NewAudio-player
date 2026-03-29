package com.example.newaudio.di

import com.example.newaudio.data.repository.DataStoreAudioSettingsRepository
import com.example.newaudio.data.repository.EqualizerRepositoryImpl
import com.example.newaudio.data.repository.ErrorRepositoryImpl
import com.example.newaudio.data.repository.FolderOrderRepositoryImpl
import com.example.newaudio.data.repository.MediaRepositoryImpl
import com.example.newaudio.data.repository.MediaScannerRepositoryImpl
import com.example.newaudio.data.repository.PlaylistRepositoryImpl
import com.example.newaudio.data.repository.SettingsRepositoryImpl
import com.example.newaudio.domain.repository.AudioSettingsRepository
import com.example.newaudio.domain.repository.IEqualizerRepository
import com.example.newaudio.domain.repository.IErrorRepository
import com.example.newaudio.domain.repository.IFolderOrderRepository
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.repository.IMediaScannerRepository
import com.example.newaudio.domain.repository.IPlaylistRepository
import com.example.newaudio.domain.repository.ISettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): ISettingsRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): IMediaRepository

    @Binds
    @Singleton
    abstract fun bindFolderOrderRepository(impl: FolderOrderRepositoryImpl): IFolderOrderRepository

    @Binds
    @Singleton
    abstract fun bindEqualizerRepository(impl: EqualizerRepositoryImpl): IEqualizerRepository

    @Binds
    @Singleton
    abstract fun bindErrorRepository(impl: ErrorRepositoryImpl): IErrorRepository

    @Binds
    @Singleton
    abstract fun bindAudioSettingsRepository(
        impl: DataStoreAudioSettingsRepository
    ): AudioSettingsRepository
    
    @Binds
    @Singleton
    abstract fun bindMediaScannerRepository(impl: MediaScannerRepositoryImpl): IMediaScannerRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): IPlaylistRepository
}
