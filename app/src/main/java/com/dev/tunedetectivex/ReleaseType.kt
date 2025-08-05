package com.dev.tunedetectivex

enum class ReleaseType {
    ALBUM, SINGLE, EP, DEFAULT;

    companion object {
        fun from(raw: String?): ReleaseType {
            return when (raw?.lowercase()) {
                "album", "album_release", "collection" -> ALBUM
                "single" -> SINGLE
                "ep" -> EP
                else -> DEFAULT
            }
        }
    }
}