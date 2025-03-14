package com.dev.tunedetectivex

import android.content.Intent
import android.graphics.drawable.Drawable
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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
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

        val progressBar: ProgressBar = findViewById(R.id.progressBarLoading)
        val imageView: ImageView = findViewById(R.id.imageViewArtist)


        progressBar.visibility = View.VISIBLE

        Glide.with(this)
            .load(artistImageUrl)
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.error_image)
            .transform(RoundedCorners(30))
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    imageView.setImageDrawable(resource)
                    progressBar.visibility = View.GONE
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    progressBar.visibility = View.GONE
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Log.e("Glide", "Image load failed: $artistImageUrl")
                    progressBar.visibility = View.GONE
                    super.onLoadFailed(errorDrawable)
                }
            })


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