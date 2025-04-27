package com.dev.tunedetectivex.models

data class ITunesArtistSearchResponse(
    val resultCount: Int,
    val results: List<ITunesArtistItem>
)

data class ITunesAlbumSearchResponse(
    val resultCount: Int,
    val results: List<ITunesTrackOrAlbumItem>
)

data class ITunesArtistItem(
    val artistId: Long?,
    val artistName: String?,
    val primaryGenreName: String?,
    val artworkUrl100: String?,
    val albums: List<ITunesAlbumItem>? = null
) {
    fun getHighResArtwork(): String? {
        return artworkUrl100?.toHighResArtwork()
    }
}

data class ITunesAlbumItem(
    val collectionId: Long?,
    val collectionName: String?,
    val artistName: String?,
    val artworkUrl100: String?,
    val collectionType: String?,
    val title: String,
    val releaseDate: String?,
    val coverUrl: String
)

data class ITunesTrackOrAlbumItem(
    val wrapperType: String?,
    val kind: String?,
    val trackName: String?,
    val trackTimeMillis: Long?,
    val collectionId: Long?,
    val collectionName: String?,
    val artistName: String?,
    val releaseDate: String?,
    val artworkUrl100: String?,
    val collectionType: String?
) {
    fun getHighResArtwork(): String? {
        return artworkUrl100?.toHighResArtwork()
    }
}

fun String.toHighResArtwork(): String {
    return this.replace("100x100bb", "1200x1200bb")
}
