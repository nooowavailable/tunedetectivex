package com.dev.tunedetectivex

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReleaseDetailsActivity : AppCompatActivity() {

    private lateinit var albumCover: ImageView
    private lateinit var releaseTitle: TextView
    private lateinit var artistName: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var apiService: DeezerApiService
    private lateinit var progressBar: ProgressBar
    private var isNetworkRequestsAllowed = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_release_details)

        albumCover = findViewById(R.id.imageViewAlbumCover)
        releaseTitle = findViewById(R.id.textViewReleaseTitle)
        artistName = findViewById(R.id.textViewArtistName)
        recyclerView = findViewById(R.id.recyclerViewTracks)
        progressBar = findViewById(R.id.progressBarLoading)
        trackAdapter = TrackAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = trackAdapter

        apiService = DeezerApiService.create()

        val releaseId = intent.getLongExtra("releaseId", -1)
        val releaseTitleText = intent.getStringExtra("releaseTitle")
        val artistNameText = intent.getStringExtra("artistName")
        val albumArtUrl = intent.getStringExtra("albumArtUrl")

        val albumId = intent.getLongExtra("releaseId", -1)
        if (albumId != -1L) {
            fetchReleaseDetails(albumId)
        } else {
            Log.e("ReleaseDetailsActivity", "Invalid Album ID received")
        }

        Log.d("ReleaseDetailsActivity", "Release ID: $releaseId")
        Log.d("ReleaseDetailsActivity", "Album Cover URL: $albumArtUrl")

        releaseTitle.text = releaseTitleText ?: "Unknown Title"
        artistName.text = artistNameText ?: "Unknown Artist"

        if (!albumArtUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(albumArtUrl)
                .apply(RequestOptions.placeholderOf(R.drawable.placeholder_image))
                .error(R.drawable.error_image)
                .into(albumCover)
        } else {
            Log.e("ReleaseDetailsActivity", "Album Cover URL is null or empty")
            albumCover.setImageResource(R.drawable.error_image)
        }

        if (releaseId != -1L) {
            showLoading(true)
            loadTracklist(releaseId)
        } else {
            Log.e("ReleaseDetailsActivity", "Invalid Release ID")
        }

        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val isFirstRunReleaseDetails = sharedPreferences.getBoolean("isFirstRunReleaseDetails", true)
        if (isFirstRunReleaseDetails) {
            showReleaseDetailsTutorial()
            sharedPreferences.edit { putBoolean("isFirstRunReleaseDetails", false) }
        }
    }

    private fun checkNetworkTypeAndSetFlag() {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any")

        isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkType!!)

        if (!isNetworkRequestsAllowed) {
            Log.w(
                "ReleaseDetailsActivity",
                "Selected network type is not available. Skipping network requests."
            )
            Toast.makeText(
                this,
                "Selected network type is not available. Please check your connection.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showReleaseDetailsTutorial() {
        TapTargetSequence(this)
            .targets(
                TapTarget.forView(
                    albumCover,
                    "Album-Cover",
                    "Here you can see the album or single cover."
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    releaseTitle,
                    "Title",
                    "The title of the release is displayed here."
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    artistName,
                    "Artists",
                    "Here you can see the artist's name."
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    recyclerView,
                    "Tracklist",
                    "Here you can find the tracks of the release. Scroll to view all tracks."
                ).transparentTarget(true).cancelable(false)
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    Toast.makeText(
                        this@ReleaseDetailsActivity,
                        "Tutorial completed! Have fun with the app.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onSequenceStep(tapTarget: TapTarget, targetClicked: Boolean) {
                }

                override fun onSequenceCanceled(tapTarget: TapTarget?) {
                    Toast.makeText(
                        this@ReleaseDetailsActivity,
                        "Tutorial aborted.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
            .start()
    }

    private fun fetchReleaseDetails(albumId: Long) {
        checkNetworkTypeAndSetFlag()

        if (!isNetworkRequestsAllowed) {
            return
        }

        showLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.getAlbumDetails(albumId).execute()
                if (response.isSuccessful) {
                    val albumDetails = response.body()
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        if (albumDetails != null) {
                            Log.d(
                                "ReleaseDetailsActivity",
                                "Album Details: ID=${albumDetails.id}, Title=${albumDetails.title}, Artist=${albumDetails.artist.name}"
                            )
                            updateUI(albumDetails)
                        } else {
                            Log.e("ReleaseDetailsActivity", "Album details are null")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Log.e(
                            "ReleaseDetailsActivity",
                            "Failed to fetch album details: ${response.message()}"
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Log.e("ReleaseDetailsActivity", "Error fetching album details: ${e.message}", e)
                }
            }
        }
    }


    private fun updateUI(album: DeezerAlbum) {
        releaseTitle.text = album.title
        artistName.text = album.artist.name

        Log.d("ReleaseDetailsActivity", "Artist Name: ${album.artist.name}")

        Glide.with(this)
            .load(album.getBestCoverUrl())
            .apply(
                RequestOptions.bitmapTransform(RoundedCorners(30))
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.error_image)
            )
            .into(albumCover)
        Log.d("ReleaseDetailsActivity", "Loaded Album Cover URL: ${album.getBestCoverUrl()}")
    }

    private fun loadTracklist(releaseId: Long) {
        checkNetworkTypeAndSetFlag()

        if (!isNetworkRequestsAllowed) {
            return
        }

        showLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.getTracklist(releaseId).execute()
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    if (response.isSuccessful) {
                        val tracks = response.body()?.data ?: emptyList()
                        trackAdapter.submitList(tracks)
                    } else {
                        Log.e(
                            "ReleaseDetailsActivity",
                            "Failed to load tracklist: ${response.message()}"
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Log.e("ReleaseDetailsActivity", "Error loading tracklist: ${e.message}", e)
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}