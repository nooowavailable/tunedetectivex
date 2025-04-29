package com.dev.tunedetectivex

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markNotificationAsSent(notification: SentNotification)

    @Query("SELECT * FROM saved_artist WHERE name = :artistName LIMIT 1")
    suspend fun getArtistByName(artistName: String): SavedArtist?

    @Query("UPDATE saved_artist SET itunesId = :itunesId WHERE id = :artistId")
    fun updateItunesId(artistId: Long, itunesId: Long)

    @Query("UPDATE saved_artist SET deezerId = :deezerId WHERE id = :artistId")
    fun updateDeezerId(artistId: Long, deezerId: Long)

    @Query("SELECT * FROM saved_artist WHERE notifyOnNewRelease = 1")
    suspend fun getAllWithNotificationsEnabled(): List<SavedArtist>

    @Query("UPDATE saved_artist SET notifyOnNewRelease = :notify WHERE id = :artistId")
    suspend fun setNotifyOnNewRelease(artistId: Long, notify: Boolean)
}
