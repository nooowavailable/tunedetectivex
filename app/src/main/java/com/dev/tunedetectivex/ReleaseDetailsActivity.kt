package com.dev.tunedetectivex

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.dev.tunedetectivex.api.ITunesApiService
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ReleaseDetailsActivity : AppCompatActivity() {

    private lateinit var albumCover: ImageView
    private lateinit var releaseTitle: TextView
    private lateinit var artistName: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var apiService: DeezerApiService
    private lateinit var progressBar: ProgressBar
    private var isNetworkRequestsAllowed = true

    private var albumArtLoadedFromNetwork = false

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

        val genericReleaseId = intent.getLongExtra("releaseId", -1L)
        val deezerId = intent.getLongExtra("deezerId", -1L)
        val itunesId = intent.getLongExtra("itunesId", -1L)
        val apiSource = intent.getStringExtra("apiSource")

        intent.getStringExtra("releaseTitle") ?: getString(R.string.unknown_title_fallback)
        intent.getStringExtra("artistName") ?: getString(R.string.unknown_artist_fallback)
        val albumArtUrl = intent.getStringExtra("albumArtUrl") ?: ""

        Log.d("ReleaseDetailsActivity", "Received IDs and Source: " +
                "Generic Release ID = $genericReleaseId, " +
                "Deezer ID = $deezerId, " +
                "iTunes ID = $itunesId, " +
                "API Source = $apiSource")

        loadImageWithGlide(albumArtUrl, albumCover, isInitialLoad = true)

        when (apiSource) {
            "Deezer" -> {
                if (deezerId > 0L) {
                    fetchReleaseDetails(deezerId)
                } else {
                    Log.e("ReleaseDetailsActivity", "Invalid Deezer album ID received ($deezerId).")
                    Toast.makeText(this, "Error: Invalid Deezer album ID.", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
            }
            "iTunes" -> {
                if (isItunesSupportEnabled() && itunesId > 0L) {
                    fetchITunesReleaseDetails(itunesId)
                } else {
                    Log.e("ReleaseDetailsActivity", "iTunes support disabled or invalid iTunes collection ID received ($itunesId).")
                    Toast.makeText(this, "Error: iTunes support disabled or invalid iTunes ID.", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
            }
            else -> {
                Toast.makeText(this, "No valid API source available for this release.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val isFirstRunReleaseDetails = sharedPreferences.getBoolean("isFirstRunReleaseDetails", true)
        if (isFirstRunReleaseDetails) {
            showReleaseDetailsTutorial()
            sharedPreferences.edit { putBoolean("isFirstRunReleaseDetails", false) }
        }
    }
    private fun isItunesSupportEnabled(): Boolean {
        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return prefs.getBoolean("itunesSupportEnabled", false)
    }

    private fun getThemeColor(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        val theme = theme
        theme.resolveAttribute(attrRes, typedValue, true)
        return ContextCompat.getColor(this, typedValue.resourceId)
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
        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(applicationContext)
        isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkPreference)
    }

    private fun extractReleaseType(title: String): String? {
        return when {
            title.contains(" - Single", ignoreCase = true) -> "Single"
            title.contains(" - EP", ignoreCase = true) -> "EP"
            title.contains(" - Album", ignoreCase = true) -> "Album"
            else -> null
        }
    }

    private fun loadImageWithGlide(url: String, imageView: ImageView, isInitialLoad: Boolean = false) {
        if (isInitialLoad) {
            Glide.with(this).clear(imageView)
            imageView.setImageDrawable(null)
        }

        if (url.isNotBlank()) {
            if (!albumArtLoadedFromNetwork || isInitialLoad) {
                progressBar.visibility = View.VISIBLE
            }

            val requestBuilder = Glide.with(this)
                .load(url.toSafeArtwork())
                .error(R.drawable.error_image)
                .transform(RoundedCorners(30))
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        progressBar.visibility = View.GONE
                        imageView.setImageResource(R.drawable.error_image)
                        Log.e("Glide", "Image load failed: $url", e)
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        progressBar.visibility = View.GONE
                        if (dataSource != DataSource.MEMORY_CACHE && dataSource != DataSource.RESOURCE_DISK_CACHE) {
                            albumArtLoadedFromNetwork = true
                        }
                        return false
                    }
                })

            requestBuilder.into(imageView)
        } else {
            imageView.setImageResource(R.drawable.error_image)
            progressBar.visibility = View.GONE
        }
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
                            if (albumDetails != null) {
                                Log.d(
                                    "ReleaseDetailsActivity",
                                    "Album Details: ID=${albumDetails.id}, Title=${albumDetails.title}, Artist=${albumDetails.artist?.name}"
                                )
                                updateUI(albumDetails)
                                loadTracklistFromDeezer(albumDetails.id)
                            } else {
                                Log.e("ReleaseDetailsActivity", "Album details are null")
                                Toast.makeText(this@ReleaseDetailsActivity, getString(R.string.release_details_not_found), Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showLoading(false)
                            Log.e(
                                "ReleaseDetailsActivity",
                                "Failed to fetch album details: ${response.message()}"
                            )
                            Toast.makeText(this@ReleaseDetailsActivity, "Error fetching Deezer release details.", Toast.LENGTH_SHORT).show()
                            finish()
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
                        Toast.makeText(this@ReleaseDetailsActivity, "Network error fetching Deezer details.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
    }

    private fun fetchITunesReleaseDetails(albumId: Long) {
        checkNetworkAndProceed {
            showLoading(true)

            val retrofit = Retrofit.Builder()
                .baseUrl("https://itunes.apple.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val iTunesService = retrofit.create(ITunesApiService::class.java)

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val response = iTunesService.lookupAlbumWithTracks(albumId).execute()
                    withContext(Dispatchers.Main) {
                        showLoading(false)

                        Log.d(
                            "ReleaseDetailsActivity",
                            "iTunes API Response: isSuccessful = ${response.isSuccessful}"
                        )
                        if (!response.isSuccessful) {
                            Log.e(
                                "ReleaseDetailsActivity",
                                "iTunes API Error Code: ${response.code()}, Message: ${response.message()}"
                            )
                            Toast.makeText(
                                this@ReleaseDetailsActivity,
                                "Error fetching iTunes release details: ${response.message()}",
                                Toast.LENGTH_LONG
                            ).show()
                            finish()
                            return@withContext
                        }

                        val responseBody = response.body()
                        if (responseBody == null) {
                            Log.e("ReleaseDetailsActivity", "iTunes API Response Body is NULL.")
                            Toast.makeText(
                                this@ReleaseDetailsActivity,
                                "iTunes API returned empty data.",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                            return@withContext
                        }

                        val results = responseBody.results
                        Log.d("ReleaseDetailsActivity", "iTunes API Results count: ${results.size}")
                        results.forEachIndexed { index, item ->
                            Log.d(
                                "ReleaseDetailsActivity",
                                "Result $index: wrapperType=${item.wrapperType}, collectionName=${item.collectionName}, trackName=${item.trackName}"
                            )
                        }


                        val primaryCollection = results.firstOrNull {
                            it.wrapperType == "collection" && !it.collectionName.isNullOrBlank()
                        }

                        val firstTrackWithDetails = results.firstOrNull {
                            it.wrapperType == "track" && it.kind == "song" && !it.collectionName.isNullOrBlank()
                        }

                        val displayTitle: String
                        val displayArtist: String
                        val displayArtworkUrl: String?

                        if (primaryCollection != null) {
                            Log.d("ReleaseDetailsActivity", "Found primary collection object.")
                            displayTitle = primaryCollection.collectionName
                                ?: getString(R.string.unknown_title_fallback)
                            displayArtist = primaryCollection.artistName
                                ?: getString(R.string.unknown_artist_fallback)
                            displayArtworkUrl = primaryCollection.artworkUrl100?.toHighResArtwork()
                        } else if (firstTrackWithDetails != null) {
                            Log.d(
                                "ReleaseDetailsActivity",
                                "No primary collection, inferring from first track."
                            )
                            displayTitle = firstTrackWithDetails.collectionName
                                ?: getString(R.string.unknown_title_fallback)
                            displayArtist = firstTrackWithDetails.artistName
                                ?: getString(R.string.unknown_artist_fallback)
                            displayArtworkUrl =
                                firstTrackWithDetails.artworkUrl100?.toHighResArtwork()
                        } else {
                            Log.e(
                                "ReleaseDetailsActivity",
                                "No collection or relevant track data found in iTunes API response for ID: $albumId"
                            )
                            Toast.makeText(
                                this@ReleaseDetailsActivity,
                                getString(R.string.release_details_not_found),
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                            return@withContext
                        }

                        val releaseType = extractReleaseType(displayTitle)
                        val cleanedTitle = displayTitle
                            .replace(" - Single", "", ignoreCase = true)
                            .replace(" - EP", "", ignoreCase = true)
                            .replace(" - Album", "", ignoreCase = true)
                            .trim()

                        val primaryColor = getThemeColor(android.R.attr.textColorPrimary)
                        val secondaryColor = getThemeColor(android.R.attr.textColorSecondary)

                        if (!releaseType.isNullOrEmpty()) {
                            val combined = "$cleanedTitle ($releaseType)"
                            val spannable = SpannableString(combined)
                            val start = combined.indexOf("($releaseType)")
                            spannable.setSpan(
                                ForegroundColorSpan(primaryColor),
                                0,
                                start,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            spannable.setSpan(
                                ForegroundColorSpan(secondaryColor),
                                start,
                                combined.length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            releaseTitle.text = spannable
                        } else {
                            releaseTitle.text = cleanedTitle
                            releaseTitle.setTextColor(primaryColor)
                        }

                        artistName.text = displayArtist

                        displayArtworkUrl?.let { coverUrl ->
                            loadImageWithGlide(coverUrl, albumCover)
                        }


                        val tracks =
                            results.filter { it.wrapperType == "track" && it.kind == "song" }
                                .map {
                                    Track(
                                        title = it.trackName
                                            ?: getString(R.string.unknown_title_fallback),
                                        duration = ((it.trackTimeMillis ?: 0) / 1000).toInt()
                                    )
                                }

                        if (tracks.isEmpty() && displayTitle.isNotBlank()) {
                            Log.w(
                                "ReleaseDetailsActivity",
                                "No individual track items found. Displaying inferred title as a single track."
                            )
                            trackAdapter.submitList(
                                listOf(
                                    Track(
                                        title = displayTitle,
                                        duration = 0
                                    )
                                )
                            )
                        } else {
                            trackAdapter.submitList(tracks)
                        }


                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Log.e("ReleaseDetailsActivity", "iTunes: Error during loading details", e)
                        Toast.makeText(
                            this@ReleaseDetailsActivity,
                            "Network error fetching iTunes details.",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
            }
        }
    }

        private fun updateUI(album: DeezerAlbum) {
        releaseTitle.text = album.title
        artistName.text = album.artist?.name
        val coverUrl = album.getBestCoverUrl()

        loadImageWithGlide(coverUrl, albumCover)
    }


    private fun loadTracklistFromDeezer(releaseId: Long) {
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
                                "Deezer: Error during tracklist request: ${response.message()}"
                            )
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Log.e(
                            "ReleaseDetailsActivity",
                            "Deezer: Error loading the tracklist",
                            e
                        )
                    }
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun String.toSafeArtwork(): String {
        return this.replace("100x100bb.jpg", "600x600bb.jpg")
            .replace("100x100bb", "600x600bb")
    }

    private fun String.toHighResArtwork(): String {
        return this.replace("100x100bb", "1200x1200bb")
    }
}