package com.example.newaudio.domain.usecase.file

import android.os.Environment
import javax.inject.Inject

class GetRootPathUseCase @Inject constructor() {
    // Return standard filesystem path for DB browsing
    suspend operator fun invoke(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
    }
}
