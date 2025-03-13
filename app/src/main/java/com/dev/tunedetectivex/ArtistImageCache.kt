package com.dev.tunedetectivex

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ArtistImageCache {
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val MAX_CACHE_SIZE = 100
    private val EXPIRATION_TIME_MS = TimeUnit.HOURS.toMillis(1)

    private data class CacheEntry(val imageUrl: String, val timestamp: Long)

    fun get(artistName: String): String? {
        val entry = cache[artistName]
        return if (entry != null && !isExpired(entry.timestamp)) {
            entry.imageUrl
        } else {
            cache.remove(artistName)
            null
        }
    }

    fun put(artistName: String, imageUrl: String) {
        if (cache.size >= MAX_CACHE_SIZE) {
            val oldestKey = cache.keys.firstOrNull()
            if (oldestKey != null) {
                cache.remove(oldestKey)
            }
        }
        cache[artistName] = CacheEntry(imageUrl, System.currentTimeMillis())
    }

    private fun isExpired(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp > EXPIRATION_TIME_MS
    }
}