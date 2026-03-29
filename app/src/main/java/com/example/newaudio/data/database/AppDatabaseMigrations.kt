package com.example.newaudio.data.database

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

    // Example template for the next migration — fill in the actual ALTER / CREATE statements:
    //
    // val MIGRATION_3_4 = object : Migration(3, 4) {
    //     override fun migrate(db: SupportSQLiteDatabase) {
    //         db.execSQL("ALTER TABLE songs ADD COLUMN newColumn TEXT")
    //     }
    // }
}
