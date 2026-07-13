package com.example.newaudio.data.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun deleteDatabaseBeforeTest() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun deleteDatabaseAfterTest() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrate3To7_validatesSchema() {
        helper.createDatabase(TEST_DB, 3).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            AppDatabaseMigrations.MIGRATION_3_4,
            AppDatabaseMigrations.MIGRATION_4_5,
            AppDatabaseMigrations.MIGRATION_5_6,
            AppDatabaseMigrations.MIGRATION_6_7
        ).close()
    }

    @Test
    fun migrate4To7_preservesAudioPlaylistAndEnablesPathCascade() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            insertSong(db, AUDIO_PATH)
            db.execSQL("INSERT INTO `playlists` (`id`, `name`, `position`, `createdAt`) VALUES (1, 'Audio', 0, 1)")
            db.execSQL("INSERT INTO `playlist_songs` (`playlistId`, `songPath`, `position`) VALUES (1, ?, 3)", arrayOf(AUDIO_PATH))
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            AppDatabaseMigrations.MIGRATION_4_5,
            AppDatabaseMigrations.MIGRATION_5_6,
            AppDatabaseMigrations.MIGRATION_6_7
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("UPDATE `songs` SET `path` = ? WHERE `path` = ?", arrayOf(AUDIO_PATH_NEW, AUDIO_PATH))
            assertSingleText(db, "SELECT `songPath` FROM `playlist_songs` WHERE `playlistId` = 1", AUDIO_PATH_NEW)
            assertSingleLong(db, "SELECT `position` FROM `playlist_songs` WHERE `playlistId` = 1", 3L)
        }
    }

    @Test
    fun migrate5To7_preservesVideoPlaylistAndEnablesPathCascade() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            insertVideo(db, VIDEO_PATH)
            db.execSQL("INSERT INTO `video_playlists` (`id`, `name`, `position`, `createdAt`) VALUES (2, 'Video', 0, 2)")
            db.execSQL("INSERT INTO `video_playlist_items` (`playlistId`, `videoPath`, `position`) VALUES (2, ?, 4)", arrayOf(VIDEO_PATH))
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            AppDatabaseMigrations.MIGRATION_5_6,
            AppDatabaseMigrations.MIGRATION_6_7
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("UPDATE `videos` SET `path` = ? WHERE `path` = ?", arrayOf(VIDEO_PATH_NEW, VIDEO_PATH))
            assertSingleText(db, "SELECT `videoPath` FROM `video_playlist_items` WHERE `playlistId` = 2", VIDEO_PATH_NEW)
            assertSingleLong(db, "SELECT `position` FROM `video_playlist_items` WHERE `playlistId` = 2", 4L)
        }
    }

    @Test
    fun migrate6To7_preservesVideoMarkersWhenPathChanges() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            insertVideo(db, VIDEO_PATH)
            db.execSQL(
                """
                INSERT INTO `video_markers`
                    (`id`, `videoPath`, `fileHash`, `filename`, `fileSize`, `durationMs`, `positionMs`, `createdAt`, `updatedAt`)
                VALUES (7, ?, NULL, 'clip.mp4', 100, 1000, 500, 1, 1)
                """.trimIndent(),
                arrayOf(VIDEO_PATH)
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            AppDatabaseMigrations.MIGRATION_6_7
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("UPDATE `videos` SET `path` = ? WHERE `path` = ?", arrayOf(VIDEO_PATH_NEW, VIDEO_PATH))
            assertSingleText(db, "SELECT `videoPath` FROM `video_markers` WHERE `id` = 7", VIDEO_PATH_NEW)
        }
    }

    @Test
    fun migrate6To7_preservesSharedMediaAcrossMultiplePlaylistsAndMarkers() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            insertSong(db, AUDIO_PATH)
            insertSong(db, AUDIO_PATH_SECOND)
            insertVideo(db, VIDEO_PATH)
            insertVideo(db, VIDEO_PATH_SECOND)
            db.execSQL("INSERT INTO `playlists` (`id`, `name`, `position`, `createdAt`) VALUES (1, 'One', 0, 1)")
            db.execSQL("INSERT INTO `playlists` (`id`, `name`, `position`, `createdAt`) VALUES (2, 'Two', 1, 2)")
            db.execSQL("INSERT INTO `playlist_songs` (`playlistId`, `songPath`, `position`) VALUES (1, ?, 3)", arrayOf(AUDIO_PATH))
            db.execSQL("INSERT INTO `playlist_songs` (`playlistId`, `songPath`, `position`) VALUES (2, ?, 7)", arrayOf(AUDIO_PATH))
            db.execSQL("INSERT INTO `playlist_songs` (`playlistId`, `songPath`, `position`) VALUES (1, ?, 4)", arrayOf(AUDIO_PATH_SECOND))

            db.execSQL("INSERT INTO `video_playlists` (`id`, `name`, `position`, `createdAt`) VALUES (10, 'V-One', 0, 1)")
            db.execSQL("INSERT INTO `video_playlists` (`id`, `name`, `position`, `createdAt`) VALUES (11, 'V-Two', 1, 2)")
            db.execSQL("INSERT INTO `video_playlist_items` (`playlistId`, `videoPath`, `position`) VALUES (10, ?, 2)", arrayOf(VIDEO_PATH))
            db.execSQL("INSERT INTO `video_playlist_items` (`playlistId`, `videoPath`, `position`) VALUES (11, ?, 8)", arrayOf(VIDEO_PATH))
            db.execSQL("INSERT INTO `video_playlist_items` (`playlistId`, `videoPath`, `position`) VALUES (10, ?, 5)", arrayOf(VIDEO_PATH_SECOND))
            db.execSQL(
                """
                INSERT INTO `video_markers`
                    (`id`, `videoPath`, `fileHash`, `filename`, `fileSize`, `durationMs`, `positionMs`, `createdAt`, `updatedAt`)
                VALUES (7, ?, NULL, 'clip.mp4', 100, 1000, 500, 1, 1)
                """.trimIndent(),
                arrayOf(VIDEO_PATH)
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            AppDatabaseMigrations.MIGRATION_6_7
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("UPDATE `songs` SET `path` = ? WHERE `path` = ?", arrayOf(AUDIO_PATH_NEW, AUDIO_PATH))
            db.execSQL("UPDATE `videos` SET `path` = ? WHERE `path` = ?", arrayOf(VIDEO_PATH_NEW, VIDEO_PATH))

            assertSingleLong(db, "SELECT COUNT(*) FROM `playlist_songs`", 3L)
            assertSingleLong(db, "SELECT COUNT(*) FROM `video_playlist_items`", 3L)
            assertSingleText(db, "SELECT `songPath` FROM `playlist_songs` WHERE `playlistId` = 1 AND `position` = 3", AUDIO_PATH_NEW)
            assertSingleText(db, "SELECT `songPath` FROM `playlist_songs` WHERE `playlistId` = 2 AND `position` = 7", AUDIO_PATH_NEW)
            assertSingleText(db, "SELECT `videoPath` FROM `video_playlist_items` WHERE `playlistId` = 10 AND `position` = 2", VIDEO_PATH_NEW)
            assertSingleText(db, "SELECT `videoPath` FROM `video_playlist_items` WHERE `playlistId` = 11 AND `position` = 8", VIDEO_PATH_NEW)
            assertSingleText(db, "SELECT `videoPath` FROM `video_markers` WHERE `id` = 7", VIDEO_PATH_NEW)
        }
    }

    private fun insertSong(db: SupportSQLiteDatabase, path: String) {
        db.execSQL(
            """
            INSERT INTO `songs`
                (`path`, `contentUri`, `title`, `artist`, `album`, `duration`, `albumArtPath`, `parentPath`, `filename`, `lastModified`, `size`, `fileHash`)
            VALUES (?, ?, 'Track', 'Artist', 'Album', 1000, NULL, '/music', 'track.mp3', 1, 100, NULL)
            """.trimIndent(),
            arrayOf(path, path)
        )
    }

    private fun insertVideo(db: SupportSQLiteDatabase, path: String) {
        db.execSQL(
            """
            INSERT INTO `videos`
                (`path`, `contentUri`, `title`, `duration`, `thumbnailUri`, `parentPath`, `filename`, `lastModified`, `size`, `width`, `height`, `fileHash`)
            VALUES (?, ?, 'Clip', 1000, NULL, '/video', 'clip.mp4', 1, 100, 1920, 1080, NULL)
            """.trimIndent(),
            arrayOf(path, path)
        )
    }

    private fun assertSingleText(db: SupportSQLiteDatabase, query: String, expected: String) {
        db.query(query).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(expected, cursor.getString(0))
            assertEquals(false, cursor.moveToNext())
        }
    }

    private fun assertSingleLong(db: SupportSQLiteDatabase, query: String, expected: Long) {
        db.query(query).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(expected, cursor.getLong(0))
            assertEquals(false, cursor.moveToNext())
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
        const val AUDIO_PATH = "/music/track.mp3"
        const val AUDIO_PATH_NEW = "/music/track-renamed.mp3"
        const val AUDIO_PATH_SECOND = "/music/track-2.mp3"
        const val VIDEO_PATH = "/video/clip.mp4"
        const val VIDEO_PATH_NEW = "/video/clip-renamed.mp4"
        const val VIDEO_PATH_SECOND = "/video/clip-2.mp4"
    }
}
