package com.example.newaudio.data.repository

import android.content.Context
import android.database.ContentObserver
import android.provider.MediaStore
import com.example.newaudio.domain.repository.IMediaStoreObserverRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreObserverRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : IMediaStoreObserverRepository {

    override fun observeAudioChanges(): Flow<Unit> {
        return callbackFlow {
            val observer = object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            // When the flow is closed, unregister the observer
            awaitClose {
                context.contentResolver.unregisterContentObserver(observer)
            }
        }.debounce(1000L) // Wait for 1 second of inactivity before emitting
    }
}