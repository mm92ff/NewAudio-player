package com.example.newaudio.data.backup

import android.content.Context
import android.net.Uri
import com.example.newaudio.domain.repository.ImportFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPlaylistBackupSource @Inject constructor(
    @ApplicationContext private val context: Context
) : PlaylistBackupSource {

    override fun readText(location: String, maxBytes: Long): String {
        val stream = openStream(location, maxBytes)
        return stream.use { it.readUtf8Limited(maxBytes) }
    }

    private fun openStream(location: String, maxBytes: Long): InputStream {
        val file = location.toFileOrNull()
        if (file != null) {
            if (!file.exists()) {
                throw PlaylistBackupException(ImportFailure.NOT_FOUND)
            }
            if (file.length() > maxBytes) {
                throw PlaylistBackupException(ImportFailure.TOO_LARGE)
            }
            return try {
                file.inputStream()
            } catch (error: FileNotFoundException) {
                throw PlaylistBackupException(ImportFailure.NOT_FOUND, error)
            }
        }

        val uri = Uri.parse(location)
        return try {
            context.contentResolver.openInputStream(uri)
                ?: throw PlaylistBackupException(ImportFailure.NOT_FOUND)
        } catch (error: FileNotFoundException) {
            throw PlaylistBackupException(ImportFailure.NOT_FOUND, error)
        }
    }

    private fun InputStream.readUtf8Limited(maxBytes: Long): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw PlaylistBackupException(ImportFailure.TOO_LARGE)
            }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun String.toFileOrNull(): File? {
        val directFile = File(this)
        if (directFile.isAbsolute) return directFile

        val uri = Uri.parse(this)
        if (!uri.scheme.equals("file", ignoreCase = true)) return null
        val path = uri.path ?: throw PlaylistBackupException(ImportFailure.NOT_FOUND)
        return File(path)
    }
}
