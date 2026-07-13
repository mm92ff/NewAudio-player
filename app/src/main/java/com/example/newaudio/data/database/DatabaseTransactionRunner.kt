package com.example.newaudio.data.database

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

interface DatabaseTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

@Singleton
class RoomDatabaseTransactionRunner @Inject constructor(
    private val database: AppDatabase
) : DatabaseTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction(block)
}
