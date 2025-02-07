package com.dev.tunedetectivex

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
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
import java.util.concurrent.TimeUnit


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
    private lateinit var artistInfoCard: MaterialCardView
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var isFabMenuOpen = false
    private lateinit var notificationManager: NotificationManagerCompat
    private val fetchedArtists = mutableSetOf<Long>()

    @SuppressLint("ObsoleteSdkInt")
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

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        recyclerViewArtists = findViewById(R.id.recyclerViewArtists)
        recyclerViewArtists.layoutManager = LinearLayoutManager(this)
        editTextArtist = findViewById(R.id.editTextArtist)
        buttonSaveArtist = findViewById(R.id.buttonSaveArtist)
        artistInfoCard = findViewById(R.id.artistInfoCard)
        textViewname = findViewById(R.id.textViewname)
        textViewAlbumTitle = findViewById(R.id.textViewAlbumTitle)
        textViewrelease_date = findViewById(R.id.textViewrelease_date)
        imageViewAlbumArt = findViewById(R.id.imageViewAlbumArt)
        progressIndicator = findViewById(R.id.progressBarLoading)
        notificationManager = NotificationManagerCompat.from(this)

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

        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val isFirstRun = sharedPreferences.getBoolean("isFirstRun", true)

        if (isFirstRun) {
            showTutorial()
            sharedPreferences.edit().putBoolean("isFirstRun", false).apply()
        }

        pushNotificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Notification permission granted")
            } else {
                Log.w(TAG, "Notification permission denied")
            }
        }

        requestNotificationPermission()
        requestIgnoreBatteryOptimizations()
        setupFetchReleasesWorker()
        updateSaveButton()
        clearPreviousSearch()
        setupApiService()

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

        swipeRefreshLayout.setOnRefreshListener {
            val searchQuery = editTextArtist.text.toString().trim()
            if (searchQuery.isNotEmpty()) {
                fetchSimilarArtists(searchQuery)
            } else {
                swipeRefreshLayout.isRefreshing = false
            }

            if (selectedArtist != null) {
                updateSaveButton()
            }
        }

        fun scheduleBackgroundJob(context: Context) {
            val sharedPreferences = context.getSharedPreferences("TaskLog", MODE_PRIVATE)

            val intervalInMinutes = sharedPreferences.getInt("fetchInterval", 90)
            val intervalInMillis = intervalInMinutes * 60 * 1000L

            val nextTriggerTime = SystemClock.elapsedRealtime() + intervalInMillis
            System.currentTimeMillis() + intervalInMillis

            val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BackgroundJobReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                nextTriggerTime,
                intervalInMillis,
                pendingIntent
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }

        db = AppDatabase.getDatabase(applicationContext)
        scheduleBackgroundJob(this)

        editTextArtist.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        editTextArtist.setSingleLine()
        editTextArtist.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val artistName = editTextArtist.text.toString().trim()
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
            if (hasFocus) {
                val inputMethodManager =
                    getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        buttonSaveArtist.setOnClickListener {
            saveArtist()
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
                val response = apiService.getArtistReleases(artistId, limit = 100).execute()
                if (response.isSuccessful) {
                    val releases = response.body()?.data
                    if (!releases.isNullOrEmpty()) {
                        val match = releases.firstOrNull { it.title.equals(title, ignoreCase = true) }
                        if (match != null) {
                            Log.d("MainActivity", "Match found: ${match.title} in discography")
                        } else {
                            Log.d("MainActivity", "No matching release found for title: $title")
                        }
                    } else {
                        Log.d("MainActivity", "No releases found for artist ID: $artistId")
                    }
                } else {
                    Log.e("MainActivity", "Deezer API error: ${response.code()} ${response.message()}")
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

        if (isFabMenuOpen) {
            fabSavedArtists.animate().translationY(0f).alpha(0f).setDuration(200).start()
            fabSettings.animate().translationY(0f).alpha(0f).setDuration(200).start()
            fabSelectFolder.animate().translationY(0f).alpha(0f).setDuration(200).start()
            fabMenu.setImageResource(R.drawable.ic_menu)
            Handler(Looper.getMainLooper()).postDelayed({
                fabSavedArtists.visibility = View.GONE
                fabSettings.visibility = View.GONE
                fabSelectFolder.visibility = View.GONE
            }, 200)
        } else {
            fabSavedArtists.visibility = View.VISIBLE
            fabSettings.visibility = View.VISIBLE
            fabSelectFolder.visibility = View.VISIBLE
            fabSavedArtists.animate().translationY(-80f).alpha(1f).setDuration(200).start()
            fabSettings.animate().translationY(-160f).alpha(1f).setDuration(200).start()
            fabSelectFolder.animate().translationY(-240f).alpha(1f).setDuration(200).start()
            fabMenu.setImageResource(R.drawable.ic_close)
        }
        isFabMenuOpen = !isFabMenuOpen
    }

    private fun showTutorial() {
        val fabMenu: FloatingActionButton = findViewById(R.id.fabMenu)
        val fabSavedArtists: FloatingActionButton = findViewById(R.id.fabSavedArtists)
        val fabSettings: FloatingActionButton = findViewById(R.id.fabSettings)

        fabSavedArtists.visibility = View.VISIBLE
        fabSavedArtists.translationY = 0f
        fabSavedArtists.alpha = 1f

        fabSettings.visibility = View.VISIBLE
        fabSettings.translationY = 0f
        fabSettings.alpha = 1f

        fabMenu.visibility = View.VISIBLE
        fabMenu.alpha = 1f

        TapTargetSequence(this)
            .targets(
                TapTarget.forView(
                    fabMenu,
                    "Main menu",
                    "Tap here to open the menu and see more options."
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    fabSavedArtists,
                    "Saved artists",
                    "Tap here to view your saved artists, including their latest releases."
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    fabSettings,
                    "Settings",
                    "Tap here to manage the app settings such as notification intervals, and so on"
                ).transparentTarget(true).cancelable(false)
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    Toast.makeText(
                        this@MainActivity,
                        "Tutorial completed! Have lots of fun with the app.",
                        Toast.LENGTH_SHORT
                    ).show()

                    fabSavedArtists.visibility = View.GONE
                    fabSettings.visibility = View.GONE
                    fabSavedArtists.translationY = 0f
                    fabSettings.translationY = 0f
                }

                override fun onSequenceStep(tapTarget: TapTarget, targetClicked: Boolean) {
                }

                override fun onSequenceCanceled(tapTarget: TapTarget?) {
                    Toast.makeText(
                        this@MainActivity,
                        "Tutorial aborted.",
                        Toast.LENGTH_SHORT
                    ).show()

                    fabSavedArtists.visibility = View.GONE
                    fabSettings.visibility = View.GONE
                    fabSavedArtists.translationY = 0f
                    fabSettings.translationY = 0f
                }
            })
            .start()
    }

    private fun showLoading(isLoading: Boolean) {
        progressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun setupFetchReleasesWorker() {
        val sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val intervalInMinutes = sharedPreferences.getInt("fetchInterval", 90).toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = PeriodicWorkRequestBuilder<FetchReleasesWorker>(
            intervalInMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "FetchReleasesWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
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
        showLoading(true)
        setMenuButtonEnabled(false)
        swipeRefreshLayout.isRefreshing = true

        buttonSaveArtist.visibility = View.GONE
        artistInfoCard.visibility = View.GONE

        apiService.searchArtist(artist).enqueue(object : Callback<DeezerSimilarArtistsResponse> {
            override fun onResponse(
                call: Call<DeezerSimilarArtistsResponse>,
                response: Response<DeezerSimilarArtistsResponse>
            ) {
                swipeRefreshLayout.isRefreshing = false
                val artists = response.body()?.data ?: emptyList()

                if (artists.isNotEmpty()) {
                    displayArtists(artists)
                } else {
                    showLoading(false)
                    setMenuButtonEnabled(true)
                }
            }

            override fun onFailure(call: Call<DeezerSimilarArtistsResponse>, t: Throwable) {
                swipeRefreshLayout.isRefreshing = false
                showLoading(false)
                setMenuButtonEnabled(true)
                Toast.makeText(this@MainActivity, "Error loading artists.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun processSimilarArtists(artists: List<DeezerArtist>, index: Int) {
        if (index >= artists.size) {
            showLoading(false)
            setMenuButtonEnabled(true)
            return
        }

        val currentArtist = artists[index]
        fetchLatestReleaseForArtist(
            artistId = currentArtist.id,
            onSuccess = { album ->
                fetchAndCheckDiscography(currentArtist.id, album.title)
                displayReleaseInfo(album)
            },
            onFailure = {
                processSimilarArtists(artists, index + 1)
            }
        )
    }

    private fun fetchLatestReleaseForArtist(
        artistId: Long,
        onSuccess: (DeezerAlbum) -> Unit,
        onFailure: () -> Unit
    ) {
        apiService.getArtistReleases(artistId, limit = 100).enqueue(object : Callback<DeezerAlbumsResponse> {
            override fun onResponse(call: Call<DeezerAlbumsResponse>, response: Response<DeezerAlbumsResponse>) {
                if (response.isSuccessful) {
                    val releases = response.body()?.data ?: emptyList()
                    if (releases.isNotEmpty()) {
                        val latestRelease = releases.maxByOrNull { release ->
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(release.release_date)?.time ?: 0L
                        }
                        latestRelease?.let {
                            fetchAndCheckDiscography(artistId, it.title)
                            onSuccess(it)
                        } ?: onFailure()
                    } else {
                        Log.d(TAG, "No Releases found for Artist ID: $artistId")
                        onFailure()
                    }
                } else {
                    Log.e(TAG, "Error when retrieving releases for Artist ID=$artistId: ${response.code()} ${response.message()}")
                    onFailure()
                }
            }

            override fun onFailure(call: Call<DeezerAlbumsResponse>, t: Throwable) {
                Log.e(TAG, "Error when retrieving releases for Artist ID=$artistId: ${t.message}", t)
                onFailure()
            }
        })
    }

    private fun displayArtists(artists: List<DeezerArtist>) {
        if (artists.isNotEmpty()) {
            recyclerViewArtists.visibility = View.VISIBLE
            val adapter = SimilarArtistsAdapter(this, artists) { artist ->
                selectedArtist = artist
                recyclerViewArtists.visibility = View.GONE
                fetchLatestReleaseForArtist(
                    artistId = artist.id,
                    onSuccess = { album ->
                        fetchAndCheckDiscography(artist.id, album.title)
                        displayReleaseInfo(album)
                    },
                    onFailure = {
                        Toast.makeText(this, "No releases found for ${artist.name}.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            recyclerViewArtists.adapter = adapter
            showLoading(false)
        } else {
            recyclerViewArtists.visibility = View.GONE
            showLoading(false)
            Toast.makeText(this, "No similar artists found.", Toast.LENGTH_SHORT).show()
        }
        setMenuButtonEnabled(true)
    }


    private fun displayReleaseInfo(album: DeezerAlbum) {
        artistInfoCard.visibility = View.VISIBLE
        textViewname.text = selectedArtist?.name ?: "Unknown Artist"
        textViewAlbumTitle.text = album.title

        val formattedDate = try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val date = inputFormat.parse(album.release_date)
            date?.let { outputFormat.format(it) }
        } catch (_: Exception) {
            album.release_date
        }
        textViewrelease_date.text = formattedDate

        val coverUrl = album.cover_xl
        Glide.with(this)
            .load(coverUrl)
            .apply(RequestOptions().transform(RoundedCorners(40)))
            .into(imageViewAlbumArt)

        updateSaveButton()
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
                        "${artist.name} saved!",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateSaveButton()
                }
            } else {
                db.savedArtistDao().delete(existingArtist)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "${artist.name} removed from saved artists!",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateSaveButton()
                }
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.fab_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun clearPreviousSearch() {
        editTextArtist.text.clear()
        buttonSaveArtist.visibility = View.GONE
        artistInfoCard.visibility = View.GONE
        recyclerViewArtists.adapter = null
        recyclerViewArtists.visibility = View.GONE

    }
}