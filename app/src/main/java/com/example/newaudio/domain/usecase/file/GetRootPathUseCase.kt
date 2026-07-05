package com.example.newaudio.domain.usecase.file

import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.example.newaudio.domain.model.MediaBrowserMode
import com.example.newaudio.domain.repository.ISettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetRootPathUseCase @Inject constructor(
    private val settingsRepository: ISettingsRepository
) {
    suspend operator fun invoke(mode: MediaBrowserMode = MediaBrowserMode.MUSIC): String {
        val configuredRoot = when (mode) {
            MediaBrowserMode.MUSIC -> settingsRepository.getMusicFolderPath().first()
            MediaBrowserMode.VIDEO -> settingsRepository.getVideoFolderPath().first()
        }

        resolveToAbsolutePathIfPossible(configuredRoot)?.let { return it }

        val defaultDirectory = when (mode) {
            MediaBrowserMode.MUSIC -> Environment.DIRECTORY_MUSIC
            MediaBrowserMode.VIDEO -> Environment.DIRECTORY_MOVIES
        }
        return Environment.getExternalStoragePublicDirectory(defaultDirectory).absolutePath
    }

    private fun resolveToAbsolutePathIfPossible(input: String): String? {
        if (input.isBlank()) return null
        if (input.startsWith("/")) return input.trimEnd('/')
        return try {
            val uri = input.toUri()
            if (uri.scheme != "content") return null
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(":", limit = 2)
            if (parts.size != 2) return null
            val rel = parts[1].trimStart('/')
            when (parts[0].lowercase()) {
                "primary" -> "/storage/emulated/0/$rel".trimEnd('/')
                else -> "/storage/${parts[0]}/$rel".trimEnd('/')
            }
        } catch (_: Exception) {
            null
        }
    }
}
