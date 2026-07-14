package com.example.newaudio.data.media.deletion

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import javax.inject.Inject

internal enum class ActiveMediaKind {
    AUDIO,
    VIDEO
}

internal data class DeletedMediaSnapshot(
    val controllerPaths: List<String>,
    val songs: List<Song>,
    val videos: List<Video>,
    val folderPath: String?,
    val originalCurrentIndex: Int,
    val currentSongPath: String?,
    val currentVideoPath: String?
) {
    init {
        require(songs.isEmpty() || videos.isEmpty()) {
            "A delete snapshot cannot contain active audio and video queues"
        }
        require(currentSongPath == null || currentVideoPath == null) {
            "A delete snapshot cannot contain two active media items"
        }
    }
}

internal data class DeletedMediaDecision(
    val remainingSongs: List<Song>,
    val remainingVideos: List<Video>,
    val folderPath: String?,
    val indicesToRemove: List<Int>,
    val originalCurrentIndex: Int,
    val targetIndex: Int?,
    val deletedActiveMedia: ActiveMediaKind?
)

/**
 * Pure delete planner. Matching is case-sensitive after slash normalization.
 * A deleted path matches itself and descendants on a separator boundary, but
 * not prefix siblings. Blank paths, filesystem roots, scheme-only URI roots
 * and paths containing `.` or `..` segments are deliberately rejected. URI
 * and UNC-like inputs otherwise use the same normalized boundary matching.
 * When the player index is invalid, the active media path is used before
 * falling back to index zero.
 */
class DeletedMediaDecisionCalculator @Inject constructor() {
    internal fun calculate(
        deletedPaths: List<String>,
        snapshot: DeletedMediaSnapshot
    ): DeletedMediaDecision? {
        val normalizedDeletedPaths = deletedPaths
            .mapNotNull(::normalizePath)
            .distinct()
        if (normalizedDeletedPaths.isEmpty() || snapshot.controllerPaths.isEmpty()) return null

        val indicesToRemove = snapshot.controllerPaths.indices.filter { index ->
            isDeleted(snapshot.controllerPaths[index], normalizedDeletedPaths)
        }
        if (indicesToRemove.isEmpty()) return null

        val activeMedia = activeMediaKind(snapshot)
        val activePath = when (activeMedia) {
            ActiveMediaKind.AUDIO -> snapshot.currentSongPath
            ActiveMediaKind.VIDEO -> snapshot.currentVideoPath
            null -> null
        }
        val currentIndex = snapshot.originalCurrentIndex
            .takeIf { it in snapshot.controllerPaths.indices }
            ?: activePath?.let(snapshot.controllerPaths::indexOf)?.takeIf { it >= 0 }
            ?: 0
        val activeMediaWasDeleted = activeMedia != null && (
            currentIndex in indicesToRemove ||
                activePath?.let { isDeleted(it, normalizedDeletedPaths) } == true
            )
        val remainingControllerCount = snapshot.controllerPaths.size - indicesToRemove.size
        val targetIndex = if (activeMediaWasDeleted && remainingControllerCount > 0) {
            val removedBeforeCurrent = indicesToRemove.count { it < currentIndex }
            (currentIndex - removedBeforeCurrent).coerceIn(0, remainingControllerCount - 1)
        } else {
            null
        }

        return DeletedMediaDecision(
            remainingSongs = snapshot.songs.filterNot {
                isDeleted(it.path, normalizedDeletedPaths)
            },
            remainingVideos = snapshot.videos.filterNot {
                isDeleted(it.path, normalizedDeletedPaths)
            },
            folderPath = snapshot.folderPath,
            indicesToRemove = indicesToRemove,
            originalCurrentIndex = currentIndex,
            targetIndex = targetIndex,
            deletedActiveMedia = activeMedia.takeIf { activeMediaWasDeleted }
        )
    }

    private fun activeMediaKind(snapshot: DeletedMediaSnapshot): ActiveMediaKind? {
        return when {
            snapshot.currentVideoPath != null -> ActiveMediaKind.VIDEO
            snapshot.currentSongPath != null -> ActiveMediaKind.AUDIO
            snapshot.videos.isNotEmpty() && snapshot.songs.isEmpty() -> ActiveMediaKind.VIDEO
            snapshot.songs.isNotEmpty() && snapshot.videos.isEmpty() -> ActiveMediaKind.AUDIO
            else -> null
        }
    }

    private fun isDeleted(path: String, normalizedDeletedPaths: List<String>): Boolean {
        val normalizedPath = normalizePath(path) ?: return false
        return normalizedDeletedPaths.any { deletedPath ->
            normalizedPath == deletedPath || normalizedPath.startsWith("$deletedPath/")
        }
    }

    private fun normalizePath(path: String): String? {
        val withForwardSlashes = path.trim().replace('\\', '/')
        if (withForwardSlashes.isBlank()) return null

        val schemeSeparator = withForwardSlashes.indexOf("://")
        val normalized = if (schemeSeparator >= 0) {
            val prefix = withForwardSlashes.substring(0, schemeSeparator + 3)
            val remainder = withForwardSlashes.substring(schemeSeparator + 3)
                .replace(REPEATED_SEPARATOR, "/")
                .trimEnd('/')
            if (remainder.isBlank()) return null
            prefix + remainder
        } else {
            withForwardSlashes
                .replace(REPEATED_SEPARATOR, "/")
                .trimEnd('/')
        }
        val pathPart = if (schemeSeparator >= 0) {
            normalized.substring(schemeSeparator + 3)
        } else {
            normalized
        }
        val containsDotSegment = pathPart.split('/').any { it == "." || it == ".." }
        return normalized.takeIf {
            it.isNotBlank() && it != "/" && !DRIVE_ROOT.matches(it) && !containsDotSegment
        }
    }

    private companion object {
        val REPEATED_SEPARATOR = Regex("/+")
        val DRIVE_ROOT = Regex("^[A-Za-z]:$")
    }
}
