package com.dev.tunedetectivex

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SearchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(searchHistory: SearchHistory)

    @Update
    suspend fun update(searchHistory: SearchHistory)

    @Query("SELECT * FROM search_history ORDER BY id DESC")
    suspend fun getAllHistory(): List<SearchHistory>

    @Query("SELECT * FROM search_history WHERE deezerId = :deezerId LIMIT 1")
    suspend fun getByDeezerId(deezerId: Long): SearchHistory?

    @Query("SELECT * FROM search_history WHERE itunesId = :itunesId LIMIT 1")
    suspend fun getByItunesId(itunesId: Long): SearchHistory?

    @Delete
    suspend fun delete(history: SearchHistory)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()
}