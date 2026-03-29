package com.example.newaudio.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        // SupervisorJob: A failure in a child does not cancel the whole scope
        // Dispatchers.Default: Good for background work (e.g. database/audio)
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}