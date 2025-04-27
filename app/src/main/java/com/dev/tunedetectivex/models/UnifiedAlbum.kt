package com.dev.tunedetectivex.models

data class UnifiedAlbum(
    val id: String,
    val title: String,
    val artistName: String,
    val releaseDate: String,
    val coverUrl: String,
    val releaseType: String? = null,
    val deezerId: Long? = null,
    val itunesId: Long? = null
)
