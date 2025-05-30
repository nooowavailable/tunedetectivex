package com.dev.tunedetectivex

data class DeezerTracksResponse(
    val data: List<Track>
)

data class Track(
    val title: String,
    val duration: Int,
    val artistName: String? = null,
    val previewUrl: String? = null
)
