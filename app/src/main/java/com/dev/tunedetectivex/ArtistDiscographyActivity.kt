package com.dev.tunedetectivex

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale

class ArtistDiscographyActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var apiService: DeezerApiService
    private lateinit var db: AppDatabase
    private var artistId: Long = 0
    private lateinit var artistName: String
    private lateinit var artistImageUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artist_discography)

        recyclerView = findViewById(R.id.recyclerViewDiscography)
        recyclerView.layoutManager = LinearLayoutManager(this)
        artistId = intent.getLongExtra("artistId", 0)
        artistName = intent.getStringExtra("artistName") ?: "Unknown Artist"
        artistImageUrl = intent.getStringExtra("artistImageUrl") ?: ""

        setupApiService()
        db = AppDatabase.getDatabase(applicationContext)

        loadArtistDiscography()
    }

    private fun setupApiService() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DeezerApiService::class.java)
    }

    private fun loadArtistDiscography() {
        apiService.getArtistReleases(artistId).enqueue(object : Callback<DeezerAlbumsResponse> {
            override fun onResponse(
                call: Call<DeezerAlbumsResponse>,
                response: Response<DeezerAlbumsResponse>
            ) {
                if (response.isSuccessful) {
                    val releases = response.body()?.data ?: emptyList()
                    if (releases.isNotEmpty()) {
                        displayDiscography(releases)
                    } else {
                        Toast.makeText(
                            this@ArtistDiscographyActivity,
                            "No releases found for $artistName.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.e(
                        "ArtistDiscographyActivity",
                        "Error fetching discography: ${response.code()} ${response.message()}"
                    )
                    Toast.makeText(
                        this@ArtistDiscographyActivity,
                        "Error fetching discography.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<DeezerAlbumsResponse>, t: Throwable) {
                Log.e("ArtistDiscographyActivity", "Error fetching discography", t)
                Toast.makeText(
                    this@ArtistDiscographyActivity,
                    "Error fetching discography.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun displayDiscography(releases: List<DeezerAlbum>) {
        val sortedReleases = releases.sortedByDescending { release ->
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(release.release_date)?.time
                ?: 0L
        }

        val adapter = DiscographyAdapter(sortedReleases) { album ->
            Toast.makeText(this, "Clicked on: ${album.title}", Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = adapter

        val imageView: ImageView = findViewById(R.id.imageViewArtist)
        Glide.with(this)
            .load(artistImageUrl)
            .apply(RequestOptions.circleCropTransform())
            .into(imageView)
    }
}