package com.dev.tunedetectivex

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dns_cache")
data class DnsEntry(
    @PrimaryKey val hostname: String,
    val ip: String,
    val timestamp: Long
)
