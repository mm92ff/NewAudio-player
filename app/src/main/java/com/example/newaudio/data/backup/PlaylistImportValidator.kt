package com.example.newaudio.data.backup

import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.ImportFailure
import com.example.newaudio.domain.repository.PlaylistExportContainer
import javax.inject.Inject

class PlaylistImportValidator @Inject constructor() {

    fun validate(container: PlaylistExportContainer) {
        if (container.version !in MIN_IMPORT_VERSION..MAX_IMPORT_VERSION) {
            reject(ImportFailure.UNSUPPORTED_VERSION)
        }
        if (container.playlists.size + container.videoPlaylists.size > MAX_PLAYLISTS ||
            container.videoMarkers.size > MAX_MARKERS
        ) {
            reject(ImportFailure.LIMIT_EXCEEDED)
        }

        var mediaItems = 0L
        container.playlists.forEach { playlist ->
            requireSafeString(playlist.name, MAX_NAME_LENGTH)
            requireValid(playlist.createdAt >= 0L)
            mediaItems += playlist.songs.size
            playlist.songs.forEach { song ->
                requireSafePath(song.path)
                requireSafeString(song.title, MAX_TEXT_LENGTH)
                requireSafeString(song.artist, MAX_TEXT_LENGTH)
                song.fileHash?.let { requireSafeString(it, MAX_HASH_LENGTH) }
                requireValid(song.size >= 0L)
            }
        }
        container.videoPlaylists.forEach { playlist ->
            requireSafeString(playlist.name, MAX_NAME_LENGTH)
            requireValid(playlist.createdAt >= 0L)
            mediaItems += playlist.videos.size
            playlist.videos.forEach { video ->
                requireSafePath(video.path)
                requireSafeString(video.title, MAX_TEXT_LENGTH)
                requireValid(video.duration >= 0L && video.size >= 0L)
            }
        }
        container.videoMarkers.forEach { marker ->
            requireSafePath(marker.videoPath)
            requireSafeString(marker.filename, MAX_NAME_LENGTH)
            marker.fileHash?.let { requireSafeString(it, MAX_HASH_LENGTH) }
            requireValid(
                marker.fileSize >= 0L &&
                    marker.durationMs >= 0L &&
                    marker.positionMs in 0L..marker.durationMs.coerceAtLeast(0L) &&
                    marker.createdAt >= 0L &&
                    marker.updatedAt >= 0L
            )
        }
        container.settings?.let(::validatePreferences)
        if (mediaItems > MAX_MEDIA_ITEMS) reject(ImportFailure.LIMIT_EXCEEDED)
    }

    private fun validatePreferences(preferences: UserPreferences) {
        requireValid(COLOR_PATTERN.matches(preferences.primaryColor))
        requireValid(COLOR_PATTERN.matches(preferences.settingsCardBorderColor))
        requireSafeOptionalPath(preferences.musicFolderPath)
        requireSafeOptionalPath(preferences.videoFolderPath)
        requireValid(
            preferences.miniPlayerProgressBarHeight.isFinite() &&
                preferences.miniPlayerProgressBarHeight in 0f..100f
        )
        requireValid(
            preferences.fullScreenPlayerProgressBarHeight.isFinite() &&
                preferences.fullScreenPlayerProgressBarHeight in 0f..100f
        )
        requireValid(
            preferences.backgroundTintFraction.isFinite() &&
                preferences.backgroundTintFraction in 0f..1f
        )
        requireValid(
            preferences.settingsCardBorderWidth.isFinite() &&
                preferences.settingsCardBorderWidth in 0f..20f
        )
        requireValid(preferences.videoGalleryColumns in 1..10)
    }

    private fun requireSafePath(value: String) {
        requireSafeString(value, MAX_PATH_LENGTH)
        requireValid(
            value.startsWith('/') ||
                value.startsWith("content://") ||
                value.startsWith("file://")
        )
    }

    private fun requireSafeOptionalPath(value: String) {
        if (value.isNotBlank()) requireSafePath(value)
    }

    private fun requireValid(condition: Boolean) {
        if (!condition) reject(ImportFailure.INVALID_FORMAT)
    }

    private fun requireSafeString(value: String, maxLength: Int) {
        if (value.length > maxLength || value.indexOf('\u0000') >= 0) {
            reject(ImportFailure.LIMIT_EXCEEDED)
        }
    }

    private fun reject(failure: ImportFailure): Nothing {
        throw PlaylistBackupException(failure)
    }

    internal companion object {
        const val MIN_IMPORT_VERSION = 1
        const val MAX_IMPORT_VERSION = 4
        const val MAX_PLAYLISTS = 1_000
        const val MAX_MEDIA_ITEMS = 100_000L
        const val MAX_MARKERS = 50_000
        const val MAX_NAME_LENGTH = 255
        const val MAX_TEXT_LENGTH = 1_024
        const val MAX_PATH_LENGTH = 4_096
        const val MAX_HASH_LENGTH = 256
        val COLOR_PATTERN = Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")
    }
}
