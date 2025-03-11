package com.dev.tunedetectivex

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private lateinit var progressBar: ProgressBar
    private var isNetworkRequestsAllowed = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artist_discography)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerViewDiscography)
        recyclerView.layoutManager = LinearLayoutManager(this)

        artistId = intent.getLongExtra("artistId", 0)
        artistName = intent.getStringExtra("artistName") ?: "Unknown Artist"
        progressBar = findViewById(R.id.progressBarLoading)
        supportActionBar?.title = artistName

        checkNetworkTypeAndSetFlag()
        setupApiService()
        db = AppDatabase.getDatabase(applicationContext)

        fetchArtistDetails(artistId)
    }

    private fun checkNetworkTypeAndSetFlag() {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any") ?: "Any"
        isNetworkRequestsAllowed = WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkType)
    }

    private fun fetchArtistDetails(artistId: Long) {
        showLoading(true)

        apiService.getArtistDetails(artistId).enqueue(object : Callback<DeezerArtist> {
            override fun onResponse(call: Call<DeezerArtist>, response: Response<DeezerArtist>) {
                showLoading(false)

                if (response.isSuccessful) {
                    val artist = response.body()
                    artist?.let {
                        artistImageUrl = it.getBestPictureUrl()
                        loadArtistDiscography()
                    } ?: run {
                        Toast.makeText(
                            this@ArtistDiscographyActivity,
                            "Artist not found.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.e(
                        "ArtistDiscographyActivity",
                        "Error fetching artist details: ${response.code()} ${response.message()}"
                    )
                    Toast.makeText(
                        this@ArtistDiscographyActivity,
                        "Error fetching artist details.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<DeezerArtist>, t: Throwable) {
                showLoading(false)
                Log.e("ArtistDiscographyActivity", "Error fetching artist details", t)
                Toast.makeText(
                    this@ArtistDiscographyActivity,
                    "Error fetching artist details.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun setupApiService() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DeezerApiService::class.java)
    }

    private fun loadArtistDiscography() {
        showLoading(true)

        apiService.getArtistReleases(artistId).enqueue(object : Callback<DeezerAlbumsResponse> {
            override fun onResponse(
                call: Call<DeezerAlbumsResponse>,
                response: Response<DeezerAlbumsResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    val releases = response.body()?.data ?: emptyList()
                    Log.d("ArtistDiscographyActivity", "Releases: $releases")
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
                showLoading(false)

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
            Intent(this, ReleaseDetailsActivity::class.java).apply {
                putExtra("releaseId", album.id)
                putExtra("releaseTitle", album.title)
                putExtra("artistName", artistName)
                putExtra("albumArtUrl", album.getBestCoverUrl())
            }.also { startActivity(it) }
        }

        recyclerView.adapter = adapter
        val imageView: ImageView = findViewById(R.id.imageViewArtist)

        val placeholderResId = R.drawable.placeholder_image

        BitmapUtils.loadBitmapFromUrl(
            this,
            artistImageUrl,
            imageView,
            cornerRadius = 30f,
            isCircular = false,
            placeholderResId = placeholderResId
        )

        recyclerView.visibility = View.VISIBLE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}