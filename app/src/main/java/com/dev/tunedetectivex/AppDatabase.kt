package com.dev.tunedetectivex

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SavedArtist::class, SentNotification::class, ReleaseItem::class, SearchHistory::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedArtistDao(): SavedArtistDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE saved_artist ADD COLUMN deezerId INTEGER")
                } catch (_: Exception) {
                }

                try {
                    database.execSQL("ALTER TABLE saved_artist ADD COLUMN itunesId INTEGER")
                } catch (_: Exception) {
                }

                try {
                    database.execSQL("ALTER TABLE saved_artist ADD COLUMN isFromDeezer INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {
                }

                try {
                    database.execSQL("ALTER TABLE saved_artist ADD COLUMN isFromITunes INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {
                }

                try {
                    database.execSQL("ALTER TABLE saved_artist ADD COLUMN lastReleaseType TEXT")
                } catch (_: Exception) {
                }
            }
        }


        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}