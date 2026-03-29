package com.example.newaudio.domain.repository

import kotlinx.coroutines.flow.Flow

interface IMediaScannerRepository {
    val isScanning: Flow<Boolean>
    
    suspend fun scanDirectory(rootPath: String)
    suspend fun scanSingleFile(path: String)
}
