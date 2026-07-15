package com.example.newaudio.data.backup

interface PlaylistBackupDestination {
    fun writeText(location: String, content: String)
}
