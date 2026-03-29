package com.example.newaudio.di

import android.content.Context
import androidx.room.Room
import com.example.newaudio.BuildConfig
import com.example.newaudio.data.database.AppDatabase
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.dao.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val builder = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "newaudio_db"
        )
            // Versions 1 and 2 were pre-release (no schema exports) — destructive migration
            // is acceptable for those. From v3 onwards, add explicit migrations to
            // AppDatabaseMigrations and register them with .addMigrations(...).
            .fallbackToDestructiveMigrationFrom(1, 2)

        if (BuildConfig.DEBUG) {
            builder.setQueryCallback({ sqlQuery, bindArgs ->
                Timber.tag("DB_INSPECTOR").d("SQL: $sqlQuery ARGS: $bindArgs")
            }, Executors.newSingleThreadExecutor())
        }

        return builder.build()
    }

    @Provides
    fun provideSongDao(database: AppDatabase): SongDao {
        return database.songDao()
    }

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao {
        return database.playlistDao()
    }
}
