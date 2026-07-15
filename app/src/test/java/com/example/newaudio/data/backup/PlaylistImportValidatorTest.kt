package com.example.newaudio.data.backup

import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.ImportFailure
import com.example.newaudio.domain.repository.PlaylistExportContainer
import com.example.newaudio.domain.repository.PlaylistExportModel
import com.example.newaudio.domain.repository.SongExportModel
import com.example.newaudio.domain.repository.VideoMarkerExportModel
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistImportValidatorTest {
    private val validator = PlaylistImportValidator()

    @Test
    fun `versions one through four are accepted`() {
        (1..4).forEach { version -> validator.validate(container(version = version)) }
    }

    @Test
    fun `versions outside supported range are rejected`() {
        assertEquals(ImportFailure.UNSUPPORTED_VERSION, failureOf(container(version = 0)))
        assertEquals(ImportFailure.UNSUPPORTED_VERSION, failureOf(container(version = 5)))
    }

    @Test
    fun `playlist count at limit is accepted and one above is rejected`() {
        val playlist = PlaylistExportModel("P", 0L, emptyList())
        validator.validate(container(playlists = List(1_000) { playlist }))

        assertEquals(
            ImportFailure.LIMIT_EXCEEDED,
            failureOf(container(playlists = List(1_001) { playlist }))
        )
    }

    @Test
    fun `media item count at limit is accepted and one above is rejected`() {
        val song = songModel("/music/a.mp3")
        validator.validate(
            container(playlists = listOf(PlaylistExportModel("P", 0L, List(100_000) { song })))
        )

        assertEquals(
            ImportFailure.LIMIT_EXCEEDED,
            failureOf(
                container(
                    playlists = listOf(PlaylistExportModel("P", 0L, List(100_001) { song }))
                )
            )
        )
    }

    @Test
    fun `marker count at limit is accepted and one above is rejected`() {
        val marker = marker(positionMs = 1L, durationMs = 1L)
        validator.validate(container(videoMarkers = List(50_000) { marker }))

        assertEquals(
            ImportFailure.LIMIT_EXCEEDED,
            failureOf(container(videoMarkers = List(50_001) { marker }))
        )
    }

    @Test
    fun `path and text limits are enforced`() {
        val validPath = "/" + "a".repeat(PlaylistImportValidator.MAX_PATH_LENGTH - 1)
        validator.validate(container(playlists = listOf(playlistWithSong(validPath))))

        val longPath = "/" + "a".repeat(PlaylistImportValidator.MAX_PATH_LENGTH)
        assertEquals(
            ImportFailure.LIMIT_EXCEEDED,
            failureOf(container(playlists = listOf(playlistWithSong(longPath))))
        )
        assertEquals(
            ImportFailure.LIMIT_EXCEEDED,
            failureOf(container(playlists = listOf(playlistWithSong("/music/a\u0000.mp3"))))
        )
    }

    @Test
    fun `name text and hash accept exact limits and reject one above`() {
        val validSong = songModel(
            path = "/music/a.mp3",
            title = "t".repeat(PlaylistImportValidator.MAX_TEXT_LENGTH),
            hash = "h".repeat(PlaylistImportValidator.MAX_HASH_LENGTH)
        )
        validator.validate(
            container(
                playlists = listOf(
                    PlaylistExportModel(
                        name = "n".repeat(PlaylistImportValidator.MAX_NAME_LENGTH),
                        createdAt = 0L,
                        songs = listOf(validSong)
                    )
                )
            )
        )

        assertEquals(
            ImportFailure.LIMIT_EXCEEDED,
            failureOf(
                container(
                    playlists = listOf(
                        PlaylistExportModel(
                            name = "n".repeat(PlaylistImportValidator.MAX_NAME_LENGTH + 1),
                            createdAt = 0L,
                            songs = emptyList()
                        )
                    )
                )
            )
        )
        assertEquals(
            ImportFailure.LIMIT_EXCEEDED,
            failureOf(
                container(
                    playlists = listOf(
                        PlaylistExportModel(
                            "P",
                            0L,
                            listOf(validSong.copy(title = "t".repeat(PlaylistImportValidator.MAX_TEXT_LENGTH + 1)))
                        )
                    )
                )
            )
        )
        assertEquals(
            ImportFailure.LIMIT_EXCEEDED,
            failureOf(
                container(
                    playlists = listOf(
                        PlaylistExportModel(
                            "P",
                            0L,
                            listOf(validSong.copy(fileHash = "h".repeat(PlaylistImportValidator.MAX_HASH_LENGTH + 1)))
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `relative media path is invalid`() {
        assertEquals(
            ImportFailure.INVALID_FORMAT,
            failureOf(container(playlists = listOf(playlistWithSong("music/a.mp3"))))
        )
    }

    @Test
    fun `non finite and out of range settings are invalid`() {
        assertEquals(
            ImportFailure.INVALID_FORMAT,
            failureOf(container(settings = UserPreferences.default().copy(backgroundTintFraction = Float.NaN)))
        )
        assertEquals(
            ImportFailure.INVALID_FORMAT,
            failureOf(container(settings = UserPreferences.default().copy(videoGalleryColumns = 11)))
        )
    }

    @Test
    fun `marker position accepts duration boundary and rejects values beyond it`() {
        validator.validate(container(videoMarkers = listOf(marker(positionMs = 100L, durationMs = 100L))))

        assertEquals(
            ImportFailure.INVALID_FORMAT,
            failureOf(container(videoMarkers = listOf(marker(positionMs = 101L, durationMs = 100L))))
        )
    }

    private fun container(
        version: Int = 4,
        playlists: List<PlaylistExportModel> = emptyList(),
        settings: UserPreferences? = null,
        videoMarkers: List<VideoMarkerExportModel> = emptyList()
    ) = PlaylistExportContainer(
        version = version,
        playlists = playlists,
        settings = settings,
        videoMarkers = videoMarkers
    )

    private fun playlistWithSong(path: String) = PlaylistExportModel(
        name = "Playlist",
        createdAt = 0L,
        songs = listOf(songModel(path))
    )

    private fun songModel(
        path: String,
        title: String = "Song",
        hash: String? = null
    ) = SongExportModel(
        path = path,
        title = title,
        artist = "Artist",
        size = 1L,
        fileHash = hash
    )

    private fun marker(positionMs: Long, durationMs: Long) = VideoMarkerExportModel(
        videoPath = "/video/a.mp4",
        fileHash = "hash",
        filename = "a.mp4",
        fileSize = 1L,
        durationMs = durationMs,
        positionMs = positionMs,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun failureOf(container: PlaylistExportContainer): ImportFailure {
        return try {
            validator.validate(container)
            throw AssertionError("Expected validation to fail")
        } catch (error: PlaylistBackupException) {
            error.failure
        }
    }
}
