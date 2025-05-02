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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
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

        val itunesId = intent.getLongExtra("itunesId", -1L)
        val rawTitle =
            intent.getStringExtra("releaseTitle") ?: getString(R.string.unknown_title_fallback)
        val artistNameText =
            intent.getStringExtra("artistName") ?: getString(R.string.unknown_artist_fallback)
        val albumArtUrl = intent.getStringExtra("albumArtUrl") ?: ""
        val releaseId = intent.getLongExtra("releaseId", -1L)
        val releaseType = extractReleaseType(rawTitle)

        val apiSource: String? = when {
            itunesId > 0L && isItunesSupportEnabled() -> "iTunes"
            releaseId > 0L -> "Deezer"
            else -> null
        }

        when (apiSource) {
            "Deezer" -> fetchReleaseDetails(releaseId)
            "iTunes" -> fetchITunesReleaseDetails(releaseId)
            else -> {
                Toast.makeText(this, "No valid API source available.", Toast.LENGTH_SHORT)
                    .show()
                finish()
                return
            }
        }

        loadInitialAlbumArt(albumArtUrl)

        val cleanedTitle = rawTitle
            .replace(" - Single", "")
            .replace(" - EP", "")
            .replace(" - Album", "")
            .trim()

        val primaryColor = TypedValue().let {
            theme.resolveAttribute(android.R.attr.textColorPrimary, it, true)
            ContextCompat.getColor(this, it.resourceId)
        }
        val secondaryColor = TypedValue().let {
            theme.resolveAttribute(android.R.attr.textColorSecondary, it, true)
            ContextCompat.getColor(this, it.resourceId)
        }

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

        artistName.text = artistNameText


        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val isFirstRunReleaseDetails = sharedPreferences.getBoolean("isFirstRunReleaseDetails", true)
        if (isFirstRunReleaseDetails) {
            showReleaseDetailsTutorial()
            sharedPreferences.edit { putBoolean("isFirstRunReleaseDetails", false) }
        }

        if (releaseId == -1L && (releaseId > 0L || (itunesId > 0L && isItunesSupportEnabled()))) {
            loadTracklist()
        }
    }

    private fun isItunesSupportEnabled(): Boolean {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        return prefs.getBoolean("itunesSupportEnabled", false)
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

    private fun extractReleaseType(title: String): String? {
        return when {
            title.contains(" - Single", ignoreCase = true) -> "Single"
            title.contains(" - EP", ignoreCase = true) -> "EP"
            title.contains(" - Album", ignoreCase = true) -> "Album"
            else -> null
        }
    }

    private fun loadInitialAlbumArt(url: String) {
        Glide.with(this).clear(albumCover)
        albumCover.setImageDrawable(null)

        if (url.isNotBlank()) {
            progressBar.visibility = View.VISIBLE

            Glide.with(this)
                .load(url.toSafeArtwork())
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
                .error(R.drawable.error_image)
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
                        progressBar.visibility = View.GONE
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        albumCover.setImageResource(R.drawable.error_image)
                        progressBar.visibility = View.GONE
                    }
                })
        } else {
            albumCover.setImageResource(R.drawable.error_image)
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
                            showLoading(false)
                            if (albumDetails != null) {
                                Log.d(
                                    "ReleaseDetailsActivity",
                                    "Album Details: ID=${albumDetails.id}, Title=${albumDetails.title}, Artist=${albumDetails.artist?.name}"
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
                        if (response.isSuccessful) {
                            val results = response.body()?.results.orEmpty()

                            val collection = results.firstOrNull {
                                it.wrapperType == "collection" && !it.collectionName.isNullOrBlank()
                            }

                            if (collection == null) {
                                Toast.makeText(
                                    this@ReleaseDetailsActivity,
                                    getString(R.string.release_details_not_found),
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                                return@withContext
                            }

                            val rawTitle = collection.collectionName
                                ?: getString(R.string.unknown_title_fallback)
                            val releaseType = extractReleaseType(rawTitle)
                            val cleanedTitle = rawTitle
                                .replace(" - Single", "", ignoreCase = true)
                                .replace(" - EP", "", ignoreCase = true)
                                .replace(" - Album", "", ignoreCase = true)
                                .trim()

                            val primaryColor = ContextCompat.getColor(
                                this@ReleaseDetailsActivity,
                                TypedValue().let {
                                    theme.resolveAttribute(
                                        android.R.attr.textColorPrimary,
                                        it,
                                        true
                                    )
                                    it.resourceId
                                })
                            val secondaryColor = ContextCompat.getColor(
                                this@ReleaseDetailsActivity,
                                TypedValue().let {
                                    theme.resolveAttribute(
                                        android.R.attr.textColorSecondary,
                                        it,
                                        true
                                    )
                                    it.resourceId
                                })

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

                            artistName.text =
                                collection.artistName ?: getString(R.string.unknown_artist_fallback)

                            val coverUrl = collection.artworkUrl100?.toHighResArtwork()
                            if (!coverUrl.isNullOrBlank()) {
                                Glide.with(this@ReleaseDetailsActivity)
                                    .load(coverUrl)
                                    .placeholder(R.drawable.ic_discography)
                                    .error(R.drawable.error_image)
                                    .transform(RoundedCorners(30))
                                    .into(albumCover)
                            }

                            val tracks = results.filter {
                                it.wrapperType == "track" && it.kind == "song"
                            }.map {
                                Track(
                                    title = it.trackName
                                        ?: getString(R.string.unknown_title_fallback),
                                    duration = ((it.trackTimeMillis ?: 0) / 1000).toInt()
                                )
                            }

                            trackAdapter.submitList(tracks)

                        } else {
                            Log.e("ReleaseDetailsActivity", "iTunes: Faulty response")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Log.e("ReleaseDetailsActivity", "iTunes: Error during loading details", e)
                    }
                }
            }
        }
    }


    private fun updateUI(album: DeezerAlbum) {
        releaseTitle.text = album.title
        artistName.text = album.artist?.name
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

        loadTracklist()
    }

    private fun loadTracklist() {
        val deezerId = intent.getLongExtra("deezerId", -1L)
        val itunesId = intent.getLongExtra("itunesId", -1L)
        val isItunesEnabled = isItunesSupportEnabled()

        when {
            deezerId > 0L && (!isItunesEnabled || itunesId <= 0L) -> {
                loadTracklistFromDeezer(deezerId)
            }

            deezerId > 0L && itunesId > 0L && isItunesEnabled -> {
                loadCombinedTracklists(deezerId, itunesId)
            }

            deezerId <= 0L && itunesId > 0L && isItunesEnabled -> {
                loadTracklistFromITunes(itunesId)
            }

            else -> {
                Log.w("ReleaseDetailsActivity", "No valid ID for tracklist")
                Toast.makeText(
                    this,
                    getString(R.string.no_tracklist_id_available),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private fun loadCombinedTracklists(deezerId: Long, itunesId: Long) {
        showLoading(true)
        val retrofit = Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val iTunesService = retrofit.create(ITunesApiService::class.java)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val deezerResponse = apiService.getTracklist(deezerId).execute()
                val itunesResponse = iTunesService.lookupAlbumWithTracks(itunesId).execute()

                val deezerTracks = deezerResponse.body()?.data ?: emptyList()
                val itunesTracks = itunesResponse.body()?.results
                    ?.filter { it.wrapperType == "track" }
                    ?.map {
                        Track(
                            title = it.trackName
                                ?: this@ReleaseDetailsActivity.getString(R.string.unknown_title),
                            duration = ((it.trackTimeMillis ?: 0) / 1000).toInt()
                        )
                    } ?: emptyList()

                val combined = (deezerTracks + itunesTracks)
                    .distinctBy { it.title.trim().lowercase() }

                withContext(Dispatchers.Main) {
                    showLoading(false)
                    trackAdapter.submitList(combined)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Log.e("ReleaseDetailsActivity", "Error when combining tracklists", e)
                }
            }
        }
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


    private fun loadTracklistFromITunes(releaseId: Long) {
        showLoading(true)

        val retrofit = Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val iTunesService = retrofit.create(ITunesApiService::class.java)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = iTunesService.lookupAlbumWithTracks(releaseId).execute()
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    if (response.isSuccessful) {
                        val tracks = response.body()?.results
                            ?.filter { it.wrapperType == "track" && it.kind == "song" }
                            ?.map {
                                Track(
                                    title = it.trackName
                                        ?: this@ReleaseDetailsActivity.getString(R.string.unknown_title),
                                    duration = ((it.trackTimeMillis ?: 0) / 1000).toInt()
                                )
                            } ?: emptyList()

                        trackAdapter.submitList(tracks)
                    } else {
                        Log.e(
                            "ReleaseDetailsActivity",
                            "iTunes: Failed to load tracks: ${response.message()}"
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Log.e("ReleaseDetailsActivity", "iTunes: Error loading tracks", e)
                }
            }
        }
    }


    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    fun String.toHighResArtwork(): String {
        return this.replace("100x100bb", "1200x1200bb")
    }
}