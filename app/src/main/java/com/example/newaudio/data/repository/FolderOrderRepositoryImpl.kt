package com.example.newaudio.data.repository

import android.content.Context
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.repository.IFolderOrderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderOrderRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IFolderOrderRepository {

    private val fileName = "folder_orders.json"
    private val orderFile by lazy { File(context.filesDir, fileName) }
    private val mutex = Mutex()

    // In-memory cache
    private val allOrdersFlow = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    @Volatile
    private var loaded: Boolean = false

    @Serializable
    private data class OrderStorage(
        val orders: Map<String, List<String>>
    )

    private suspend fun ensureLoaded() {
        // 1. Fast check without lock
        if (loaded) return

        mutex.withLock {
            // 2. Double-check with lock (thread safety)
            if (loaded) return

            val loadedOrders = withContext(ioDispatcher) {
                if (!orderFile.exists()) return@withContext emptyMap()

                try {
                    val json = orderFile.readText()
                    Json.decodeFromString<OrderStorage>(json).orders
                } catch (e: Exception) {
                    // IMPORTANT: Log errors instead of silently swallowing them
                    Timber.e(e, "Failed to load folder orders from $fileName. Returning empty map.")
                    // Optional: create a backup of the corrupted file if analysis is desired
                    emptyMap()
                }
            }

            allOrdersFlow.value = loadedOrders
            loaded = true
        }
    }

    private suspend fun saveToDisk() {
        val storage = OrderStorage(allOrdersFlow.value)

        withContext(ioDispatcher) {
            try {
                val json = Json.encodeToString(storage)

                // ATOMIC WRITE PATTERN:
                // 1. Write to temporary file
                val tmpFile = File(context.filesDir, "$fileName.tmp")
                tmpFile.writeText(json)

                // 2. Attempt to atomically replace the real file
                if (tmpFile.renameTo(orderFile)) {
                    // Success
                } else {
                    // Fallback if renameTo fails (rare, but possible)
                    Timber.w("Atomic rename failed. Trying manual copy.")
                    tmpFile.copyTo(orderFile, overwrite = true)
                    tmpFile.delete()
                }
            } catch (e: IOException) {
                Timber.e(e, "Failed to save folder orders to disk.")
            }
        }
    }

    override fun observeFolderOrder(path: String): Flow<List<String>?> {
        return allOrdersFlow
            .onStart { ensureLoaded() } // Guarantees loading before the first emit
            .map { orders -> orders[path] }
    }

    override suspend fun saveFolderOrder(path: String, order: List<String>) {
        // Ensure we have the current state before overwriting
        ensureLoaded()

        mutex.withLock {
            val currentOrders = allOrdersFlow.value.toMutableMap()

            // Optimization: only save if something actually changed
            if (currentOrders[path] == order) {
                return@withLock
            }

            currentOrders[path] = order
            allOrdersFlow.value = currentOrders
            saveToDisk()
        }
    }
}