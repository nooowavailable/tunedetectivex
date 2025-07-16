package com.dev.tunedetectivex

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SavedArtist::class,
        SentNotification::class,
        ReleaseItem::class,
        SearchHistory::class,
        DnsEntry::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedArtistDao(): SavedArtistDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun dnsCacheDao(): DnsCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `dns_cache` (
                      `hostname` TEXT NOT NULL, 
                      `ip` TEXT NOT NULL, 
                      `timestamp` INTEGER NOT NULL, 
                      PRIMARY KEY(`hostname`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .addMigrations(MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
