package com.dev.tunedetectivex

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
import com.dev.tunedetectivex.util.ItunesResultDialogHelper
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit


@Suppress("DEPRECATION")
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

        lifecycleScope.launch {
            val selectedPosition = spinnerViewType.selectedItemPosition
            if (selectedPosition == 0) {
                val prefs =
                    applicationContext.getSharedPreferences("AppPreferences", MODE_PRIVATE)
                val isItunesEnabled =
                    prefs.getBoolean("itunesSupportEnabled", false)
                getItunesAttemptCount()

                if (isItunesEnabled) {
                    val jobs = allArtists.map { artist ->
                        launch {
                            fetchArtistDetails(artist)
                        }
                    }
                    jobs.joinAll()
                } else {
                    Log.d(
                        "SavedArtistsActivity",
                        "🔕 iTunes matching disabled - Details update skipped."
                    )
                }
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (::artistAdapter.isInitialized && artistAdapter.isInSelectionMode()) {
                        artistAdapter.clearSelection()
                    } else {
                        finish()
                    }
                }
            }
        )


    }

    private fun getItunesAttemptCount(): Int {
        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return prefs.getInt("itunesMatchingAttempts", 0)
    }

    private fun incrementItunesAttemptCount() {
        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val current = prefs.getInt("itunesMatchingAttempts", 0)
        prefs.edit { putInt("itunesMatchingAttempts", current + 1) }
    }


    private fun checkNetworkTypeAndSetFlag() {
        val sharedPreferences =
            applicationContext.getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any") ?: "Any"
        isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkType)

        if (!isNetworkRequestsAllowed) {
            Toast.makeText(
                this,
                getString(R.string.network_type_not_available),
                Toast.LENGTH_SHORT
            ).show()
        }
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
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
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
                ignoreBtn.visibility = if (visible) View.VISIBLE else View.GONE
                deleteBtn.visibility = if (visible) View.VISIBLE else View.GONE
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
                        "Benachrichtigungen deaktiviert",
                        Toast.LENGTH_SHORT
                    ).show()
                    artistAdapter.clearSelection()
                    ignoreBtn.visibility = View.GONE
                    deleteBtn.visibility = View.GONE
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
                        "Künstler gelöscht",
                        Toast.LENGTH_SHORT
                    ).show()
                    artistAdapter.clearSelection()
                    ignoreBtn.visibility = View.GONE
                    deleteBtn.visibility = View.GONE
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
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
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
                    val position = viewHolder.adapterPosition
                    val artist = adapter.currentList[position]
                    adapter.deleteItem(position)
                    deleteArtist(artist)
                } else {
                    recyclerView.adapter?.notifyItemChanged(viewHolder.adapterPosition)
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

        checkNetworkTypeAndSetFlag()

        if (!isNetworkRequestsAllowed) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT)
                .show()
            showLoading(false)
            isLoading = false
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
                val itunesSupportEnabled =
                    sharedPreferences.getBoolean("itunesSupportEnabled", false)
                val attemptCount = getItunesAttemptCount()
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

                val updatedList = mutableListOf<SavedArtistItem>()
                var deezerUpdated = 0
                var itunesUpdated = 0

                for (artist in savedArtists) {
                    val updated = if (itunesSupportEnabled && attemptCount < 3) {
                        fetchArtistDetailsByDiscography(artist) ?: artist
                    } else {
                        Log.d("SavedArtistsActivity", "⏭️ iTunes matching disabled - skip $artist")
                        artist
                    }

                    if (artist.deezerId == null && updated.deezerId != null) deezerUpdated++
                    if (artist.itunesId == null && updated.itunesId != null) itunesUpdated++

                    updatedList.add(updated.toItem())
                }

                if (itunesSupportEnabled && attemptCount >= 3) {
                    sharedPreferences.edit { putBoolean("itunesSupportEnabled", true) }
                }

                if (itunesSupportEnabled && attemptCount < 3) {
                    incrementItunesAttemptCount()
                }

                val notMatched = updatedList.filter { it.itunesId == null }

                withContext(Dispatchers.Main) {
                    allArtists = updatedList

                    if (recyclerView.adapter != artistAdapter) {
                        recyclerView.adapter = artistAdapter
                    }

                    artistAdapter.submitList(allArtists)

                    Log.d("SavedArtistsActivity", "🔄 Auto-Fix completed:")
                    Log.d("SavedArtistsActivity", "🟢 Deezer-IDs added: $deezerUpdated")
                    Log.d("SavedArtistsActivity", "🔵 iTunes-IDs added: $itunesUpdated")

                    if (itunesSupportEnabled) {
                        when (attemptCount) {
                            0 -> Toast.makeText(
                                this@SavedArtistsActivity,
                                getString(R.string.itunes_attempt_1),
                                Toast.LENGTH_SHORT
                            ).show()

                            1 -> Toast.makeText(
                                this@SavedArtistsActivity,
                                getString(R.string.itunes_attempt_2),
                                Toast.LENGTH_SHORT
                            ).show()

                            2 -> Toast.makeText(
                                this@SavedArtistsActivity,
                                getString(R.string.itunes_attempt_3),
                                Toast.LENGTH_SHORT
                            ).show()

                            3 -> Toast.makeText(
                                this@SavedArtistsActivity,
                                getString(R.string.itunes_attempt_final),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        ItunesResultDialogHelper.showResultSummaryDialog(
                            context = this@SavedArtistsActivity,
                            deezerUpdated = deezerUpdated,
                            itunesUpdated = itunesUpdated,
                            notMatchedArtists = notMatched,
                            coroutineScope = lifecycleScope,
                            onDeleteArtist = { artist -> deleteArtist(artist) },
                            onRescanArtist = { artist, _ ->
                                val updated = lifecycleScope.async(Dispatchers.IO) {
                                    db.savedArtistDao().getArtistById(artist.id)
                                        ?.let { fetchArtistDetailsByDiscography(it) }
                                }
                                updated.await()?.let {
                                    val newList = allArtists.toMutableList()
                                    newList.replaceFirstOrAdd(it.toItem())
                                    allArtists = newList
                                    artistAdapter.submitList(newList)
                                }
                            },
                            onManualSelect = { artist, selectedId ->
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        db.savedArtistDao().updateItunesId(artist.id, selectedId)
                                    }
                                    val newList = allArtists.toMutableList()
                                    newList.replaceFirstOrAdd(artist.copy(itunesId = selectedId))
                                    allArtists = newList
                                    artistAdapter.submitList(newList)
                                    Toast.makeText(
                                        this@SavedArtistsActivity,
                                        getString(R.string.artist_updated, artist.name),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
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


    private suspend fun fetchArtistDetailsByDiscography(artist: SavedArtist): SavedArtist? {
        val prefs = applicationContext.getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val itunesSupportEnabled = prefs.getBoolean("itunesSupportEnabled", false)

        if (itunesSupportEnabled) {
            Log.d(
                "SavedArtistsActivity",
                "🚫 iTunes matching deactivated - iTunes is skipped completely."
            )
            return artist
        }


        val searchName = artist.name
        val context = applicationContext

        val isNetworkOk = WorkManagerUtil.isSelectedNetworkTypeAvailable(
            context,
            prefs.getString("networkType", "Any") ?: "Any"
        )
        if (!isNetworkOk) return artist

        return try {
            var updated = artist

            val deezerSearchResults =
                apiService.searchArtist(searchName).execute().body()?.data.orEmpty()
            val deezerMatch = deezerSearchResults.firstOrNull { it.name == searchName }
            val deezerId = deezerMatch?.id ?: -1L

            val deezerTitles = if (deezerId > 0) {
                val releases =
                    apiService.getArtistReleases(deezerId, 0).execute().body()?.data.orEmpty()
                releases.map { normalizeTitle(it.title) }.toSet()
            } else emptySet()

            var iTunesId: Long? = null
            var itunesTitles: Set<String> = emptySet()

            if (itunesSupportEnabled) {
                Log.d("SavedArtistsActivity", "🔍 Start iTunes matching for $searchName")
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://itunes.apple.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val iTunesService = retrofit.create(ITunesApiService::class.java)

                val iTunesSearchResults =
                    iTunesService.searchArtist(term = searchName, entity = "musicArtist")
                        .execute().body()?.results.orEmpty()
                val iTunesMatch = iTunesSearchResults.firstOrNull { it.artistName == searchName }
                iTunesId = iTunesMatch?.artistId

                itunesTitles = if (iTunesId != null) {
                    val releases = iTunesService.lookupArtistWithAlbums(iTunesId).execute()
                        .body()?.results.orEmpty()
                    releases.filter { it.collectionName != null }
                        .map { normalizeTitle(it.collectionName!!) }
                        .toSet()
                } else emptySet()
            } else {
                Log.d(
                    "SavedArtistsActivity",
                    "🚫 iTunes matching deactivated - iTunes is skipped completely."
                )
            }

            val common = deezerTitles.intersect(itunesTitles)

            Log.d(
                "SavedArtistsActivity",
                "📀 Deezer-Title: ${deezerTitles.size}, iTunes-Title: ${itunesTitles.size}, Combined: ${common.size}"
            )

            if (deezerId > 0 && updated.deezerId == null) {
                db.savedArtistDao().updateDeezerId(updated.id, deezerId)
                updated = updated.copy(deezerId = deezerId)
                Log.d("SavedArtistsActivity", "✅ Deezer-ID saved: $deezerId")
            }

            if (iTunesId != null && common.size >= 3 && updated.itunesId == null) {
                db.savedArtistDao().updateItunesId(updated.id, iTunesId)
                updated = updated.copy(itunesId = iTunesId)
                Log.d(
                    "SavedArtistsActivity",
                    "✅ iTunes-ID saved: $iTunesId (⟶ ${common.size} unified Releases)"
                )
            }

            return updated
        } catch (e: Exception) {
            Log.e(
                "SavedArtistsActivity",
                "❌ Error with fetchArtistDetailsByDiscography: ${e.message}",
                e
            )
            artist
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


    private fun MutableList<SavedArtistItem>.replaceFirstOrAdd(newItem: SavedArtistItem) {
        val index = indexOfFirst { it.id == newItem.id }
        if (index != -1) {
            this[index] = newItem
        } else {
            add(newItem)
        }
    }

    private suspend fun fetchArtistDetails(
        artist: SavedArtistItem,
        customName: String? = null
    ): SavedArtistItem? {
        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val itunesSupportEnabled = !prefs.getBoolean("itunesSupportEnabled", false)

        val isNetworkRequestsAllowed = WorkManagerUtil.isSelectedNetworkTypeAvailable(
            applicationContext,
            prefs.getString("networkType", "Any") ?: "Any"
        )
        if (!isNetworkRequestsAllowed) return artist

        if (itunesSupportEnabled) {
            Log.d(
                "SavedArtistsActivity",
                "🚫 iTunes matching disabled - fetchArtistDetails() skips iTunes completely."
            )
            return artist
        }

        return try {
            var updated = artist
            val searchName = customName ?: artist.name

            val deezerId = artist.deezerId ?: run {
                val deezerResults =
                    apiService.searchArtist(searchName).execute().body()?.data.orEmpty()
                val deezerMatch = deezerResults.firstOrNull { it.name == searchName }
                deezerMatch?.id?.also {
                    db.savedArtistDao().updateDeezerId(artist.id, it)
                    updated = updated.copy(deezerId = it)
                }
            } ?: return artist

            val deezerTitles = apiService.getArtistReleases(deezerId, 0)
                .execute().body()?.data.orEmpty().map { normalizeTitle(it.title) }.toSet()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://itunes.apple.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val iTunesService = retrofit.create(ITunesApiService::class.java)

            val iTunesResults = iTunesService
                .searchArtist(term = searchName, entity = "musicArtist")
                .execute().body()?.results.orEmpty()
            val exactMatch = iTunesResults.firstOrNull { it.artistName == searchName }
            val iTunesId = exactMatch?.artistId

            val iTunesTitles = if (iTunesId != null) {
                iTunesService.lookupArtistWithAlbums(iTunesId).execute().body()?.results
                    ?.filter { it.collectionName != null }
                    ?.map { normalizeTitle(it.collectionName!!) }
                    ?.toSet()
            } else emptySet()

            val common = deezerTitles.intersect(iTunesTitles ?: emptySet())

            if (common.size >= 3 && iTunesId != null) {
                db.savedArtistDao().updateItunesId(artist.id, iTunesId)
                updated = updated.copy(itunesId = iTunesId)
                Log.d(
                    "SavedArtistsActivity",
                    "🎯 iTunes-ID for '${artist.name}' set: $iTunesId (${common.size} Matches)"
                )
            } else {
                Log.d(
                    "SavedArtistsActivity",
                    "❌ No iTunes match for '${artist.name}' (${common.size} combined titles)"
                )
            }

            return updated
        } catch (e: Exception) {
            Log.e("SavedArtistsActivity", "Error with fetchArtistDetails(): ${e.message}", e)
            artist
        }
    }


    fun normalizeTitle(title: String): String {
        return title.lowercase(Locale.getDefault())
            .replace(Regex("\\s*-\\s*(single|ep|album)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
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

            val uniqueReleases = releaseItems.distinctBy {
                val normTitle = normalizeTitle(it.title)
                val shortDate = it.releaseDate.take(10)
                "$normTitle|$shortDate"
            }

            val sortedReleaseItems = uniqueReleases.sortedByDescending {
                try {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.releaseDate)?.time
                } catch (e: Exception) {
                    null
                }
            }

            withContext(Dispatchers.Main) {
                val adapter = ReleaseAdapter { release ->
                    val intent = Intent(
                        this@SavedArtistsActivity,
                        ReleaseDetailsActivity::class.java
                    ).apply {
                        putExtra("releaseId", release.id)
                        putExtra("releaseTitle", release.title)
                        putExtra("artistName", release.artistName)
                        putExtra("albumArtUrl", release.albumArtUrl)
                        putExtra("deezerId", release.deezerId ?: -1L)
                        putExtra("itunesId", release.itunesId ?: -1L)
                        putExtra("apiSource", release.apiSource)
                    }
                    startActivity(intent)
                }

                recyclerView.adapter = adapter
                adapter.submitList(sortedReleaseItems)
                recyclerView.visibility = if (sortedReleaseItems.isNotEmpty()) View.VISIBLE else View.GONE
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

    private suspend fun fetchReleasesForArtist(artist: SavedArtist): List<ReleaseItem> {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any") ?: "Any"
        val isNetworkAvailable =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkType)

        if (!isNetworkAvailable) {
            Log.w("SavedArtistsActivity", "🚫 Network not available – skipping ${artist.name}")
            return emptyList()
        }

        val releases = mutableListOf<ReleaseItem>()
        val artistImage = artist.profileImageUrl ?: ""

        try {
            val deezerResponse =
                apiService.getArtistReleases(artist.deezerId ?: artist.id, 0).execute()
            if (deezerResponse.isSuccessful) {
                val deezerReleases = deezerResponse.body()?.data.orEmpty().map { release ->
                    ReleaseItem(
                        id = release.id,
                        title = release.title,
                        artistName = artist.name,
                        albumArtUrl = release.getBestCoverUrl()?.takeIf { it.isNotBlank() } ?: "",
                        releaseDate = release.release_date,
                        apiSource = "Deezer",
                        deezerId = release.id,
                        artistImageUrl = artistImage
                    )
                }
                releases.addAll(deezerReleases)
            }
        } catch (e: Exception) {
            Log.e(
                "SavedArtistsActivity",
                "❌ Deezer fetch failed for ${artist.name}: ${e.message}",
                e
            )
        }

        val itunesSupportEnabled = sharedPreferences.getBoolean("itunesSupportEnabled", false)

        if (itunesSupportEnabled && artist.itunesId != null) {
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
                            ReleaseItem(
                                id = album.collectionId ?: -1L,
                                title = album.collectionName ?: "Unknown",
                                artistName = album.artistName ?: artist.name,
                                albumArtUrl = album.artworkUrl100
                                    ?.replace("100x100bb", "1200x1200bb")
                                    ?.takeIf { it.isNotBlank() } ?: "",
                                releaseDate = album.releaseDate ?: "Unknown",
                                apiSource = "iTunes",
                                itunesId = album.collectionId,
                                artistImageUrl = artistImage
                            )
                        }
                    releases.addAll(iTunesReleases)
                }
            } catch (e: Exception) {
                Log.e(
                    "SavedArtistsActivity",
                    "❌ iTunes fetch failed for ${artist.name}: ${e.message}",
                    e
                )
            }
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
            db.savedArtistDao().delete(SavedArtist(
                id = artistItem.id,
                name = artistItem.name,
                lastReleaseTitle = artistItem.lastReleaseTitle,
                lastReleaseDate = artistItem.lastReleaseDate,
                profileImageUrl = artistItem.picture
            ))
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