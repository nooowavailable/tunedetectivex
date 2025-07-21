package com.dev.tunedetectivex

data class DeezerAlbumsResponse(
    val data: List<DeezerAlbum>
)

data class DeezerAlbum(
    val id: Long,
    val title: String,
    val release_date: String,
    val cover: String? = null,
    val cover_xl: String? = null,
    val cover_big: String? = null,
    val cover_medium: String? = null,
    val cover_small: String? = null,
    val record_type: String? = null,
    val artist: DeezerAlbumArtist?
) {
    fun getBestCoverUrl(): String {
        return when {
            !cover_xl.isNullOrEmpty() -> cover_xl
            !cover_big.isNullOrEmpty() -> cover_big
            !cover_medium.isNullOrEmpty() -> cover_medium
            !cover_small.isNullOrEmpty() -> cover_small
            !cover.isNullOrEmpty() -> cover
            else -> ""
        }
    }
}

data class DeezerArtist(
    var id: Long,
    val name: String,
    val picture: String,
    val picture_small: String,
    val picture_medium: String,
    val picture_big: String,
    val picture_xl: String,
    var itunesId: Long? = null
) {
    fun getBestPictureUrl(): String {
        return when {
            !picture_xl.isNullOrEmpty() -> picture_xl
            !picture_big.isNullOrEmpty() -> picture_big
            !picture_medium.isNullOrEmpty() -> picture_medium
            !picture_small.isNullOrEmpty() -> picture_small
            !picture.isNullOrEmpty() -> picture
            else -> ""
        }
    }
}

data class DeezerAlbumArtist(
    val id: Long,
    val name: String,
    val link: String? = null,
    val picture: String? = null,
    val tracklist: String? = null
)