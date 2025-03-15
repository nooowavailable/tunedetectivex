package com.dev.tunedetectivex

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var pushNotificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var recyclerViewArtists: RecyclerView
    private lateinit var editTextArtist: EditText
    private lateinit var buttonSaveArtist: ImageButton
    private lateinit var textViewname: TextView
    private lateinit var imageViewAlbumArt: ImageView
    private lateinit var textViewAlbumTitle: TextView
    private lateinit var textViewrelease_date: TextView
    private lateinit var apiService: DeezerApiService
    private lateinit var db: AppDatabase
    private var selectedArtist: DeezerArtist? = null
    private lateinit var progressBar: ProgressBar
    private var isFabMenuOpen = false
    private lateinit var notificationManager: NotificationManagerCompat
    private val fetchedArtists = mutableSetOf<Long>()
    private var isNetworkRequestsAllowed = true
    private lateinit var buttonOpenDiscography: MaterialButton
    private lateinit var fabAbout: FloatingActionButton
    private lateinit var artistInfoContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pushNotificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Notification permission granted.")
            } else {
                Log.w(TAG, "Notification permission denied.")
                Toast.makeText(
                    this,
                    "Notification permission is required for progress updates.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        recyclerViewArtists = findViewById(R.id.recyclerViewArtists)
        recyclerViewArtists.layoutManager = LinearLayoutManager(this)
        editTextArtist = findViewById(R.id.editTextArtist)
        buttonSaveArtist = findViewById(R.id.buttonSaveArtist)
        artistInfoContainer = findViewById(R.id.artistInfoContainer)
        textViewname = findViewById(R.id.textViewname)
        textViewAlbumTitle = findViewById(R.id.textViewAlbumTitle)
        textViewrelease_date = findViewById(R.id.textViewrelease_date)
        imageViewAlbumArt = findViewById(R.id.imageViewAlbumArt)
        progressBar = findViewById(R.id.progressBarLoading)
        buttonOpenDiscography = findViewById(R.id.button_open_discography)
        notificationManager = NotificationManagerCompat.from(this)
        fabAbout = findViewById(R.id.fabAbout)
        fabAbout.visibility = View.GONE

        val fabMenu: FloatingActionButton = findViewById(R.id.fabMenu)
        val fabSavedArtists: FloatingActionButton = findViewById(R.id.fabSavedArtists)
        val fabSettings: FloatingActionButton = findViewById(R.id.fabSettings)
        val fabSelectFolder: FloatingActionButton = findViewById(R.id.fabSelectFolder)

        fabSavedArtists.visibility = View.GONE
        fabSelectFolder.visibility = View.GONE
        fabSettings.visibility = View.GONE
        fabSelectFolder.visibility = View.GONE
        fabSavedArtists.translationY = 0f
        fabSettings.translationY = 0f
        fabSelectFolder.translationY = 0f

        isFabMenuOpen = false

        val appPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val isFirstRun = appPreferences.getBoolean("isFirstRun", true)

        if (isFirstRun) {
            showTutorial()
            appPreferences.edit { putBoolean("isFirstRun", false) }
        }

        val intervalInMinutes =
            getSharedPreferences("TaskLog", MODE_PRIVATE).getInt("fetchInterval", 30)
        WorkManagerUtil.setupFetchReleasesWorker(this, intervalInMinutes)

        requestNotificationPermission()
        checkNetworkTypeAndSetFlag()
        updateSaveButton()
        clearPreviousSearch()
        setupApiService()
        setupBackGesture()

        val searchLayout: TextInputLayout = findViewById(R.id.searchLayout)
        searchLayout.setEndIconOnClickListener {
            showSearchHistory()
        }

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

        fabSelectFolder.setOnClickListener {
            startActivity(Intent(this, FolderImportActivity::class.java))
        }

        fabAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
            toggleFabMenu()
        }

        db = AppDatabase.getDatabase(applicationContext)
        editTextArtist.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        editTextArtist.setSingleLine()
        editTextArtist.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val artistName = editTextArtist.text.toString().trim()
                Log.d(TAG, "Artist name entered: $artistName")
                if (artistName.isNotEmpty()) {
                    fetchSimilarArtists(artistName)
                }

                val inputMethodManager =
                    getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(editTextArtist.windowToken, 0)

                true
            } else {
                false
            }
        }

        editTextArtist.setOnFocusChangeListener { view, hasFocus ->
            if (!hasFocus) {
                val artistName = editTextArtist.text.toString().trim()
                if (artistName.isNotEmpty()) {
                    fetchSimilarArtists(artistName)
                }
            } else {
                val inputMethodManager =
                    getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        buttonSaveArtist.setOnClickListener {
            saveArtist()
        }

        buttonOpenDiscography.setOnClickListener {
            selectedArtist?.let { artist ->
                openArtistDiscography(artist)
            } ?: Toast.makeText(this, R.string.Noartistselected, Toast.LENGTH_SHORT).show()
        }
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


    private fun checkNetworkTypeAndSetFlag() {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any") ?: "Any"
        isNetworkRequestsAllowed = WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkType)
    }

    private fun saveSearchHistory(artist: DeezerArtist) {
        lifecycleScope.launch(Dispatchers.IO) {
            val existingHistory = db.searchHistoryDao().getHistoryByArtistId(artist.id)
            val history = SearchHistory(
                artistName = artist.name,
                profileImageUrl = artist.getBestPictureUrl(),
                artistId = artist.id
            )

            if (existingHistory == null) {
                db.searchHistoryDao().insert(history)
            } else {
                existingHistory.artistName = artist.name
                existingHistory.profileImageUrl = artist.getBestPictureUrl()
                db.searchHistoryDao().update(existingHistory)
            }

            withContext(Dispatchers.Main) {
                val historyList = db.searchHistoryDao().getAllHistory()
                (recyclerViewArtists.adapter as? SearchHistoryAdapter)?.refreshHistory(historyList)
            }
        }
    }

    private fun showSearchHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val historyList = db.searchHistoryDao().getAllHistory()
            val uniqueHistoryList = historyList.distinctBy { it.artistId }
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
                                id = historyItem.artistId,
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

                            checkNetworkTypeAndSetFlag()
                            if (!isNetworkRequestsAllowed) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.network_type_not_available),
                                    Toast.LENGTH_SHORT
                                ).show()
                                showLoading(false)
                                return@SearchHistoryAdapter
                            }

                            fetchLatestReleaseForArtist(artist.id,
                                onSuccess = { album ->
                                    displayReleaseInfo(album)
                                    buttonSaveArtist.visibility = View.VISIBLE
                                    showLoading(false)
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

    private fun fetchAndCheckDiscography(artistId: Long, title: String) {
        if (fetchedArtists.contains(artistId)) {
            Log.d("MainActivity", "Already fetched discography for artist ID: $artistId")
            return
        }

        fetchedArtists.add(artistId)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("MainActivity", "Fetching discography for artist ID: $artistId")
                val response = apiService.getArtistReleases(artistId, 0).execute()
                if (response.isSuccessful) {
                    val releases = response.body()?.data
                    if (!releases.isNullOrEmpty()) {
                        val match =
                            releases.firstOrNull { it.title.equals(title, ignoreCase = true) }
                        if (match != null) {
                            Log.d("MainActivity", "Match found: ${match.title} in discography")
                        } else {
                            Log.d("MainActivity", "No matching release found for title: $title")
                        }
                    } else {
                        Log.d("MainActivity", "No releases found for artist ID: $artistId")
                    }
                } else {
                    Log.e(
                        "MainActivity",
                        "Deezer API error: ${response.code()} ${response.message()}"
                    )
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching discography from Deezer", e)
            }
        }
    }

    private fun toggleFabMenu() {
        val fabMenu: FloatingActionButton = findViewById(R.id.fabMenu)
        val fabSavedArtists: FloatingActionButton = findViewById(R.id.fabSavedArtists)
        val fabSettings: FloatingActionButton = findViewById(R.id.fabSettings)
        val fabSelectFolder: FloatingActionButton = findViewById(R.id.fabSelectFolder)
        val fabAbout: FloatingActionButton = findViewById(R.id.fabAbout)

        val appPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val isFolderImportEnabled = appPreferences.getBoolean("isFolderImportEnabled", false)

        fabSelectFolder.visibility = if (isFolderImportEnabled) View.VISIBLE else View.GONE

        val translationDistance = -0f

        if (isFabMenuOpen) {
            fabSavedArtists.animate().translationY(0f).alpha(0f).setDuration(200).start()
            fabSettings.animate().translationY(0f).alpha(0f).setDuration(200).start()
            fabSelectFolder.animate().translationY(0f).alpha(0f).setDuration(200).start()
            fabAbout.animate().translationY(0f).alpha(0f).setDuration(200).start()

            fabMenu.setImageResource(R.drawable.ic_menu)

            Handler(Looper.getMainLooper()).postDelayed({
                fabSavedArtists.visibility = View.GONE
                fabSettings.visibility = View.GONE
                fabSelectFolder.visibility = View.GONE
                fabAbout.visibility = View.GONE
            }, 200)
        } else {
            fabSavedArtists.visibility = View.VISIBLE
            fabSettings.visibility = View.VISIBLE
            fabSelectFolder.visibility = if (isFolderImportEnabled) View.VISIBLE else View.GONE
            fabAbout.visibility = View.VISIBLE

            fabSavedArtists.animate().translationY(translationDistance).alpha(1f).setDuration(200)
                .start()
            fabSettings.animate().translationY(translationDistance * 2).alpha(1f).setDuration(200)
                .start()
            fabSelectFolder.animate().translationY(translationDistance * 3).alpha(1f)
                .setDuration(200).start()
            fabAbout.animate().translationY(translationDistance * 4).alpha(1f).setDuration(200)
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

        fabSavedArtists.visibility = View.VISIBLE
        fabSavedArtists.translationY = 0f
        fabSavedArtists.alpha = 1f

        fabSettings.visibility = View.VISIBLE
        fabSettings.translationY = 0f
        fabSettings.alpha = 1f

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
                    fabAbout.visibility = View.GONE
                    fabSavedArtists.translationY = 0f
                    fabSettings.translationY = 0f
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
                    fabAbout.visibility = View.GONE
                    fabSavedArtists.translationY = 0f
                    fabSettings.translationY = 0f
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
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DeezerApiService::class.java)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                pushNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Log.d(TAG, "Notification permission already granted.")
            }
        } else {
            Log.d(TAG, "Notification permission not required for this Android version.")
        }
    }

    private fun fetchSimilarArtists(artist: String) {
        if (artist.isBlank()) {
            Toast.makeText(this, getString(R.string.please_enter_artist_name), Toast.LENGTH_SHORT)
                .show()
            return
        }

        checkNetworkTypeAndSetFlag()

        if (!isNetworkRequestsAllowed) {
            Log.w(TAG, "Selected network type is not available. Skipping network requests.")
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT)
                .show()
            return
        }

        showLoading(true)
        setMenuButtonEnabled(false)

        buttonSaveArtist.visibility = View.GONE
        artistInfoContainer.visibility = View.GONE

        Log.d(TAG, "Fetching similar artists for: $artist")

        apiService.searchArtist(artist).enqueue(object : Callback<DeezerSimilarArtistsResponse> {
            override fun onResponse(
                call: Call<DeezerSimilarArtistsResponse>,
                response: Response<DeezerSimilarArtistsResponse>
            ) {
                showLoading(false)
                Log.d(TAG, "Response received: ${response.code()}")
                val artists = response.body()?.data ?: emptyList()

                if (artists.isNotEmpty()) {
                    displayArtists(artists)
                } else {
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
                Log.e(TAG, "Error loading artists: ${t.message}", t)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error_loading_artists),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun fetchLatestReleaseForArtist(
        artistId: Long,
        onSuccess: (DeezerAlbum) -> Unit,
        onFailure: () -> Unit
    ) {
        if (!isNetworkRequestsAllowed) {
            Log.w(TAG, "Network requests not allowed. Skipping fetch for artist ID: $artistId")
            onFailure()
            return
        }

        showLoading(true)

        apiService.getArtistReleases(artistId, 0).enqueue(object : Callback<DeezerAlbumsResponse> {
            override fun onResponse(
                call: Call<DeezerAlbumsResponse>,
                response: Response<DeezerAlbumsResponse>
            ) {
                showLoading(false)

                if (response.isSuccessful) {
                    val releases = response.body()?.data ?: emptyList()
                    if (releases.isNotEmpty()) {
                        val latestRelease = releases.maxByOrNull { release ->
                            SimpleDateFormat(
                                "yyyy-MM-dd",
                                Locale.getDefault()
                            ).parse(release.release_date)?.time ?: 0L
                        }
                        latestRelease?.let {
                            fetchAndCheckDiscography(artistId, it.title)
                            fetchArtistProfilePicture(artistId)
                            onSuccess(it)
                        } ?: onFailure()
                    } else {
                        Log.d(TAG, "No Releases found for Artist ID: $artistId")
                        onFailure()
                    }
                } else {
                    Log.e(
                        TAG,
                        "Error when retrieving releases for Artist ID=$artistId: ${response.code()} ${response.message()}"
                    )
                    onFailure()
                }
            }

            override fun onFailure(call: Call<DeezerAlbumsResponse>, t: Throwable) {
                showLoading(false)
                Log.e(
                    TAG,
                    "Error when retrieving releases for Artist ID=$artistId: ${t.message}",
                    t
                )
                onFailure()
            }
        })
    }

    private fun displayArtists(artists: List<DeezerArtist>) {
        showLoading(false)

        if (artists.isNotEmpty()) {
            recyclerViewArtists.visibility = View.VISIBLE
            val adapter = SimilarArtistsAdapter(this, artists) { artist ->
                selectedArtist = artist
                recyclerViewArtists.visibility = View.GONE
                buttonOpenDiscography.visibility = View.VISIBLE
                buttonSaveArtist.visibility = View.GONE

                saveSearchHistory(artist)
                fetchLatestReleaseForArtist(
                    artistId = artist.id,
                    onSuccess = { album ->
                        fetchAndCheckDiscography(artist.id, album.title)
                        displayReleaseInfo(album)
                        buttonSaveArtist.visibility = View.VISIBLE
                    },
                    onFailure = {
                        Toast.makeText(
                            this,
                            getString(R.string.no_releases_found_for_artist, artist.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
            recyclerViewArtists.adapter = adapter
            recyclerViewArtists.layoutManager = LinearLayoutManager(this)
        } else {
            recyclerViewArtists.visibility = View.GONE
            Toast.makeText(this, getString(R.string.no_similar_artists_found), Toast.LENGTH_SHORT)
                .show()
        }
        setMenuButtonEnabled(true)
    }

    private fun openArtistDiscography(artist: DeezerArtist) {
        showLoading(true)

        val intent = Intent(this, ArtistDiscographyActivity::class.java).apply {
            putExtra("artistId", artist.id)
            putExtra("artistName", artist.name)
            putExtra("artistImageUrl", artist.getBestPictureUrl())
        }

        startActivity(intent)
        showLoading(false)
    }

    private fun displayReleaseInfo(album: DeezerAlbum) {
        artistInfoContainer.visibility = View.VISIBLE

        Log.d(TAG, "Displaying release info for artist: ${selectedArtist?.name}")

        textViewname.text = selectedArtist?.name ?: getString(R.string.unknown_artist)
        textViewAlbumTitle.text = album.title

        val formattedDate = try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val date = inputFormat.parse(album.release_date)
            date?.let { outputFormat.format(it) } ?: "Unknown Date"
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing release date: ${e.message}")
            "Unknown Date"
        }
        textViewrelease_date.text = formattedDate

        val coverUrl = album.getBestCoverUrl()

        progressBar.visibility = View.VISIBLE

        Glide.with(this)
            .load(coverUrl)
            .placeholder(R.drawable.ic_discography)
            .error(R.drawable.error_image)
            .transform(RoundedCorners(30))
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    imageViewAlbumArt.setImageDrawable(resource)
                    progressBar.visibility = View.GONE
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    progressBar.visibility = View.GONE
                }
            })

        updateSaveButton()

        artistInfoContainer.isClickable = true
        artistInfoContainer.isFocusable = true

        artistInfoContainer.setOnClickListener {

            val intent = Intent(this, ReleaseDetailsActivity::class.java)

            intent.putExtra("releaseId", album.id)
            intent.putExtra("releaseTitle", album.title)
            intent.putExtra("artistName", selectedArtist?.name ?: "Unknown Artist")
            intent.putExtra("albumArtUrl", album.getBestCoverUrl())

            startActivity(intent)
        }
    }




    private fun fetchArtistProfilePicture(artistId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val artistResponse = apiService.getArtistDetails(artistId).execute()
            if (artistResponse.isSuccessful) {
                val artist = artistResponse.body()
                artist?.let {
                    val profileImageUrl = it.getBestPictureUrl()
                    Log.d(TAG, "Fetched profile image URL: $profileImageUrl")
                }
            } else {
                Log.e(
                    TAG,
                    "Error fetching artist profile picture: ${artistResponse.code()} ${artistResponse.message()}"
                )
            }
        }
    }

    private fun updateSaveButton() {
        val artist = selectedArtist
        if (artist == null) {
            buttonSaveArtist.visibility = View.GONE
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val existingArtist = db.savedArtistDao().getArtistById(artist.id)
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
        val artist = selectedArtist ?: return
        val releaseDate = textViewrelease_date.text.toString()
        val profileImageUrl = artist.getBestPictureUrl()
        val releaseTitle = textViewAlbumTitle.text.toString()

        lifecycleScope.launch(Dispatchers.IO) {
            val existingArtist = db.savedArtistDao().getArtistById(artist.id)
            if (existingArtist == null) {
                db.savedArtistDao().insert(
                    SavedArtist(
                        id = artist.id,
                        name = artist.name,
                        lastReleaseDate = releaseDate,
                        lastReleaseTitle = releaseTitle,
                        profileImageUrl = profileImageUrl
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
            } else {
                db.savedArtistDao().delete(existingArtist)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.artist_removed, artist.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    updateSaveButton()
                }
            }
        }
    }

    private fun clearPreviousSearch() {
        editTextArtist.text.clear()
        buttonSaveArtist.visibility = View.GONE
        artistInfoContainer.visibility = View.GONE
        recyclerViewArtists.adapter = null
        recyclerViewArtists.visibility = View.GONE

    }
}