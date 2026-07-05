package com.example.newaudio.domain.model

sealed interface PlaybackSessionSnapshot {
    val currentIndex: Int
    val currentPath: String
    val positionMs: Long
    val folderPath: String?
    val wasPlaying: Boolean

    data class MusicSession(
        val songs: List<Song>,
        override val currentIndex: Int,
        override val currentPath: String,
        override val positionMs: Long,
        override val folderPath: String?,
        override val wasPlaying: Boolean
    ) : PlaybackSessionSnapshot

    data class VideoSession(
        val videos: List<Video>,
        override val currentIndex: Int,
        override val currentPath: String,
        override val positionMs: Long,
        override val folderPath: String?,
        override val wasPlaying: Boolean
    ) : PlaybackSessionSnapshot
}

data class SessionStartPosition(
    val index: Int,
    val positionMs: Long,
    val usedSnapshot: Boolean
)

fun PlaybackSessionSnapshot.MusicSession?.resolveMusicSessionStart(
    songs: List<Song>,
    requestedIndex: Int,
    folderPath: String?
): SessionStartPosition {
    val safeIndex = requestedIndex.coerceInPlaylist(songs.size)
    val requestedSong = songs.getOrNull(safeIndex)
        ?: return SessionStartPosition(index = 0, positionMs = 0L, usedSnapshot = false)

    if (this == null || currentPath != requestedSong.path || !matchesFolder(folderPath)) {
        return SessionStartPosition(index = safeIndex, positionMs = 0L, usedSnapshot = false)
    }

    val restoredIndex = songs.indexOfFirst { it.path == currentPath }
        .takeIf { it >= 0 }
        ?: safeIndex

    return SessionStartPosition(
        index = restoredIndex,
        positionMs = positionMs.coerceAtLeast(0L),
        usedSnapshot = true
    )
}

fun PlaybackSessionSnapshot.VideoSession?.resolveVideoSessionStart(
    videos: List<Video>,
    requestedIndex: Int,
    folderPath: String?
): SessionStartPosition {
    val safeIndex = requestedIndex.coerceInPlaylist(videos.size)
    val requestedVideo = videos.getOrNull(safeIndex)
        ?: return SessionStartPosition(index = 0, positionMs = 0L, usedSnapshot = false)

    if (this == null || currentPath != requestedVideo.path || !matchesFolder(folderPath)) {
        return SessionStartPosition(index = safeIndex, positionMs = 0L, usedSnapshot = false)
    }

    val restoredIndex = videos.indexOfFirst { it.path == currentPath }
        .takeIf { it >= 0 }
        ?: safeIndex

    return SessionStartPosition(
        index = restoredIndex,
        positionMs = positionMs.coerceAtLeast(0L),
        usedSnapshot = true
    )
}

private fun PlaybackSessionSnapshot.matchesFolder(targetFolderPath: String?): Boolean {
    return folderPath == null || targetFolderPath == null || folderPath == targetFolderPath
}

private fun Int.coerceInPlaylist(size: Int): Int {
    return if (size <= 0) 0 else coerceIn(0, size - 1)
}
