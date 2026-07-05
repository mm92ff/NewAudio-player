package com.example.newaudio.fake

import com.example.newaudio.domain.repository.IMediaScannerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeMediaScannerRepository : IMediaScannerRepository {

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: Flow<Boolean> = _isScanning.asStateFlow()

    var scanDirectoryCalled: String? = null
    var scanSingleFileCalled: String? = null
    var scanVideoDirectoryCalled: String? = null
    var scanSingleVideoFileCalled: String? = null
    val scanSingleFileCalls = mutableListOf<String>()
    val scanSingleVideoFileCalls = mutableListOf<String>()

    override suspend fun scanDirectory(rootPath: String) {
        scanDirectoryCalled = rootPath
    }

    override suspend fun scanSingleFile(path: String) {
        scanSingleFileCalled = path
        scanSingleFileCalls.add(path)
    }

    override suspend fun scanVideoDirectory(rootPath: String) {
        scanVideoDirectoryCalled = rootPath
    }

    override suspend fun scanSingleVideoFile(path: String) {
        scanSingleVideoFileCalled = path
        scanSingleVideoFileCalls.add(path)
    }
}
