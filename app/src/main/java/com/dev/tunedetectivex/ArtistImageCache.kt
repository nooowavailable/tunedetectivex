package com.dev.tunedetectivex

import java.util.concurrent.ConcurrentHashMap

object ArtistImageCache {
    private val cache = ConcurrentHashMap<String, String>()

    fun get(artistName: String): String? {
        return cache[artistName]
    }

    fun put(artistName: String, imageUrl: String) {
        cache[artistName] = imageUrl
    }

    fun clear() {
        cache.clear()
    }
}