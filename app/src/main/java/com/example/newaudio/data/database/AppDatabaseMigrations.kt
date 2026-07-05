package com.example.newaudio.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database migrations for AppDatabase.
 *
 * Versions 1 and 2 were pre-release and have no schema export.
 * Those are handled via fallbackToDestructiveMigrationFrom(1, 2) in DatabaseModule.
 *
 * Starting from version 3, every schema change MUST have an explicit migration here.
 * Steps when bumping the DB version:
 *   1. Increment `version` in @Database
 *   2. Add a MIGRATION_X_Y object below
 *   3. Register it in DatabaseModule.provideAppDatabase
 *   4. Run ./gradlew assembleDebug to generate the new schema JSON
 *   5. Commit both the migration and the schema JSON
 */
object AppDatabaseMigrations {

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `videos` (
                    `path` TEXT NOT NULL,
                    `contentUri` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `duration` INTEGER NOT NULL,
                    `thumbnailUri` TEXT,
                    `parentPath` TEXT NOT NULL,
                    `filename` TEXT NOT NULL,
                    `lastModified` INTEGER NOT NULL,
                    `size` INTEGER NOT NULL,
                    `width` INTEGER NOT NULL DEFAULT 0,
                    `height` INTEGER NOT NULL DEFAULT 0,
                    `fileHash` TEXT,
                    PRIMARY KEY(`path`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_videos_parentPath` ON `videos` (`parentPath`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_videos_title` ON `videos` (`title`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_videos_filename` ON `videos` (`filename`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_videos_filename_size` ON `videos` (`filename`, `size`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_videos_parentPath_title` ON `videos` (`parentPath`, `title`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_videos_fileHash` ON `videos` (`fileHash`)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `video_playlists` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `video_playlist_items` (
                    `playlistId` INTEGER NOT NULL,
                    `videoPath` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    PRIMARY KEY(`playlistId`, `videoPath`),
                    FOREIGN KEY(`playlistId`) REFERENCES `video_playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`videoPath`) REFERENCES `videos`(`path`) ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_playlist_items_playlistId` ON `video_playlist_items` (`playlistId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_playlist_items_videoPath` ON `video_playlist_items` (`videoPath`)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `video_markers` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `videoPath` TEXT NOT NULL,
                    `fileHash` TEXT,
                    `filename` TEXT NOT NULL,
                    `fileSize` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `positionMs` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`videoPath`) REFERENCES `videos`(`path`) ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_markers_videoPath` ON `video_markers` (`videoPath`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_markers_fileHash` ON `video_markers` (`fileHash`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_markers_filename_fileSize_durationMs` ON `video_markers` (`filename`, `fileSize`, `durationMs`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_markers_videoPath_positionMs` ON `video_markers` (`videoPath`, `positionMs`)")
        }
    }
}
