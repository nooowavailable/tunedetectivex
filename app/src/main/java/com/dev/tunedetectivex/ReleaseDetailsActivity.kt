package com.dev.tunedetectivex

import android.graphics.drawable.Drawable
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
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
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

        checkNetworkTypeAndSetFlag()


        val releaseId = intent.getLongExtra("releaseId", -1)
        val releaseTitleText = intent.getStringExtra("releaseTitle") ?: "Unknown Title"
        val artistNameText = intent.getStringExtra("artistName") ?: "Unknown Artist"
        val albumArtUrl = intent.getStringExtra("albumArtUrl") ?: ""

        val albumId = intent.getLongExtra("releaseId", -1)
        if (albumId != -1L) {
            fetchReleaseDetails(albumId)
        } else {
            Log.e("ReleaseDetailsActivity", "Invalid Album ID received")
        }

        Log.d("ReleaseDetailsActivity", "Release ID: $releaseId")
        Log.d("ReleaseDetailsActivity", "Album Cover URL: $albumArtUrl")

        releaseTitle.text = releaseTitleText
        artistName.text = artistNameText

        if (albumArtUrl.isNotEmpty()) {
            progressBar.visibility = View.VISIBLE

            Glide.with(this)
                .load(albumArtUrl)
                .placeholder(R.drawable.ic_discography)
                .error(R.drawable.ic_discography)
                .transform(RoundedCorners(20))
                .into(object : CustomTarget<Drawable>() {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        albumCover.setImageDrawable(resource)

                        progressBar.visibility = View.GONE
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                    }
                })
        } else {
            Log.e("ReleaseDetailsActivity", "Album Cover URL is null or empty")
            albumCover.setImageResource(R.drawable.error_image)
            progressBar.visibility = View.GONE
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

    private fun checkNetworkAndProceed(action: () -> Unit) {
        checkNetworkTypeAndSetFlag()
        if (isNetworkRequestsAllowed) {
            action()
        } else {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun checkNetworkTypeAndSetFlag() {
        val sharedPreferences =
            applicationContext.getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any") ?: "Any"
        isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkType)
    }

    private fun showReleaseDetailsTutorial() {
        TapTargetSequence(this)
            .targets(
                TapTarget.forView(
                    albumCover,
                    getString(R.string.tutorial_album_cover_title),
                    getString(R.string.tutorial_album_cover_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    releaseTitle,
                    getString(R.string.tutorial_release_title),
                    getString(R.string.tutorial_release_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    artistName,
                    getString(R.string.tutorial_artist_name_title),
                    getString(R.string.tutorial_artist_name_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    recyclerView,
                    getString(R.string.tutorial_tracklist_title),
                    getString(R.string.tutorial_tracklist_description)
                ).transparentTarget(true).cancelable(false)
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    Toast.makeText(
                        this@ReleaseDetailsActivity,
                        getString(R.string.tutorial_completed),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onSequenceStep(tapTarget: TapTarget, targetClicked: Boolean) {
                }

                override fun onSequenceCanceled(tapTarget: TapTarget?) {
                    Toast.makeText(
                        this@ReleaseDetailsActivity,
                        getString(R.string.tutorial_aborted),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
            .start()
    }

    private fun fetchReleaseDetails(albumId: Long) {
        checkNetworkAndProceed {
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
                        Log.e(
                            "ReleaseDetailsActivity",
                            "Error fetching album details: ${e.message}",
                            e
                        )
                    }
                }
            }
        }
    }


    private fun updateUI(album: DeezerAlbum) {
        releaseTitle.text = album.title
        artistName.text = album.artist.name

        Log.d("ReleaseDetailsActivity", "Artist Name: ${album.artist.name}")

        val coverUrl = album.getBestCoverUrl()

        if (coverUrl.isNotEmpty()) {
            Glide.with(this)
                .load(coverUrl)
                .placeholder(R.drawable.ic_discography)
                .error(R.drawable.ic_discography)
                .transform(RoundedCorners(30))
                .into(albumCover)
        } else {
            albumCover.setImageResource(R.drawable.error_image)
        }
    }

    private fun loadTracklist(releaseId: Long) {
        checkNetworkAndProceed {
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
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}