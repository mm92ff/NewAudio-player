package com.example.newaudio.domain.model

import java.io.File

/**
 * Represents an item in the file browser, which can be either a folder or a file.
 */
sealed interface FileItem {
    val name: String
    val path: String

    data class Folder(
        override val name: String,
        override val path: String,
        val songCount: Int? = null // New property
    ) : FileItem

    data class AudioFile(
        override val name: String,
        override val path: String,
        val songId: Long, // MediaStore ID for the audio file
        val song: Song // Embed the song details for playable files
    ) : FileItem

    data class OtherFile(
        override val name: String,
        override val path: String
    ) : FileItem
}
