package com.example.newaudio.domain.repository

import kotlinx.coroutines.flow.Flow

interface IFolderOrderRepository {

    fun observeFolderOrder(path: String): Flow<List<String>?>

    suspend fun saveFolderOrder(path: String, order: List<String>)
}