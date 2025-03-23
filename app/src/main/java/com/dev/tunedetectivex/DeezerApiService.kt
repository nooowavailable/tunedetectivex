package com.dev.tunedetectivex

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DeezerApiService {

    @GET("/")
    fun ping(): Call<ResponseBody>

    @GET("search/artist")
    fun searchArtist(@Query("q") query: String): Call<DeezerSimilarArtistsResponse>

    @GET("artist/{id}/albums")
    fun getArtistReleases(
        @Path("id") artistId: Long,
        @Query("index") offset: Int = 0
    ): Call<DeezerAlbumsResponse>

    @GET("album/{id}/tracks")
    fun getTracklist(@Path("id") albumId: Long): Call<DeezerTracksResponse>

    @GET("album/{id}")
    fun getAlbumDetails(@Path("id") albumId: Long): Call<DeezerAlbum>

    companion object {
        fun create(): DeezerApiService {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.deezer.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(DeezerApiService::class.java)
        }
    }

    @GET("artist/{id}")
    fun getArtistDetails(@Path("id") artistId: Long): Call<DeezerArtist>
}

