package com.dev.tunedetectivex.api

import com.dev.tunedetectivex.models.ITunesAlbumSearchResponse
import com.dev.tunedetectivex.models.ITunesArtistSearchResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface ITunesApiService {
    @GET("search")
    fun searchArtist(
        @Query("term") term: String,
        @Query("entity") entity: String = "musicArtist"
    ): Call<ITunesArtistSearchResponse>

    @GET("lookup")
    fun lookupArtistWithAlbums(
        @Query("id") artistId: Long,
        @Query("entity") entity: String = "album"
    ): Call<ITunesAlbumSearchResponse>

    @GET("lookup")
    fun lookupAlbumWithTracks(
        @Query("id") albumId: Long,
        @Query("entity") entity: String = "song"
    ): Call<ITunesAlbumSearchResponse>
}