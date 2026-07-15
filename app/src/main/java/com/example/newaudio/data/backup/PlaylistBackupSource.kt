package com.example.newaudio.data.backup

interface PlaylistBackupSource {
    fun readText(location: String, maxBytes: Long): String
}
