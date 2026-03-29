package com.example.newaudio.domain.usecase.media

import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.domain.repository.IMediaScannerRepository
import com.example.newaudio.domain.usecase.file.GetRootPathUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * App-start policy: if the song database is empty, trigger a full scan.
 */
class ScanLibraryIfEmptyUseCase @Inject constructor(
    private val mediaRepository: IMediaRepository,
    private val mediaScannerRepository: IMediaScannerRepository,
    private val getRootPathUseCase: GetRootPathUseCase
) {
    suspend operator fun invoke() {
        val existing = mediaRepository.getLibrarySongCount()
        if (existing > 0) {
            Timber.tag(TAG).d("Skipping initial scan; DB already has %d songs.", existing)
            return
        }

        val rootPath = getRootPathUseCase()
        Timber.tag(TAG).d("DB is empty -> starting initial scan from root: %s", rootPath)
        mediaScannerRepository.scanDirectory(rootPath)
    }

    private companion object {
        private const val TAG = "ScanLibrary"
    }
}
