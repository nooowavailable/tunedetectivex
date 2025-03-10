package com.dev.tunedetectivex

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SavedArtistDao {

    @Query("SELECT * FROM saved_artist WHERE id = :artistId LIMIT 1")
    suspend fun getArtistById(artistId: Long): SavedArtist?

    @Query("SELECT * FROM saved_artist")
    suspend fun getAll(): List<SavedArtist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artist: SavedArtist)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artists: List<SavedArtist>)

    @Delete
    suspend fun delete(artist: SavedArtist)

    @Query("UPDATE saved_artist SET lastReleaseDate = :lastReleaseDate, lastReleaseTitle = :lastReleaseTitle WHERE id = :artistId")
    suspend fun updateReleaseDetails(artistId: Long, lastReleaseDate: String, lastReleaseTitle: String)

    @Query("UPDATE saved_artist SET profileImageUrl = :profileImageUrl WHERE id = :artistId")
    suspend fun updateArtistDetails(artistId: Long, profileImageUrl: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelease(release: ReleaseItem)

    @Query("SELECT EXISTS(SELECT 1 FROM sent_notification WHERE notificationHash = :hash)")
    suspend fun isNotificationSent(hash: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markNotificationAsSent(notification: SentNotification)

    @Query("SELECT * FROM saved_artist WHERE name = :artistName LIMIT 1")
    suspend fun getArtistByName(artistName: String): SavedArtist?


    @Dao
    interface SearchHistoryDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(searchHistory: SearchHistory)

        @Update
        suspend fun update(searchHistory: SearchHistory)

        @Query("SELECT * FROM search_history")
        suspend fun getAllHistory(): List<SearchHistory>

        @Query("SELECT * FROM search_history WHERE artistId = :artistId LIMIT 1")
        suspend fun getHistoryByArtistId(artistId: Long): SearchHistory?

        @Delete
        suspend fun delete(history: SearchHistory)

        @Query("DELETE FROM search_history")
        suspend fun clearHistory()
    }
}
