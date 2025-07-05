package com.dev.tunedetectivex

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedArtist::class,
        SentNotification::class,
        ReleaseItem::class,
        SearchHistory::class,
        DnsEntry::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedArtistDao(): SavedArtistDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun dnsCacheDao(): DnsCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}