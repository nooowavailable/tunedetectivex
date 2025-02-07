package com.dev.tunedetectivex

data class DeezerAlbumsResponse(
    val data: List<DeezerAlbum>
)

data class DeezerAlbum(
    val id: Long,
    val title: String,
    val release_date: String,
    val cover: String,
    val cover_xl: String,
    val cover_big: String,
    val cover_medium: String,
    val cover_small: String,
    val record_type: String,
    val artist: DeezerArtist
) {
    fun getBestCoverUrl(): String {
        return when {
            !cover_xl.isNullOrEmpty() -> cover_xl
            !cover_big.isNullOrEmpty() -> cover_big
            !cover_medium.isNullOrEmpty() -> cover_medium
            !cover_small.isNullOrEmpty() -> cover_small
            else -> ""
        }
    }
}

data class DeezerArtist(
    val id: Long,
    val name: String,
    val picture: String,
    val picture_small: String,
    val picture_medium: String,
    val picture_big: String,
    val picture_xl: String
) {
    fun getBestPictureUrl(): String {
        return when {
            !picture_xl.isNullOrEmpty() -> picture_xl
            !picture_big.isNullOrEmpty() -> picture_big
            !picture_medium.isNullOrEmpty() -> picture_medium
            !picture_small.isNullOrEmpty() -> picture_small
            else -> ""
        }
    }
}
