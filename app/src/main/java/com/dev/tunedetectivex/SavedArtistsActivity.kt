package com.dev.tunedetectivex

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dev.tunedetectivex.api.ITunesApiService
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit


class SavedArtistsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var spinnerViewType: Spinner
    private lateinit var db: AppDatabase
    private lateinit var apiService: DeezerApiService
    private lateinit var searchView: androidx.appcompat.widget.SearchView
    private var allArtists: List<SavedArtistItem> = emptyList()
    private lateinit var artistAdapter: SavedArtistAdapter
    private lateinit var releaseAdapter: ReleaseAdapter
    private var isNetworkRequestsAllowed = true
    private var isLoading = false
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var selectionBackCallback: OnBackPressedCallback


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_artists)

        recyclerView = findViewById(R.id.recyclerViewSavedArtists)
        spinnerViewType = findViewById(R.id.spinnerViewType)
        searchView = findViewById(R.id.searchViewArtists)
        loadingIndicator = findViewById(R.id.progressBar)

        db = AppDatabase.getDatabase(applicationContext)

        setupApiService()
        setupRecyclerView()
        setupSearchView()
        setupSpinner()

        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val isFirstRunSavedArtists = sharedPreferences.getBoolean("isFirstRunSavedArtists", true)

        if (isFirstRunSavedArtists) {
            showSavedArtistsTutorial()
            sharedPreferences.edit { putBoolean("isFirstRunSavedArtists", false) }
        }

        selectionBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                artistAdapter.clearSelection()
                isEnabled = false
            }
        }
        onBackPressedDispatcher.addCallback(this, selectionBackCallback)


    }

    private fun checkNetworkTypeAndSetFlag() {
        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(applicationContext)
        isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkPreference)
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.view_type_options,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerViewType.adapter = adapter

        spinnerViewType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (isLoading) return

                when (position) {
                    0 -> {
                        loadSavedArtists()
                        recyclerView.adapter = artistAdapter
                    }

                    1 -> {
                        loadSavedReleases()
                        recyclerView.adapter = releaseAdapter
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
    }


    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterArtists(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterArtists(newText)
                return true
            }
        })
    }


    private fun filterArtists(query: String?) {
        if (query.isNullOrBlank()) {
            updateArtistList(allArtists)
        } else {
            val filteredArtists = allArtists.filter { it.name.contains(query, ignoreCase = true) }
            updateArtistList(filteredArtists)
        }
    }


    private fun updateArtistList(artists: List<SavedArtistItem>) {
        val adapter = recyclerView.adapter as? SavedArtistAdapter ?: return
        val filteredArtists = artists.filter { it.name.isNotBlank() }
        adapter.submitList(filteredArtists)
    }


    private fun setupRecyclerView() {
        val actionButtonsContainer = findViewById<LinearLayout>(R.id.actionButtonsContainer)
        val ignoreBtn = findViewById<FloatingActionButton>(R.id.buttonIgnoreSelected)
        val deleteBtn = findViewById<FloatingActionButton>(R.id.buttonDeleteSelected)

        artistAdapter = SavedArtistAdapter(
            onDelete = { artist -> deleteArtistFromDb(artist) },
            onArtistClick = { artist -> openArtistDiscography(artist) },
            onToggleNotifications = { artistItem, newValue ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.savedArtistDao().setNotifyOnNewRelease(artistItem.id, newValue)
                }
            }
        ).apply {
            onSelectionChanged = { selectedItems ->
                val visible = selectedItems.isNotEmpty()
                actionButtonsContainer.visibility = if (visible) View.VISIBLE else View.GONE
                selectionBackCallback.isEnabled = visible
            }
        }

        ignoreBtn.setOnClickListener {
            val selectedItems = artistAdapter.currentList.filter { artistAdapter.isSelected(it.id) }
            lifecycleScope.launch(Dispatchers.IO) {
                selectedItems.forEach {
                    db.savedArtistDao().setNotifyOnNewRelease(it.id, false)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SavedArtistsActivity,
                        getString(R.string.notifications_disabled),
                        Toast.LENGTH_SHORT
                    ).show()
                    artistAdapter.clearSelection()
                    actionButtonsContainer.visibility = View.GONE
                    loadSavedArtists()
                }
            }
        }

        deleteBtn.setOnClickListener {
            val selectedItems = artistAdapter.currentList.filter { artistAdapter.isSelected(it.id) }
            lifecycleScope.launch(Dispatchers.IO) {
                selectedItems.forEach {
                    db.savedArtistDao().delete(
                        SavedArtist(
                            id = it.id,
                            name = it.name,
                            lastReleaseTitle = it.lastReleaseTitle,
                            lastReleaseDate = it.lastReleaseDate,
                            profileImageUrl = it.picture,
                            notifyOnNewRelease = it.notifyOnNewRelease
                        )
                    )
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SavedArtistsActivity,
                        getString(R.string.artists_deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                    artistAdapter.clearSelection()
                    actionButtonsContainer.visibility = View.GONE
                    loadSavedArtists()
                }
            }
        }

        releaseAdapter = ReleaseAdapter { release ->
            val intent = Intent(this, ReleaseDetailsActivity::class.java).apply {
                putExtra("releaseId", release.id)
                putExtra("releaseTitle", release.title)
                putExtra("artistName", release.artistName)
            }
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = artistAdapter
        enableSwipeToDelete()
    }


    private fun openArtistDiscography(artist: SavedArtistItem) {
        val intent = Intent(this, ArtistDiscographyActivity::class.java).apply {
            putExtra("artistName", artist.name)
            putExtra("artistImageUrl", artist.picture)
            putExtra("deezerId", artist.deezerId ?: -1L)
            putExtra("itunesId", artist.itunesId ?: -1L)
        }
        startActivity(intent)
    }

    private fun deleteArtistFromDb(artist: SavedArtistItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.savedArtistDao().delete(
                SavedArtist(
                    id = artist.id,
                    name = artist.name,
                    lastReleaseTitle = artist.lastReleaseTitle,
                    lastReleaseDate = artist.lastReleaseDate,
                    profileImageUrl = artist.picture,
                    notifyOnNewRelease = artist.notifyOnNewRelease
                )
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@SavedArtistsActivity,
                    getString(R.string.artist_deleted, artist.name),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private fun enableSwipeToDelete() {
        val swipeHandler = object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val selectedPosition = spinnerViewType.selectedItemPosition

                if (selectedPosition == 0) {
                    val adapter = recyclerView.adapter as SavedArtistAdapter
                    val position = viewHolder.bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val artist = adapter.currentList[position]
                        adapter.deleteItem(position)
                        deleteArtist(artist)
                    }
                } else {
                    recyclerView.adapter?.notifyItemChanged(viewHolder.bindingAdapterPosition)
                    Toast.makeText(
                        this@SavedArtistsActivity,
                        getString(R.string.deletion_only_in_artist_view),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val selectedPosition = spinnerViewType.selectedItemPosition
                return if (selectedPosition == 0) {
                    super.getSwipeDirs(recyclerView, viewHolder)
                } else {
                    0
                }
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun loadSavedArtists() {
        if (isLoading) return
        isLoading = true
        showLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val savedArtists = db.savedArtistDao().getAll().sortedBy { it.name.lowercase() }

                if (savedArtists.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        isLoading = false
                        Toast.makeText(
                            this@SavedArtistsActivity,
                            "No saved artists found.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val currentArtists = savedArtists.map { it.toItem() }

                withContext(Dispatchers.Main) {
                    allArtists = currentArtists

                    if (recyclerView.adapter != artistAdapter) {
                        recyclerView.adapter = artistAdapter
                    }
                    artistAdapter.submitList(allArtists)
                }
            } catch (e: Exception) {
                Log.e("SavedArtistsActivity", "Error with loadSavedArtists: ${e.message}", e)
            } finally {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    isLoading = false
                }
            }
        }
    }


    private fun SavedArtist.toItem(): SavedArtistItem {
        return SavedArtistItem(
            id = id,
            name = name,
            lastReleaseTitle = lastReleaseTitle,
            lastReleaseDate = lastReleaseDate,
            picture = profileImageUrl ?: "",
            deezerId = deezerId,
            itunesId = itunesId,
            notifyOnNewRelease = notifyOnNewRelease
        )
    }


    private fun normalizeTitle(title: String): String {
        return title
            .lowercase(Locale.getDefault())
            .replace(Regex("\\s*-\\s*(single|ep|album)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private fun isItunesSupportEnabled(): Boolean {
        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return prefs.getBoolean("itunesSupportEnabled", false)
    }

    private fun loadSavedReleases() {
        if (isLoading) return
        isLoading = true
        showLoading(true)

        checkNetworkTypeAndSetFlag()

        if (!isNetworkRequestsAllowed) {
            Toast.makeText(
                this,
                getString(R.string.network_type_not_available),
                Toast.LENGTH_SHORT
            ).show()
            showLoading(false)
            isLoading = false
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val savedArtists = db.savedArtistDao().getAll()

            if (savedArtists.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SavedArtistsActivity,
                        getString(R.string.no_saved_artists),
                        Toast.LENGTH_SHORT
                    ).show()
                    showLoading(false)
                    isLoading = false
                }
                return@launch
            }

            val releaseItems = coroutineScope {
                savedArtists.map { artist ->
                    async { fetchReleasesForArtist(artist) }
                }.awaitAll().flatten()
            }
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val grouped = releaseItems.groupBy {
                "${normalizeTitle(it.title)}|${it.releaseDate.take(10)}"
            }

            val deduplicated = grouped.mapNotNull { (_, group) ->
                group.maxWithOrNull { a, b ->
                    val dateA = try {
                        format.parse(a.releaseDate)
                    } catch (_: Exception) {
                        null
                    }
                    val dateB = try {
                        format.parse(b.releaseDate)
                    } catch (_: Exception) {
                        null
                    }

                    val cmpDate = when {
                        dateA == null && dateB == null -> 0
                        dateA == null -> -1
                        dateB == null -> 1
                        else -> dateA.compareTo(dateB)
                    }

                    if (cmpDate != 0) cmpDate * -1 else {
                        val scoreA =
                            (if (a.albumArtUrl.isNotBlank()) 1 else 0) + (if (!a.artistImageUrl.isNullOrBlank()) 1 else 0)
                        val scoreB =
                            (if (b.albumArtUrl.isNotBlank()) 1 else 0) + (if (!b.artistImageUrl.isNullOrBlank()) 1 else 0)

                        scoreA.compareTo(scoreB)
                    }
                }
            }

            val sorted = deduplicated.sortedByDescending {
                try {
                    format.parse(it.releaseDate)?.time
                } catch (_: Exception) {
                    null
                }
            }

            withContext(Dispatchers.Main) {
                val adapter = ReleaseAdapter { release ->
                    val actualApiSource = when {
                        release.deezerId != null && release.deezerId > 0 -> "Deezer"
                        release.itunesId != null && release.itunesId > 0 && isItunesSupportEnabled() -> "iTunes"
                        else -> {
                            Log.e("SavedArtistsActivity", "No valid Deezer/iTunes ID or iTunes support for release: ${release.title}. Cannot determine API source.")
                            "Unknown"
                        }
                    }

                    Log.d("SavedArtistsActivity", "Tapped Release: " +
                            "ID=${release.id}, " +
                            "Title=${release.title}, " +
                            "DeezerID=${release.deezerId}, " +
                            "iTunesID=${release.itunesId}, " +
                            "API_Source_from_ReleaseObject=${release.apiSource}, " +
                            "Determined_API_Source=${actualApiSource}")

                    val intent = Intent(
                        this@SavedArtistsActivity,
                        ReleaseDetailsActivity::class.java
                    ).apply {
                        putExtra("releaseId", when(actualApiSource) {
                            "Deezer" -> release.deezerId ?: -1L
                            "iTunes" -> release.itunesId ?: -1L
                            else -> -1L
                        })
                        putExtra("releaseTitle", release.title)
                        putExtra("artistName", release.artistName)
                        putExtra("albumArtUrl", release.albumArtUrl)
                        putExtra("apiSource", actualApiSource)
                        putExtra("deezerId", release.deezerId ?: -1L)
                        putExtra("itunesId", release.itunesId ?: -1L)
                    }
                    startActivity(intent)
                }

                val prettifiedList = sorted.map { release ->
                    val rawTitle = release.title
                    val cleanedTitle = rawTitle.replace(
                        Regex(
                            "\\s*-\\s*(Single|EP|Album)",
                            RegexOption.IGNORE_CASE
                        ), ""
                    ).trim()
                    val typeMatch = Regex("(?i)\\b(Single|EP|Album)\\b").find(rawTitle)
                    val type = typeMatch?.value?.replaceFirstChar { it.uppercaseChar() }

                    release.copy(title = if (type != null) "$cleanedTitle ($type)" else cleanedTitle)
                }

                recyclerView.adapter = adapter
                adapter.submitList(prettifiedList)
                recyclerView.visibility =
                    if (prettifiedList.isNotEmpty()) View.VISIBLE else View.GONE
                recyclerView.scrollToPosition(0)

                val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
                val isFirstRunReleases = sharedPreferences.getBoolean("isFirstRunReleases", true)
                if (isFirstRunReleases) {
                    showReleasesTutorial()
                    sharedPreferences.edit { putBoolean("isFirstRunReleases", false) }
                }

                showLoading(false)
                isLoading = false
            }
        }
    }

    private fun fetchReleasesForArtist(artist: SavedArtist): List<ReleaseItem> {
        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(applicationContext)
        val isNetworkAvailable = WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkPreference)

        if (!isNetworkAvailable) {
            Log.w("SavedArtistsActivity", "🚫 Network not available – skipping ${artist.name}")
            return emptyList()
        }

        val releases = mutableListOf<ReleaseItem>()
        val artistImage = artist.profileImageUrl ?: ""

        try {
            if (artist.deezerId != null) {
                val deezerResponse =
                    apiService.getArtistReleases(artist.deezerId, 0).execute()
                if (deezerResponse.isSuccessful) {
                    val deezerReleases = deezerResponse.body()?.data.orEmpty().map { release ->
                        val uniqueId = "Deezer_${release.id}".hashCode().toLong()
                        ReleaseItem(
                            id = uniqueId,
                            title = release.title,
                            artistName = artist.name,
                            albumArtUrl = release.getBestCoverUrl().takeIf { it.isNotBlank() } ?: "",
                            releaseDate = release.release_date,
                            apiSource = "Deezer",
                            deezerId = release.id,
                            artistImageUrl = artistImage,
                            releaseType = release.record_type?.replaceFirstChar { it.uppercaseChar() }
                        )
                    }
                    releases.addAll(deezerReleases)
                } else {
                    Log.e("SavedArtistsActivity", "❌ Deezer API error for ${artist.name}: ${deezerResponse.code()} - ${deezerResponse.message()}")
                }
            } else {
                Log.d("SavedArtistsActivity", "⏩ Skipping Deezer release fetch for ${artist.name} - Deezer ID is null.")
            }


            if (artist.itunesId != null) {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://itunes.apple.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val iTunesService = retrofit.create(ITunesApiService::class.java)

                val iTunesResponse = iTunesService.lookupArtistWithAlbums(artist.itunesId).execute()
                if (iTunesResponse.isSuccessful) {
                    val iTunesReleases = iTunesResponse.body()?.results?.filter { it.collectionName != null }?.map { release ->
                        val uniqueId = "iTunes_${release.collectionId}".hashCode().toLong()
                        ReleaseItem(
                            id = uniqueId,
                            title = release.collectionName ?: "",
                            artistName = release.artistName ?: artist.name,
                            albumArtUrl = release.artworkUrl100?.replace("100x100bb.jpg", "600x600bb.jpg") ?: "",
                            releaseDate = release.releaseDate ?: "",
                            apiSource = "iTunes",
                            itunesId = release.collectionId,
                            artistImageUrl = artistImage,
                            releaseType = null
                        )
                    }.orEmpty()
                    releases.addAll(iTunesReleases)
                } else {
                    Log.e("SavedArtistsActivity", "❌ iTunes API error for ${artist.name}: ${iTunesResponse.code()} - ${iTunesResponse.message()}")
                }
            } else {
                Log.d("SavedArtistsActivity", "⏩ Skipping iTunes release fetch for ${artist.name} - iTunes ID is null.")
            }
        } catch (e: Exception) {
            Log.e(
                "SavedArtistsActivity",
                "❌ Error fetching releases for ${artist.name}: ${e.message}",
                e
            )
        }
        return releases
    }

    private fun setupApiService() {
        val cacheSize = (5 * 1024 * 1024).toLong()
        val cache = Cache(applicationContext.cacheDir, cacheSize)

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .cache(cache)
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = 100
                }
            )
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(DeezerApiService::class.java)
    }

    private fun deleteArtist(artistItem: SavedArtistItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.savedArtistDao().delete(
                SavedArtist(
                    id = artistItem.id,
                    name = artistItem.name,
                    lastReleaseTitle = artistItem.lastReleaseTitle,
                    lastReleaseDate = artistItem.lastReleaseDate,
                    profileImageUrl = artistItem.picture
                )
            )
        }
    }

    private fun showSavedArtistsTutorial() {
        TapTargetSequence(this)
            .targets(
                TapTarget.forView(
                    spinnerViewType,
                    getString(R.string.select_view_title),
                    getString(R.string.select_view_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    recyclerView,
                    getString(R.string.saved_artists_title),
                    getString(R.string.saved_artists_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    recyclerView,
                    getString(R.string.artist_picture_title),
                    getString(R.string.artist_picture_description)
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    recyclerView,
                    getString(R.string.newest_releases_title),
                    getString(R.string.newest_releases_description)
                ).transparentTarget(true).cancelable(false)
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    Toast.makeText(
                        this@SavedArtistsActivity,
                        getString(R.string.tutorial_completed_artists),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onSequenceStep(tapTarget: TapTarget, targetClicked: Boolean) {
                }

                override fun onSequenceCanceled(tapTarget: TapTarget?) {
                    Toast.makeText(
                        this@SavedArtistsActivity,
                        getString(R.string.tutorial_canceled_artists),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
            .start()
    }

    private fun showReleasesTutorial() {
        TapTargetSequence(this)
            .targets(
                TapTarget.forView(
                    recyclerView,
                    getString(R.string.latest_releases_title),
                    getString(R.string.latest_releases_description)
                ).transparentTarget(true).cancelable(false)
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    Toast.makeText(
                        this@SavedArtistsActivity,
                        getString(R.string.tutorial_completed_releases),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onSequenceStep(tapTarget: TapTarget, targetClicked: Boolean) {
                }

                override fun onSequenceCanceled(tapTarget: TapTarget?) {
                    Toast.makeText(
                        this@SavedArtistsActivity,
                        getString(R.string.tutorial_aborted_releases),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
            .start()
    }

    private fun showLoading(isLoading: Boolean) {
        loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
    }
}