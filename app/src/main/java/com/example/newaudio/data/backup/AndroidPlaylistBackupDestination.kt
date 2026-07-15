package com.example.newaudio.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPlaylistBackupDestination @Inject constructor(
    @ApplicationContext private val context: Context
) : PlaylistBackupDestination {

    override fun writeText(location: String, content: String) {
        openStream(location).use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
            stream.flush()
        }
    }

    private fun openStream(location: String): OutputStream {
        val file = location.toFileOrNull()
        if (file != null) return file.outputStream()

        val uri = Uri.parse(location)
        return context.contentResolver.openOutputStream(uri)
            ?: throw FileNotFoundException("Could not open output stream for $uri")
    }

    private fun String.toFileOrNull(): File? {
        val directFile = File(this)
        if (directFile.isAbsolute) return directFile

        val uri = Uri.parse(this)
        if (!uri.scheme.equals("file", ignoreCase = true)) return null
        return File(requireNotNull(uri.path) { "Invalid file URI: $uri" })
    }
}
