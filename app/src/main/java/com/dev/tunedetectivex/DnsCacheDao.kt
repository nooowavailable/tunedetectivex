package com.dev.tunedetectivex

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DnsCacheDao {
    @Query("SELECT * FROM dns_cache WHERE hostname = :host")
    suspend fun get(host: String): DnsEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: DnsEntry)

    @Query("DELETE FROM dns_cache WHERE timestamp < :threshold")
    suspend fun clearOld(threshold: Long)

    @Query("DELETE FROM dns_cache")
    suspend fun clearAll()
}
