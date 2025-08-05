package com.dev.tunedetectivex

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var pushNotificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var recyclerViewArtists: RecyclerView
    private lateinit var editTextArtist: EditText
    private lateinit var buttonSaveArtist: ImageButton
    private lateinit var imageViewAlbumArt: ImageView
    private lateinit var textViewAlbumTitle: TextView
    private lateinit var textViewrelease_date: TextView
    private lateinit var apiService: DeezerApiService
    private lateinit var db: AppDatabase
    private var selectedArtist: DeezerArtist? = null
    private lateinit var progressBar: ProgressBar
    private var isFabMenuOpen = false
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var fabAbout: FloatingActionButton
    private lateinit var artistInfoContainer: LinearLayout
    private lateinit var recyclerViewReleases: RecyclerView
    private lateinit var fabScrollToTop: FloatingActionButton
    private lateinit var textViewNoSavedArtists: TextView
    private lateinit var imageViewArtistProfile: ImageView
    private lateinit var textViewArtistName: TextView
    private lateinit var releaseInfoPlaceholder: LinearLayout
    private lateinit var placeholderAlbumArt: View
    private var breathingAnimator: ValueAnimator? = null
    private lateinit var buttonToggleLayout: ImageButton
    private var isListLayout = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pushNotificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "Notification permission granted.")
                WorkManagerUtil.reEnqueueIfMissing(this)
            } else {
                Log.w(TAG, "Notification permission denied.")
                Toast.makeText(
                    this,
                    getString(R.string.toast_notification_permission_required),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        recyclerViewArtists = findViewById(R.id.recyclerViewArtists)
        recyclerViewArtists.layoutManager = LinearLayoutManager(this)

        buttonToggleLayout = findViewById(R.id.buttonToggleLayout)
        editTextArtist = findViewById(R.id.editTextArtist)
        buttonSaveArtist = findViewById(R.id.buttonSaveArtist)

        artistInfoContainer = findViewById(R.id.artistInfoContainer)
        textViewAlbumTitle = findViewById(R.id.textViewAlbumTitle)
        textViewrelease_date = findViewById(R.id.textViewrelease_date)
        imageViewAlbumArt = findViewById(R.id.imageViewAlbumArt)
        imageViewArtistProfile = findViewById(R.id.imageViewArtistProfile)
        textViewArtistName = findViewById(R.id.textViewArtistName)

        progressBar = findViewById(R.id.progressBarLoading)
        notificationManager = NotificationManagerCompat.from(this)

        textViewNoSavedArtists = findViewById(R.id.textViewNoSavedArtists)
        imageViewArtistProfile = findViewById(R.id.imageViewArtistProfile)
        releaseInfoPlaceholder = findViewById(R.id.releaseInfoPlaceholder)
        placeholderAlbumArt = releaseInfoPlaceholder.findViewById(R.id.placeholderAlbumArt)

        val fabMenu: FloatingActionButton = findViewById(R.id.fabMenu)
        val fabSavedArtists: FloatingActionButton = findViewById(R.id.fabSavedArtists)
        val fabSettings: FloatingActionButton = findViewById(R.id.fabSettings)
        val fabCheckStatus: FloatingActionButton = findViewById(R.id.fabCheckStatus)

        fabAbout = findViewById(R.id.fabAbout)
        fabAbout.visibility = View.GONE
        isFabMenuOpen = false

        val allFabs =
            listOf(fabSavedArtists, fabSettings, fabAbout, fabCheckStatus)
        allFabs.forEach {
            it.visibility = View.GONE
            it.translationY = 0f
        }

        fabScrollToTop = findViewById(R.id.fabScrollToTop)

        fabScrollToTop.setOnClickListener {
            recyclerViewReleases.smoothScrollToPosition(0)
            recyclerViewArtists.smoothScrollToPosition(0)
        }

        buttonToggleLayout.setOnClickListener {
            toggleLayoutManager()
        }

        val appPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        if (appPreferences.getBoolean("isFirstRun", true)) {
            showTutorial()
            appPreferences.edit { putBoolean("isFirstRun", false) }
        }

        requestNotificationPermission()

        updateSaveButton()
        clearPreviousSearch()


        val searchLayout: TextInputLayout = findViewById(R.id.searchLayout)
        searchLayout.startIconContentDescription = null

        searchLayout.setStartIconOnClickListener {
        }

        searchLayout.setEndIconOnClickListener {
            showSearchHistory()
        }

        setupApiService()
        setupBackGesture()

        fabMenu.setOnClickListener { view ->
            val popupMenu = PopupMenu(this, view)
            popupMenu.menuInflater.inflate(R.menu.fab_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_saved_artists -> {
                        startActivity(Intent(this, SavedArtistsActivity::class.java))
                        true
                    }

                    R.id.menu_settings -> {
                        startActivity(Intent(this, SettingsActivity::class.java))
                        true
                    }

                    R.id.menu_about -> {
                        startActivity(Intent(this, AboutActivity::class.java))
                        true
                    }

                    else -> false
                }
            }
            popupMenu.show()
        }

        fabMenu.setOnClickListener {
            toggleFabMenu()
        }


        fabSavedArtists.setOnClickListener {
            startActivity(Intent(this, SavedArtistsActivity::class.java))
            toggleFabMenu()
        }

        fabSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            toggleFabMenu()
        }

        fabAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
            toggleFabMenu()
        }

        fabCheckStatus.setOnClickListener {
            if (isItunesSupportEnabled()) {
                checkItunesStatus()
            }
            checkDeezerStatus()
        }

        db = AppDatabase.getDatabase(applicationContext)
        editTextArtist.apply {
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine()

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    triggerArtistSearch()
                    true
                } else false
            }

            setOnFocusChangeListener { view, hasFocus ->
                if (!hasFocus) {
                    triggerArtistSearch()
                } else {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }

        buttonSaveArtist.setOnClickListener {
            saveArtist()
        }

        imageViewArtistProfile.setOnClickListener {
            selectedArtist?.let { artist ->
                openArtistDiscography(artist)
            } ?: Toast.makeText(this, R.string.Noartistselected, Toast.LENGTH_SHORT).show()
        }

        textViewArtistName.setOnClickListener {
            selectedArtist?.let { artist ->
                openArtistDiscography(artist)
            } ?: Toast.makeText(this, R.string.Noartistselected, Toast.LENGTH_SHORT).show()
        }

        recyclerViewReleases = findViewById(R.id.recyclerViewReleases)
        recyclerViewReleases.layoutManager = LinearLayoutManager(this)

        if (isAutoLoadReleasesEnabled()) {
            progressBar.visibility = View.VISIBLE
            loadSavedReleases()
        } else {
            progressBar.visibility = View.GONE
            recyclerViewReleases.visibility = View.GONE
        }

        recyclerViewReleases.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                fabScrollToTop.visibility =
                    if ((recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() > 3) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
            }
        })

        recyclerViewArtists.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                fabScrollToTop.visibility =
                    if ((recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() > 3) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
            }
        })

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            pushNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

    }

    private fun toggleLayoutManager() {
        isListLayout = !isListLayout

        if (isListLayout) {
            recyclerViewArtists.layoutManager = LinearLayoutManager(this)
            recyclerViewReleases.layoutManager = LinearLayoutManager(this)
            buttonToggleLayout.setImageResource(R.drawable.ic_grid_layout)
        } else {
            recyclerViewArtists.layoutManager = GridLayoutManager(this, 2)
            recyclerViewReleases.layoutManager = GridLayoutManager(this, 2)
            buttonToggleLayout.setImageResource(R.drawable.ic_list_layout)
        }

        val currentArtists = (recyclerViewArtists.adapter as? SimilarArtistsAdapter)?.getCurrentList() ?: emptyList()

        val artistAdapter = SimilarArtistsAdapter(this, currentArtists, { artist ->
            selectedArtist = artist
            recyclerViewArtists.visibility = View.GONE
            buttonSaveArtist.visibility = View.GONE

            saveSearchHistory(artist)
            showLoading(true)

            lifecycleScope.launch(Dispatchers.IO) {
                val deezerId = artist.id
                val deezerReleases = apiService.getArtistReleases(deezerId, 0)
                    .execute().body()?.data.orEmpty()
                val deezerTitles = deezerReleases.map { normalizeTitle(it.title) }.toSet()

                val deezerLatest = deezerReleases.maxByOrNull {
                    SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.getDefault()
                    ).parse(it.release_date)?.time ?: 0L
                }?.let {
                    UnifiedAlbum(
                        id = it.id.toString(),
                        title = it.title,
                        releaseDate = it.release_date,
                        coverUrl = it.getBestCoverUrl(),
                        artistName = artist.name,
                        releaseType = it.record_type?.replaceFirstChar { c -> c.uppercaseChar() },
                        deezerId = it.id
                    )
                }

                var iTunesLatest: UnifiedAlbum? = null

                if (isItunesSupportEnabled()) {
                    val iTunesService = Retrofit.Builder()
                        .baseUrl("https://itunes.apple.com/")
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(ITunesApiService::class.java)

                    val itunesMatch =
                        iTunesService.searchArtist(term = artist.name, entity = "musicArtist")
                            .execute()
                            .body()?.results?.firstOrNull { it.artistName == artist.name }

                    val itunesArtistId = itunesMatch?.artistId
                    artist.itunesId = itunesArtistId

                    val iTunesAlbums = itunesArtistId?.let {
                        iTunesService.lookupArtistWithAlbums(it).execute()
                            .body()?.results.orEmpty()
                    } ?: emptyList()

                    val iTunesTitles = iTunesAlbums.mapNotNull {
                        it.collectionName?.let { name ->
                            normalizeTitle(name)
                        }
                    }.toSet()

                    val common = deezerTitles.intersect(iTunesTitles)
                    val isMatch = common.size >= 3

                    iTunesLatest = if (isMatch) {
                        iTunesAlbums.maxByOrNull {
                            it.releaseDate?.let { d ->
                                SimpleDateFormat(
                                    "yyyy-MM-dd",
                                    Locale.getDefault()
                                ).parse(d)?.time
                            } ?: 0L
                        }?.let {
                            UnifiedAlbum(
                                id = it.collectionId.toString(),
                                title = it.collectionName
                                    ?.replace(
                                        Regex(
                                            "\\s*-\\s*(Single|EP|Album)",
                                            RegexOption.IGNORE_CASE
                                        ), ""
                                    )
                                    ?.trim() ?: "Unknown Album",
                                releaseDate = it.releaseDate ?: "Unknown Date",
                                coverUrl = it.artworkUrl100?.replace("100x100bb", "1200x1200bb")
                                    ?: "",
                                artistName = it.artistName ?: artist.name,
                                releaseType = extractReleaseTypeFromTitle(it.collectionName),
                                itunesId = it.collectionId
                            )
                        }
                    } else null
                }

                val newer = listOfNotNull(deezerLatest, iTunesLatest).maxByOrNull {
                    SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.getDefault()
                    ).parse(it.releaseDate)?.time ?: 0L
                }

                withContext(Dispatchers.Main) {
                    if (newer != null) {
                        displayReleaseInfo(newer)
                        buttonSaveArtist.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.no_releases_found_for_artist, artist.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    showLoading(false)
                }
            }
        }, isListLayout)

        recyclerViewArtists.adapter = artistAdapter

        val currentReleases = (recyclerViewReleases.adapter as? ReleaseAdapter)?.currentList ?: emptyList()
        val releaseAdapter = ReleaseAdapter({ release ->
        }, isListLayout)
        releaseAdapter.submitList(currentReleases)
        recyclerViewReleases.adapter = releaseAdapter
    }
    private fun startBreathingAnimation() {
        if (breathingAnimator == null) {
            breathingAnimator = ValueAnimator.ofFloat(0.5f, 1.0f).apply {
                duration = 600
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { animator ->
                    releaseInfoPlaceholder.alpha = animator.animatedValue as Float
                }
            }
        }
        if (!breathingAnimator!!.isStarted) {
            breathingAnimator?.start()
        }
    }

    private fun stopBreathingAnimation() {
        breathingAnimator?.cancel()
        breathingAnimator?.removeAllUpdateListeners()
        breathingAnimator = null
        releaseInfoPlaceholder.alpha = 1.0f
    }


    private fun isAutoLoadReleasesEnabled(): Boolean {
        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return prefs.getBoolean("autoLoadReleases", true)
    }


    private fun normalizeTitle(title: String): String {
        return title.lowercase(Locale.getDefault())
            .replace(Regex("\\((feat\\.?|featuring)[^)]*\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*(single|ep|album)"), "")
            .replace(Regex("\\s*\\((single|ep|album)\\)"), "")
            .trim()
    }

    private fun isItunesSupportEnabled(): Boolean {
        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return prefs.getBoolean("itunesSupportEnabled", false)
    }


    private fun displayArtists(artists: List<DeezerArtist>) {
        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(false)

        if (artists.isNotEmpty()) {
            recyclerViewArtists.visibility = View.VISIBLE

            val adapter = SimilarArtistsAdapter(
                context = this,
                artists = artists,
                itemClick = { artist ->
                    selectedArtist = artist
                    recyclerViewArtists.visibility = View.GONE
                    buttonSaveArtist.visibility = View.GONE

                    saveSearchHistory(artist)
                    showLoading(true)
                    val isInitialClick = true

                    lifecycleScope.launch(Dispatchers.IO) {
                        val deezerId = artist.id
                        val deezerReleases = apiService.getArtistReleases(deezerId, 0)
                            .execute().body()?.data.orEmpty()
                        val deezerTitles = deezerReleases.map { normalizeTitle(it.title) }.toSet()

                        val deezerLatest = deezerReleases.maxByOrNull {
                            SimpleDateFormat(
                                "yyyy-MM-dd",
                                Locale.getDefault()
                            ).parse(it.release_date)?.time ?: 0L
                        }?.let {
                            UnifiedAlbum(
                                id = it.id.toString(),
                                title = it.title,
                                releaseDate = it.release_date,
                                coverUrl = it.getBestCoverUrl(),
                                artistName = artist.name,
                                releaseType = it.record_type?.replaceFirstChar { c -> c.uppercaseChar() },
                                deezerId = it.id
                            )
                        }

                        var iTunesLatest: UnifiedAlbum? = null

                        if (isItunesSupportEnabled()) {
                            val iTunesService = Retrofit.Builder()
                                .baseUrl("https://itunes.apple.com/")
                                .addConverterFactory(GsonConverterFactory.create())
                                .build()
                                .create(ITunesApiService::class.java)

                            val itunesMatch =
                                iTunesService.searchArtist(term = artist.name, entity = "musicArtist")
                                    .execute()
                                    .body()?.results?.firstOrNull { it.artistName == artist.name }

                            val itunesArtistId = itunesMatch?.artistId
                            artist.itunesId = itunesArtistId

                            val iTunesAlbums = itunesArtistId?.let {
                                iTunesService.lookupArtistWithAlbums(it).execute()
                                    .body()?.results.orEmpty()
                            } ?: emptyList()

                            val iTunesTitles = iTunesAlbums.mapNotNull {
                                it.collectionName?.let { name ->
                                    normalizeTitle(name)
                                }
                            }.toSet()

                            val common = deezerTitles.intersect(iTunesTitles)
                            val isMatch = common.size >= 3

                            iTunesLatest = if (isMatch) {
                                iTunesAlbums.maxByOrNull {
                                    it.releaseDate?.let { d ->
                                        SimpleDateFormat(
                                            "yyyy-MM-dd",
                                            Locale.getDefault()
                                        ).parse(d)?.time
                                    } ?: 0L
                                }?.let {
                                    UnifiedAlbum(
                                        id = it.collectionId.toString(),
                                        title = it.collectionName
                                            ?.replace(
                                                Regex(
                                                    "\\s*-\\s*(Single|EP|Album)",
                                                    RegexOption.IGNORE_CASE
                                                ), ""
                                            )
                                            ?.trim() ?: "Unknown Album",
                                        releaseDate = it.releaseDate ?: "Unknown Date",
                                        coverUrl = it.artworkUrl100?.replace("100x100bb", "1200x1200bb")
                                            ?: "",
                                        artistName = it.artistName ?: artist.name,
                                        releaseType = extractReleaseTypeFromTitle(it.collectionName),
                                        itunesId = it.collectionId
                                    )
                                }
                            } else null
                        }

                        val newer = listOfNotNull(deezerLatest, iTunesLatest).maxByOrNull {
                            SimpleDateFormat(
                                "yyyy-MM-dd",
                                Locale.getDefault()
                            ).parse(it.releaseDate)?.time ?: 0L
                        }

                        withContext(Dispatchers.Main) {
                            if (newer != null) {
                                displayReleaseInfo(newer)
                                buttonSaveArtist.visibility = View.VISIBLE
                            } else if (!isInitialClick) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.no_releases_found_for_artist, artist.name),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            showLoading(false)
                        }
                    }
                },
                isListLayout = isListLayout
            )

            recyclerViewArtists.adapter = adapter
            if (isListLayout) {
                recyclerViewArtists.layoutManager = LinearLayoutManager(this)
            } else {
                recyclerViewArtists.layoutManager = GridLayoutManager(this, 2)
            }
        } else {
            recyclerViewArtists.visibility = View.GONE
            Toast.makeText(this, getString(R.string.no_similar_artists_found), Toast.LENGTH_SHORT).show()
        }
        setMenuButtonEnabled(true)
    }

    private fun displayReleaseInfo(unifiedAlbum: UnifiedAlbum) {

        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        artistInfoContainer.visibility = View.INVISIBLE
        releaseInfoPlaceholder.visibility = View.VISIBLE

        placeholderAlbumArt.post {
            val width = placeholderAlbumArt.width
            if (width > 0) {
                val params = placeholderAlbumArt.layoutParams
                if (params.height != width) {
                    params.height = width
                    placeholderAlbumArt.layoutParams = params
                }
            }
        }

        startBreathingAnimation()


        textViewArtistName.text = unifiedAlbum.artistName

        val primaryTypedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimary, primaryTypedValue, true)
        val primaryColor = ContextCompat.getColor(this, primaryTypedValue.resourceId)

        val secondaryTypedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.textColorSecondary, secondaryTypedValue, true)
        val secondaryColor = ContextCompat.getColor(this, secondaryTypedValue.resourceId)

        val rawTitle = unifiedAlbum.title
        val parenMatch = Regex("\\((Single|EP|Album)\\)", RegexOption.IGNORE_CASE).find(rawTitle)
        val dashMatch = Regex("-(\\s*)(Single|EP|Album)", RegexOption.IGNORE_CASE).find(rawTitle)

        val extractedType = when {
            parenMatch != null -> parenMatch.groupValues[1]
            dashMatch != null -> dashMatch.groupValues[2]
            else -> null
        }?.replaceFirstChar { it.uppercaseChar() }

        val finalType = unifiedAlbum.releaseType ?: extractedType

        val cleanedTitle = rawTitle
            .replace(Regex("\\s*-\\s*(Single|EP|Album)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\((Single|EP|Album)\\)", RegexOption.IGNORE_CASE), "")
            .trim()

        if (finalType != null) {
            val fullText = "$cleanedTitle ($finalType)"
            val spannable = SpannableString(fullText)
            val start = fullText.indexOf("($finalType)")

            spannable.setSpan(
                ForegroundColorSpan(primaryColor),
                0,
                start,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            spannable.setSpan(
                ForegroundColorSpan(secondaryColor),
                start,
                fullText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            textViewAlbumTitle.text = spannable
        } else {
            textViewAlbumTitle.text = cleanedTitle
            textViewAlbumTitle.setTextColor(primaryColor)
        }

        val formattedDate = try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val date = inputFormat.parse(unifiedAlbum.releaseDate)
            date?.let { outputFormat.format(it) } ?: "Unknown Date"
        } catch (e: Exception) {
            Log.e(TAG, "Error in date formatting: ${e.message}")
            "Unknown Date"
        }
        textViewrelease_date.text = formattedDate

        val cornerRadius = 30f
        val highResUrl = getHighResArtworkUrl(unifiedAlbum.coverUrl)

        var artistProfileImageLoaded = false
        var albumArtLoaded = false

        val revealContentIfReady = {
            if (artistProfileImageLoaded && albumArtLoaded) {
                stopBreathingAnimation()
                releaseInfoPlaceholder.visibility = View.GONE

                artistInfoContainer.alpha = 0f
                artistInfoContainer.visibility = View.VISIBLE
                ObjectAnimator.ofFloat(artistInfoContainer, "alpha", 0f, 1f).apply {
                    duration = 400
                    start()
                }
                updateSaveButton()
            }
        }

        Glide.with(imageViewArtistProfile.context)
            .load(selectedArtist?.getBestPictureUrl())
            .placeholder(R.drawable.placeholder_circle_shape)
            .error(R.drawable.error_image)
            .circleCrop()
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    imageViewArtistProfile.setImageDrawable(resource)
                    artistProfileImageLoaded = true
                    revealContentIfReady()
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    imageViewArtistProfile.setImageDrawable(errorDrawable)
                    artistProfileImageLoaded = true
                    revealContentIfReady()
                }
            })

        Glide.with(imageViewAlbumArt)
            .load(highResUrl)
            .placeholder(R.drawable.ic_discography)
            .error(R.drawable.error_image)
            .transform(
                CenterCrop(),
                GranularRoundedCorners(cornerRadius, cornerRadius, cornerRadius, cornerRadius)
            )
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    imageViewAlbumArt.setImageDrawable(resource)
                    albumArtLoaded = true
                    revealContentIfReady()
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    imageViewAlbumArt.setImageDrawable(errorDrawable)
                    albumArtLoaded = true
                    revealContentIfReady()
                }
            })

        artistInfoContainer.setOnClickListener {
            val deezerId = unifiedAlbum.deezerId ?: -1L
            val itunesId = unifiedAlbum.itunesId ?: -1L

            val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
            if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
                Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (deezerId == -1L && (itunesId == -1L || !isItunesSupportEnabled())) {
                Toast.makeText(this, "No valid API source ID available for this release.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val source = when {
                deezerId > 0 -> "Deezer"
                itunesId > 0 && isItunesSupportEnabled() -> "iTunes"
                else -> null
            }

            if (source == null) {
                Toast.makeText(this, "No valid API source determined.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, ReleaseDetailsActivity::class.java).apply {
                putExtra("releaseId", when(source) {
                    "Deezer" -> deezerId
                    "iTunes" -> itunesId
                    else -> -1L
                })

                putExtra("releaseTitle", unifiedAlbum.title)
                putExtra("artistName", unifiedAlbum.artistName)
                putExtra("albumArtUrl", unifiedAlbum.coverUrl)
                putExtra("apiSource", source)
                putExtra("deezerId", deezerId)
                putExtra("itunesId", itunesId)
            }

            Log.d("MainActivity", "Sending to ReleaseDetails: " +
                    "releaseId=${intent.getLongExtra("releaseId", -1L)}, " +
                    "deezerId=${intent.getLongExtra("deezerId", -1L)}, " +
                    "itunesId=${intent.getLongExtra("itunesId", -1L)}, " +
                    "apiSource=${intent.getStringExtra("apiSource")}")

            startActivity(intent)
        }
    }

    private fun extractReleaseTypeFromTitle(originalTitle: String?): String? {
        if (originalTitle == null) return null

        val pattern = Regex("\\s*-\\s*(Single|EP|Album)", RegexOption.IGNORE_CASE)
        val match = pattern.find(originalTitle)
        return match?.groupValues?.get(1)?.replaceFirstChar { it.uppercaseChar() }
    }

    private fun triggerArtistSearch() {

        val query = editTextArtist.text.toString().trim()
        if (query.isNotEmpty()) {
            recyclerViewArtists.visibility = View.GONE
            recyclerViewReleases.visibility = View.GONE
            releaseInfoPlaceholder.visibility = View.GONE
            buttonSaveArtist.visibility = View.GONE
        }

        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        recyclerViewReleases.visibility = View.GONE

        val artistName = editTextArtist.text.toString().trim()
        if (artistName.isNotEmpty()) {
            fetchArtistBasedOnApi(artistName)
            hideKeyboard()
        }
    }


    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(editTextArtist.windowToken, 0)
    }


    private fun fetchArtistBasedOnApi(artistName: String) {
        fetchSimilarArtistsFromDeezer(artistName)
    }

    private fun fetchSimilarArtistsFromDeezer(artist: String) {

        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)
        setMenuButtonEnabled(false)
        buttonSaveArtist.visibility = View.GONE
        artistInfoContainer.visibility = View.GONE

        textViewNoSavedArtists.visibility = View.GONE

        apiService.searchArtist(artist).enqueue(object : Callback<DeezerSimilarArtistsResponse> {
            override fun onResponse(
                call: Call<DeezerSimilarArtistsResponse>,
                response: Response<DeezerSimilarArtistsResponse>
            ) {
                showLoading(false)
                val artists = response.body()?.data ?: emptyList()
                if (artists.isNotEmpty()) {
                    displayArtists(artists)
                } else {
                    recyclerViewArtists.visibility = View.GONE
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.no_similar_artists_found),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                setMenuButtonEnabled(true)
            }

            override fun onFailure(call: Call<DeezerSimilarArtistsResponse>, t: Throwable) {
                showLoading(false)
                setMenuButtonEnabled(true)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error_loading_artists),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun checkDeezerStatus() {

        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        apiService.ping().enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.deezer_api_online),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.deezer_api_error, response.code()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.deezer_api_not_reachable, t.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun checkItunesStatus() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val iTunesService = retrofit.create(ITunesApiService::class.java)

        iTunesService.ping().enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "iTunes API is reachable", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "iTunes API error: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(this@MainActivity, "iTunes API not reachable: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupBackGesture() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })
    }

    private fun showExitConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.exit_confirmation_title))
            .setMessage(getString(R.string.exit_confirmation_message))
            .setPositiveButton(getString(R.string.exit_confirmation_positive)) { _: DialogInterface, _: Int ->
                finish()
            }
            .setNegativeButton(getString(R.string.exit_confirmation_negative)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveSearchHistory(artist: DeezerArtist) {

        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val existingHistory = db.searchHistoryDao().getByDeezerId(artist.id)
            val history = SearchHistory(
                artistName = artist.name,
                profileImageUrl = artist.getBestPictureUrl(),
                deezerId = artist.id,
                itunesId = artist.itunesId
            )

            if (existingHistory == null) {
                db.searchHistoryDao().insert(history)
            } else {
                val updated = existingHistory.copy(
                    artistName = artist.name,
                    profileImageUrl = artist.getBestPictureUrl()
                )
                db.searchHistoryDao().update(updated)

            }

            withContext(Dispatchers.Main) {
                val historyList = db.searchHistoryDao().getAllHistory()
                (recyclerViewArtists.adapter as? SearchHistoryAdapter)?.refreshHistory(historyList)
            }
        }
    }

    private fun showSearchHistory() {

        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val historyList = db.searchHistoryDao().getAllHistory()
            val uniqueHistoryList = historyList.distinctBy { "${it.deezerId}_${it.itunesId}" }
            withContext(Dispatchers.Main) {
                if (uniqueHistoryList.isNotEmpty()) {
                    val recyclerView = RecyclerView(this@MainActivity).apply {
                        layoutManager = LinearLayoutManager(this@MainActivity)
                    }

                    val dialog = MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.SearchHistory)
                        .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                        .create()

                    val adapter = SearchHistoryAdapter(
                        this@MainActivity,
                        uniqueHistoryList.toMutableList(),
                        { historyItem ->
                            val artist = DeezerArtist(
                                id = historyItem.deezerId ?: -1L,
                                name = historyItem.artistName,
                                picture = historyItem.profileImageUrl,
                                picture_small = "",
                                picture_medium = "",
                                picture_big = "",
                                picture_xl = ""
                            )

                            this@MainActivity.selectedArtist = artist
                            Log.d(TAG, "Selected artist: ${selectedArtist?.name}")

                            buttonSaveArtist.visibility = View.GONE

                            showLoading(true)

                            processCombinedArtist(
                                artist,
                                onAlbumFound = { unifiedAlbum ->
                                    displayReleaseInfo(unifiedAlbum)
                                    buttonSaveArtist.visibility = View.VISIBLE
                                    showLoading(false)

                                    openArtistDiscography(artist)
                                },
                                onFailure = {
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(
                                            R.string.no_releases_found_for_artist,
                                            artist.name
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showLoading(false)
                                }
                            )
                        },
                        dialog
                    )

                    recyclerView.adapter = adapter

                    dialog.setButton(
                        DialogInterface.BUTTON_POSITIVE,
                        getString(R.string.clear_history)
                    ) { _, _ ->
                        clearSearchHistory()
                        adapter.refreshHistory(emptyList())
                    }

                    dialog.setView(recyclerView)
                    dialog.show()

                    adapter.refreshHistory(uniqueHistoryList)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.no_search_history_found),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun clearSearchHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            db.searchHistoryDao().clearHistory()
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.search_history_cleared),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun toggleFabMenu() {
        val fabMenu: FloatingActionButton = findViewById(R.id.fabMenu)
        val fabSavedArtists: FloatingActionButton = findViewById(R.id.fabSavedArtists)
        val fabSettings: FloatingActionButton = findViewById(R.id.fabSettings)
        val fabAbout: FloatingActionButton = findViewById(R.id.fabAbout)
        val fabCheckStatus: FloatingActionButton = findViewById(R.id.fabCheckStatus)

        val translationDistance = -0f

        if (isFabMenuOpen) {
            fabSavedArtists.animate().translationY(0f).alpha(0f).setDuration(200).start()
            fabSettings.animate().translationY(0f).alpha(0f).setDuration(200).start()
            fabCheckStatus.animate().translationY(0f).alpha(0f).setDuration(200).start()
            fabAbout.animate().translationY(0f).alpha(0f).setDuration(200).start()

            fabMenu.setImageResource(R.drawable.ic_menu)

            Handler(Looper.getMainLooper()).postDelayed({
                fabSavedArtists.visibility = View.GONE
                fabSettings.visibility = View.GONE
                fabCheckStatus.visibility = View.GONE
                fabAbout.visibility = View.GONE
            }, 200)
        } else {
            fabSavedArtists.visibility = View.VISIBLE
            fabSettings.visibility = View.VISIBLE
            fabCheckStatus.visibility = View.VISIBLE
            fabAbout.visibility = View.VISIBLE

            fabSavedArtists.animate().translationY(translationDistance * 1).alpha(1f)
                .setDuration(200).start()
            fabSettings.animate().translationY(translationDistance * 2).alpha(1f).setDuration(200)
                .start()
            fabCheckStatus.animate().translationY(translationDistance * 3).alpha(1f)
                .setDuration(200).start()
            fabAbout.animate().translationY(translationDistance * 5).alpha(1f).setDuration(200)
                .start()

            fabMenu.setImageResource(R.drawable.ic_close)
        }
        isFabMenuOpen = !isFabMenuOpen
    }

    private fun showTutorial() {
        val fabMenu: FloatingActionButton = findViewById(R.id.fabMenu)
        val fabSavedArtists: FloatingActionButton = findViewById(R.id.fabSavedArtists)
        val fabSettings: FloatingActionButton = findViewById(R.id.fabSettings)
        val fabAbout: FloatingActionButton = findViewById(R.id.fabAbout)
        val fabCheckStatus: FloatingActionButton = findViewById(R.id.fabCheckStatus)

        fabSavedArtists.visibility = View.VISIBLE
        fabSavedArtists.translationY = 0f
        fabSavedArtists.alpha = 1f

        fabSettings.visibility = View.VISIBLE
        fabSettings.translationY = 0f
        fabSettings.alpha = 1f

        fabCheckStatus.visibility = View.VISIBLE
        fabCheckStatus.translationY = 0f
        fabCheckStatus.alpha = 1f

        fabAbout.visibility = View.VISIBLE
        fabAbout.translationY = 0f
        fabAbout.alpha = 1f

        fabMenu.visibility = View.VISIBLE
        fabMenu.alpha = 1f

        TapTargetSequence(this)
            .targets(
                TapTarget.forView(
                    fabMenu,
                    getString(R.string.tutorial_main_menu_title),
                    getString(R.string.tutorial_main_menu_message)
                ).transparentTarget(true).cancelable(false),
                TapTarget.forView(
                    fabSavedArtists,
                    getString(R.string.tutorial_saved_artists_title),
                    getString(R.string.tutorial_saved_artists_message)
                ).transparentTarget(true).cancelable(false),
                TapTarget.forView(
                    fabSettings,
                    getString(R.string.tutorial_settings_title),
                    getString(R.string.tutorial_settings_message)
                ).transparentTarget(true).cancelable(false),
                TapTarget.forView(
                    fabAbout,
                    getString(R.string.tutorial_about_title),
                    getString(R.string.tutorial_about_message)
                ).transparentTarget(true).cancelable(false),
                TapTarget.forView(
                    fabCheckStatus,
                    getString(R.string.tutorial_check_status_title),
                    getString(R.string.tutorial_check_status_message)
                ).transparentTarget(true).cancelable(false)
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.tutorial_completed_message),
                        Toast.LENGTH_SHORT
                    ).show()

                    fabSavedArtists.visibility = View.GONE
                    fabSettings.visibility = View.GONE
                    fabCheckStatus.visibility = View.GONE
                    fabAbout.visibility = View.GONE
                    fabSavedArtists.translationY = 0f
                    fabSettings.translationY = 0f
                    fabCheckStatus.translationY = 0f
                    fabAbout.translationY = 0f
                }

                override fun onSequenceStep(tapTarget: TapTarget, targetClicked: Boolean) {
                }

                override fun onSequenceCanceled(tapTarget: TapTarget?) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.tutorial_aborted_message),
                        Toast.LENGTH_SHORT
                    ).show()

                    fabSavedArtists.visibility = View.GONE
                    fabSettings.visibility = View.GONE
                    fabCheckStatus.visibility = View.GONE
                    fabAbout.visibility = View.GONE
                    fabSavedArtists.translationY = 0f
                    fabSettings.translationY = 0f
                    fabCheckStatus.translationY = 0f
                    fabAbout.translationY = 0f
                }
            })
            .start()
    }

    private fun showLoading(isLoading: Boolean) {
        Log.d(TAG, "Loading state: $isLoading")
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun setupApiService() {

        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DeezerApiService::class.java)
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Requesting notification permission.")
            pushNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Log.d(TAG, "Notification permission already granted.")
            WorkManagerUtil.reEnqueueIfMissing(this)
        }
    }



    private fun openArtistDiscography(artist: DeezerArtist) {
        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ArtistDiscographyActivity::class.java).apply {
            putExtra("artistName", artist.name)
            putExtra("artistImageUrl", artist.getBestPictureUrl())
            putExtra("deezerId", artist.id)
            putExtra("itunesId", artist.itunesId ?: -1L)
        }
        startActivity(intent)
    }


    private fun updateSaveButton() {
        val artist = selectedArtist
        if (artist == null) {
            buttonSaveArtist.visibility = View.GONE
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var existingArtist = db.savedArtistDao().getArtistById(artist.id)

            if (existingArtist == null) {
                existingArtist = db.savedArtistDao().getArtistByName(artist.name)
            }

            withContext(Dispatchers.Main) {
                buttonSaveArtist.setImageResource(
                    if (existingArtist != null) R.drawable.ic_saved_artist else R.drawable.ic_save_artist
                )
                buttonSaveArtist.visibility = View.VISIBLE
            }
        }
    }

    private fun setMenuButtonEnabled(enabled: Boolean) {
        val fabMenu: FloatingActionButton = findViewById(R.id.fabMenu)
        fabMenu.alpha = if (enabled) 1.0f else 0.5f
        fabMenu.isClickable = enabled
    }

    private fun saveArtist() {

        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        val artist = selectedArtist ?: return
        val releaseDate = textViewrelease_date.text.toString()
        val profileImageUrl = artist.getBestPictureUrl()
        val releaseTitle = textViewAlbumTitle.text.toString()

        lifecycleScope.launch(Dispatchers.IO) {
            val existingArtist = db.savedArtistDao().getArtistById(artist.id)
            if (existingArtist != null) {
                db.savedArtistDao().delete(existingArtist)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.artist_removed, artist.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    updateSaveButton()
                }
                return@launch
            }

            var iTunesArtistId: Long? = null
            var itunesTitles: Set<String>? = emptySet()

            val deezerSearchResults =
                apiService.searchArtist(artist.name).execute().body()?.data.orEmpty()
            val exactDeezerMatch = deezerSearchResults.firstOrNull { it.name == artist.name }
            val deezerArtistId = exactDeezerMatch?.id ?: -1L

            val deezerReleases = if (deezerArtistId > 0) {
                apiService.getArtistReleases(deezerArtistId, 0).execute().body()?.data.orEmpty()
            } else emptyList()

            val deezerTitles = deezerReleases.map { normalizeTitle(it.title) }.toSet()

            if (isItunesSupportEnabled()) {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://itunes.apple.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val iTunesService = retrofit.create(ITunesApiService::class.java)

                val iTunesSearchResults =
                    iTunesService.searchArtist(term = artist.name, entity = "musicArtist")
                        .execute().body()?.results.orEmpty()
                val exactITunesMatch =
                    iTunesSearchResults.firstOrNull { it.artistName == artist.name }
                iTunesArtistId = exactITunesMatch?.artistId

                itunesTitles = if (iTunesArtistId != null) {
                    val albumResponse =
                        iTunesService.lookupArtistWithAlbums(iTunesArtistId).execute().body()
                    albumResponse?.results
                        ?.filter { it.collectionName != null }
                        ?.map { normalizeTitle(it.collectionName!!) }
                        ?.toSet()
                } else emptySet()
            }

            Log.d(TAG, "Deezer-Title (${deezerTitles.size}): ${deezerTitles.sorted()}")
            Log.d(TAG, "iTunes-Title (${itunesTitles?.size ?: 0}): ${itunesTitles?.sorted()}")

            val commonReleases = deezerTitles.intersect(itunesTitles ?: emptySet())
            Log.d(TAG, "Shared releases (${commonReleases.size}): ${commonReleases.sorted()}")

            if (deezerTitles.isEmpty() && itunesTitles.isNullOrEmpty()) {
                Log.w(TAG, "Neither Deezer nor iTunes releases available. Saving aborted.")
                return@launch
            }

            db.savedArtistDao().insert(
                SavedArtist(
                    id = if (deezerArtistId > 0) deezerArtistId else artist.id,
                    name = artist.name,
                    lastReleaseDate = releaseDate,
                    lastReleaseTitle = releaseTitle,
                    profileImageUrl = profileImageUrl,
                    isFromDeezer = deezerTitles.isNotEmpty(),
                    isFromITunes = !itunesTitles.isNullOrEmpty(),
                    deezerId = deezerArtistId.takeIf { it > 0 },
                    itunesId = if (isItunesSupportEnabled()) iTunesArtistId else null,
                    notifyOnNewRelease = true
                )
            )

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.artist_saved, artist.name),
                    Toast.LENGTH_SHORT
                ).show()
                updateSaveButton()
            }
        }
    }

    fun getHighResArtworkUrl(originalUrl: String?): String {
        return originalUrl?.replace("100x100bb", "1200x1200bb") ?: ""
    }

    private fun clearPreviousSearch() {
        editTextArtist.text.clear()
        buttonSaveArtist.visibility = View.GONE
        artistInfoContainer.visibility = View.GONE
        recyclerViewArtists.adapter = null
        recyclerViewArtists.visibility = View.GONE
    }

    private fun processCombinedArtist(
        artist: DeezerArtist,
        onAlbumFound: (UnifiedAlbum) -> Unit,
        onFailure: () -> Unit
    ) {

        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val deezerReleases = apiService.getArtistReleases(artist.id, 0)
                .execute().body()?.data.orEmpty()
            val deezerTitles = deezerReleases.map { normalizeTitle(it.title) }.toSet()

            val deezerLatest = deezerReleases.maxByOrNull {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.release_date)?.time
                    ?: 0L
            }?.let {
                UnifiedAlbum(
                    id = it.id.toString(),
                    title = it.title,
                    releaseDate = it.release_date,
                    coverUrl = it.getBestCoverUrl(),
                    artistName = artist.name,
                    releaseType = it.record_type?.replaceFirstChar { c -> c.uppercaseChar() },
                    deezerId = artist.id
                )
            }

            var iTunesLatest: UnifiedAlbum? = null

            if (isItunesSupportEnabled()) {
                val iTunesService = Retrofit.Builder()
                    .baseUrl("https://itunes.apple.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ITunesApiService::class.java)

                val itunesMatch =
                    iTunesService.searchArtist(term = artist.name, entity = "musicArtist")
                        .execute().body()?.results?.firstOrNull { it.artistName == artist.name }

                val itunesArtistId = itunesMatch?.artistId
                artist.itunesId = itunesArtistId

                val iTunesAlbums = itunesArtistId?.let {
                    iTunesService.lookupArtistWithAlbums(it).execute().body()?.results.orEmpty()
                } ?: emptyList()

                val iTunesTitles =
                    iTunesAlbums.mapNotNull { it.collectionName?.let { name -> normalizeTitle(name) } }
                        .toSet()
                val common = deezerTitles.intersect(iTunesTitles)
                val isMatch = common.size >= 3

                if (isMatch) {
                    iTunesLatest = iTunesAlbums.maxByOrNull {
                        it.releaseDate?.let { d ->
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(d)?.time
                        } ?: 0L
                    }?.let {
                        UnifiedAlbum(
                            id = it.collectionId.toString(),
                            title = it.collectionName
                                ?.replace(
                                    Regex(
                                        "\\s*-\\s*(Single|EP|Album)",
                                        RegexOption.IGNORE_CASE
                                    ), ""
                                )
                                ?.trim() ?: "Unknown Album",
                            releaseDate = it.releaseDate ?: "Unknown Date",
                            coverUrl = it.artworkUrl100?.replace("100x100bb", "1200x1200bb") ?: "",
                            artistName = it.artistName ?: artist.name,
                            releaseType = extractReleaseTypeFromTitle(it.collectionName),
                            itunesId = itunesArtistId
                        )
                    }
                }
            }

            val newest = listOfNotNull(deezerLatest, iTunesLatest).maxByOrNull {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.releaseDate)?.time
                    ?: 0L
            }

            withContext(Dispatchers.Main) {
                if (newest != null) {
                    onAlbumFound(newest)
                } else {
                    onFailure()
                }
            }
        }
    }

    private fun releaseInfoScore(release: ReleaseItem): Int {
        var score = 0
        if (!release.albumArtUrl.isNullOrBlank()) score += 2
        if (!release.releaseType.isNullOrBlank()) score += 2
        if (!release.artistName.isNullOrBlank()) score += 1
        if (!release.apiSource.isNullOrBlank()) score += 1
        return score
    }

    private fun loadSavedReleases() {
        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        textViewNoSavedArtists.visibility = View.GONE
        recyclerViewReleases.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val savedArtists = db.savedArtistDao().getAll()
            if (savedArtists.isEmpty()) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (editTextArtist.text.isNullOrBlank()) {
                        textViewNoSavedArtists.visibility = View.VISIBLE
                    } else {
                        textViewNoSavedArtists.visibility = View.GONE
                    }
                }
                return@launch
            }

            val releaseItems = coroutineScope {
                savedArtists.map { artist ->
                    async { fetchReleasesForArtist(artist) }
                }.awaitAll().flatten()
            }

            val uniqueReleases = releaseItems.groupBy {
                val normTitle = normalizeTitle(it.title)
                val shortDate = it.releaseDate.take(10)
                "$normTitle|$shortDate"
            }.mapValues { (_, releases) ->
                releases.maxByOrNull { releaseInfoScore(it) } ?: releases.first()
            }.values.toList()

            val sortedReleaseItems = uniqueReleases
                .map { release ->
                    val cleanedTitle = release.title
                        .replace(Regex("\\s*-\\s*(Single|EP|Album)", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("\\s*\\((Single|EP|Album)\\)", RegexOption.IGNORE_CASE), "")
                        .trim()

                    val type = release.releaseType?.replaceFirstChar { it.uppercaseChar() }
                    val displayTitle =
                        if (!type.isNullOrBlank()) "$cleanedTitle ($type)" else cleanedTitle
                    release.copy(title = displayTitle)
                }
                .sortedByDescending {
                    try {
                        SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.getDefault()
                        ).parse(it.releaseDate)?.time
                    } catch (_: Exception) {
                        null
                    }
                }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE

                if (sortedReleaseItems.isEmpty()) {
                    textViewNoSavedArtists.visibility = View.VISIBLE
                    return@withContext
                }

                recyclerViewReleases.visibility = View.VISIBLE

                if (isListLayout) {
                    recyclerViewReleases.layoutManager = LinearLayoutManager(this@MainActivity)
                } else {
                    recyclerViewReleases.layoutManager = GridLayoutManager(this@MainActivity, 2)
                }

                val adapter = ReleaseAdapter(
                    onReleaseClick = { release ->
                        val intent =
                            Intent(this@MainActivity, ReleaseDetailsActivity::class.java).apply {
                                putExtra("releaseId", release.id)
                                putExtra("releaseTitle", release.title)
                                putExtra("artistName", release.artistName)
                                putExtra("albumArtUrl", release.albumArtUrl)
                                putExtra("deezerId", release.deezerId ?: -1L)
                                putExtra("itunesId", release.itunesId ?: -1L)
                                putExtra("apiSource", release.apiSource)
                            }
                        startActivity(intent)
                    },
                    isListLayout = isListLayout
                )

                recyclerViewReleases.adapter = adapter
                adapter.submitList(sortedReleaseItems)
            }
        }
    }

    private fun fetchReleasesForArtist(artist: SavedArtist): List<ReleaseItem> {
        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(this)
        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
            return emptyList()
        }

        val releases = mutableListOf<ReleaseItem>()
        val artistImage = artist.profileImageUrl ?: ""

        try {
            val deezerResponse =
                apiService.getArtistReleases(artist.deezerId ?: artist.id, 0).execute()
            if (deezerResponse.isSuccessful) {
                val deezerReleases = deezerResponse.body()?.data?.map { release ->
                    ReleaseItem(
                        id = release.id,
                        title = release.title,
                        artistName = artist.name,
                        albumArtUrl = release.getBestCoverUrl(),
                        releaseDate = release.release_date,
                        apiSource = "Deezer",
                        deezerId = release.id,
                        artistImageUrl = artistImage,
                        releaseType = release.record_type?.replaceFirstChar { it.uppercaseChar() }
                    )
                } ?: emptyList()
                releases.addAll(deezerReleases)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Deezer error for ${artist.name}: ${e.message}", e)
        }

        if (isItunesSupportEnabled() && artist.itunesId != null) {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://itunes.apple.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val iTunesService = retrofit.create(ITunesApiService::class.java)

                val response = iTunesService.lookupArtistWithAlbums(artist.itunesId).execute()
                if (response.isSuccessful) {
                    val iTunesReleases = response.body()?.results.orEmpty()
                        .filter { it.collectionType in listOf("Album", "EP", "Single") }
                        .map { album ->
                            val extractedType =
                                Regex("\\s*-\\s*(Single|EP|Album)", RegexOption.IGNORE_CASE)
                                    .find(album.collectionName ?: "")
                                    ?.groupValues?.get(1)
                                    ?.replaceFirstChar { it.uppercaseChar() }

                            ReleaseItem(
                                id = album.collectionId ?: -1L,
                                title = album.collectionName ?: "Unknown",
                                artistName = album.artistName ?: artist.name,
                                albumArtUrl = album.artworkUrl100?.replace(
                                    "100x100bb",
                                    "1200x1200bb"
                                ) ?: "",
                                releaseDate = album.releaseDate ?: "Unknown",
                                apiSource = "iTunes",
                                itunesId = album.collectionId,
                                artistImageUrl = artistImage,
                                releaseType = extractedType
                            )
                        }
                    releases.addAll(iTunesReleases)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "iTunes error for ${artist.name}: ${e.message}", e)
            }
        }

        return releases
    }

}