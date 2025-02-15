package com.dev.tunedetectivex

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var searchView: androidx.appcompat.widget.SearchView
    private var allArtists: List<SavedArtistItem> = emptyList()
    private lateinit var artistAdapter: SavedArtistAdapter
    private lateinit var releaseAdapter: ReleaseAdapter
    private lateinit var progressBar: ProgressBar
    private var isNetworkRequestsAllowed = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_artists)

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        recyclerView = findViewById(R.id.recyclerViewSavedArtists)
        spinnerViewType = findViewById(R.id.spinnerViewType)
        searchView = findViewById(R.id.searchViewArtists)
        progressBar = findViewById(R.id.progressBar)


        db = AppDatabase.getDatabase(applicationContext)

        setupApiService()
        setupRecyclerViewWithPlaceholder()
        setupSearchView()
        setupSpinner()
        setupRecyclerView()



        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val isFirstRunSavedArtists = sharedPreferences.getBoolean("isFirstRunSavedArtists", true)

        if (isFirstRunSavedArtists) {
            showSavedArtistsTutorial()
            sharedPreferences.edit().putBoolean("isFirstRunSavedArtists", false).apply()
        }

        swipeRefreshLayout.setOnRefreshListener {
            progressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                val selectedPosition = spinnerViewType.selectedItemPosition
                if (selectedPosition == 0) {
                    allArtists.forEach { artist ->
                        showRealtimeIndicator(artist.id)
                        fetchArtistDetails(artist)
                    }
                } else {
                    loadSavedReleases()
                }
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun checkNetworkTypeAndSetFlag() {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any")

        isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkType!!)

        if (!isNetworkRequestsAllowed) {
            Toast.makeText(
                this,
                "Selected network type is not available. Please check your connection.",
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


    private fun setupRecyclerViewWithPlaceholder() {
        recyclerView.adapter = PlaceholderAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch(Dispatchers.IO) {
            val savedArtists = db.savedArtistDao().getAll().sortedBy { it.name.lowercase() }
            val tempList = savedArtists.map {
                val artistName = it.name
                Log.d("SavedArtistsActivity", "Loaded artist: ID=${it.id}, Name=$artistName")
                SavedArtistItem(
                    id = it.id,
                    name = artistName,
                    lastReleaseTitle = it.lastReleaseTitle,
                    lastReleaseDate = it.lastReleaseDate,
                    picture = it.profileImageUrl ?: ""
                )
            }.toMutableList()

            withContext(Dispatchers.Main) {
                artistAdapter = SavedArtistAdapter(
                    onDelete = { artist -> deleteArtistFromDb(artist) },
                    onArtistClick = { artist -> openArtistDiscography(artist) }
                )
                recyclerView.adapter = artistAdapter
                artistAdapter.submitList(tempList)
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
        artistAdapter = SavedArtistAdapter(
            onDelete = { artist -> deleteArtistFromDb(artist) },
            onArtistClick = { artist -> openArtistDiscography(artist) }
        )
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
            putExtra("artistId", artist.id)
            putExtra("artistName", artist.name)
            putExtra("artistImageUrl", artist.picture)
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
                    profileImageUrl = artist.picture
                )
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SavedArtistsActivity, "${artist.name} has been deleted.", Toast.LENGTH_SHORT).show()
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
                        "Deletion is only possible in the artist view.",
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
        checkNetworkTypeAndSetFlag()

        if (!isNetworkRequestsAllowed) {
            Toast.makeText(
                this,
                "Selected network type is not available. Please check your connection.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        swipeRefreshLayout.isEnabled = false
        swipeRefreshLayout.isRefreshing = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val savedArtists = db.savedArtistDao().getAll().sortedBy { it.name.lowercase() }
                val tempList = savedArtists.map {
                    val artistName = it.name
                    Log.d("SavedArtistsActivity", "Artists from DB: ID=${it.id}, Name=$artistName")
                    SavedArtistItem(
                        id = it.id,
                        name = artistName,
                        lastReleaseTitle = it.lastReleaseTitle,
                        lastReleaseDate = it.lastReleaseDate,
                        picture = it.profileImageUrl ?: "",
                        isLoading = true
                    )
                }.toMutableList()

                withContext(Dispatchers.Main) {
                    updateArtistList(tempList)
                }

                val artistIds = savedArtists.map { it.id }
                val fetchedArtists = fetchArtistsBatch(artistIds)

                fetchedArtists.forEach { fetchedArtist ->
                    Log.d(
                        "SavedArtistsActivity",
                        "API data: ID=${fetchedArtist.id}, Name=${fetchedArtist.name}, Bild=${fetchedArtist.picture_xl}"
                    )
                    tempList.replaceFirstOrAdd(
                        SavedArtistItem(
                            id = fetchedArtist.id,
                            name = fetchedArtist.name,
                            picture = fetchedArtist.picture_xl,
                            isLoading = false
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    allArtists = tempList.toList()
                    updateArtistList(allArtists)
                }
            } catch (e: Exception) {
                Log.e("SavedArtistsActivity", "Error loading the artists: ${e.message}", e)
            } finally {
                withContext(Dispatchers.Main) {
                    swipeRefreshLayout.isRefreshing = false
                    swipeRefreshLayout.isEnabled = true
                }
            }
        }
    }

    private fun MutableList<SavedArtistItem>.replaceFirstOrAdd(newItem: SavedArtistItem) {
        val index = indexOfFirst { it.id == newItem.id }
        if (index != -1) {
            this[index] = newItem
        } else {
            add(newItem)
        }
    }




    private suspend fun fetchArtistsByName(name: String): List<DeezerArtist> {
        val sharedPreferences =
            applicationContext.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any")
        val isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkType!!)

        if (!isNetworkRequestsAllowed) {
            Log.w(
                "SavedArtistsActivity",
                "Selected network type is not available. Skipping network requests."
            )
            return emptyList()
        }

        return try {
            val response = apiService.searchArtist(name).execute()
            if (response.isSuccessful) {
                response.body()?.data ?: emptyList()
            } else {
                Log.e("SavedArtistsActivity", "Error when retrieving the artist by name: $name")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("SavedArtistsActivity", "Error when retrieving the artist by name: $name", e)
            emptyList()
        }
    }

    private suspend fun fetchArtistDetails(artist: SavedArtistItem): SavedArtistItem? {
        val sharedPreferences =
            applicationContext.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any")
        val isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkType!!)

        if (!isNetworkRequestsAllowed) {
            Log.w(
                "SavedArtistsActivity",
                "Selected network type is not available. Skipping network requests."
            )
            return artist
        }

        return try {
            val fetchedArtists = fetchArtistsByName(artist.name)
            val bestMatch = fetchedArtists.firstOrNull()
            if (bestMatch != null) {
                Log.d(
                    "SavedArtistsActivity",
                    "Best matching: ID=${bestMatch.id}, Name=${bestMatch.name}, Bild=${bestMatch.picture_xl}"
                )
                db.savedArtistDao().updateArtistDetails(
                    artistId = bestMatch.id,
                    profileImageUrl = bestMatch.picture_xl
                )
                return artist.copy(
                    id = bestMatch.id,
                    name = bestMatch.name,
                    picture = bestMatch.picture_xl
                )
            }
            Log.w("SavedArtistsActivity", "No match for artists: ${artist.name}")
            artist
        } catch (e: Exception) {
            Log.e(
                "SavedArtistsActivity",
                "Error when retrieving details for ${artist.name}: ${e.message}",
                e
            )
            artist
        }
    }



    private fun showRealtimeIndicator(artistId: Long) {
        lifecycleScope.launch {
            updateArtistLoadingState(artistId, true)
            delay(1000)
            updateArtistLoadingState(artistId, false)
        }
    }


    private fun loadSavedReleases() {
        checkNetworkTypeAndSetFlag()

        if (!isNetworkRequestsAllowed) {
            Toast.makeText(
                this,
                "Selected network type is not available. Please check your connection.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        swipeRefreshLayout.isEnabled = true
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
            val isFirstRunReleases = sharedPreferences.getBoolean("isFirstRunReleases", true)

            val savedArtists = db.savedArtistDao().getAll()
            val artistIds = savedArtists.map { it.id }

            val releaseItems = mutableListOf<ReleaseItem>()

            for (artistId in artistIds) {
                val releases = fetchReleasesForArtist(artistId)
                releaseItems.addAll(releases)
            }

            val sortedReleaseItems = releaseItems.sortedByDescending { release ->
                try {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(release.releaseDate)?.time
                } catch (_: Exception) {
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
                    }
                    startActivity(intent)
                }
                recyclerView.adapter = adapter
                adapter.submitList(sortedReleaseItems)
                recyclerView.visibility = if (sortedReleaseItems.isNotEmpty()) View.VISIBLE else View.GONE
                recyclerView.scrollToPosition(0)

                if (isFirstRunReleases) {
                    showReleasesTutorial()
                    sharedPreferences.edit().putBoolean("isFirstRunReleases", false).apply()
                }
                progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun fetchReleasesForArtist(artistId: Long): List<ReleaseItem> {
        val sharedPreferences =
            applicationContext.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any")
        val isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkType!!)

        if (!isNetworkRequestsAllowed) {
            Log.w(
                "SavedArtistsActivity",
                "Selected network type is not available. Skipping network requests."
            )
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getArtistReleases(artistId, 0).execute()
                if (response.isSuccessful) {
                    Log.d("SavedArtistsActivity", "API response successful for Artist ID=$artistId")
                    response.body()?.data?.map { release ->
                        ReleaseItem(
                            id = release.id,
                            title = release.title,
                            artistName = db.savedArtistDao().getArtistById(artistId)?.name ?: "Unknown artist",
                            albumArtUrl = release.getBestCoverUrl(),
                            releaseDate = release.release_date
                        )
                    } ?: emptyList()
                } else {
                    Log.e(
                        "SavedArtistsActivity",
                        "Error when retrieving releases for Artist ID=$artistId: ${response.code()} ${response.message()}"
                    )
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("SavedArtistsActivity", "Error when retrieving releases for Artist ID=$artistId: ${e.message}", e)
                emptyList()
            }
        }
    }


    private suspend fun fetchArtistsBatch(artistIds: List<Long>): List<DeezerArtist> {
        val sharedPreferences =
            applicationContext.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any")
        val isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkType!!)

        if (!isNetworkRequestsAllowed) {
            Log.w(
                "SavedArtistsActivity",
                "Selected network type is not available. Skipping network requests."
            )
            return emptyList()
        }

        return coroutineScope {
            artistIds.chunked(10).flatMap { batch ->
                batch.map { id ->
                    async(Dispatchers.IO) {
                        try {
                            val response = apiService.getArtistDetails(id).execute()
                            if (response.isSuccessful) {
                                response.body()?.also {
                                    Log.d(
                                        "SavedArtistsActivity",
                                        "API response successful: ID=$id, Name=${it.name}, Bild=${it.picture_xl}"
                                    )
                                }
                            } else {
                                Log.e(
                                    "SavedArtistsActivity",
                                    "Faulty API response for ID=$id: ${response.errorBody()?.string()}"
                                )
                                null
                            }
                        } catch (e: Exception) {
                            Log.e(
                                "SavedArtistsActivity",
                                "Error retrieving artist information: ID=$id, ${e.message}",
                                e
                            )
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }
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


    private fun updateArtistLoadingState(artistId: Long, isLoading: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            val updatedArtists = allArtists.map { artist ->
                if (artist.id == artistId) {
                    artist.copy(isLoading = isLoading)
                } else {
                    artist
                }
            }
            allArtists = updatedArtists
            updateArtistList(allArtists)
        }
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
                    "Select view",
                    "Choose here whether you want to display saved artists or their latest releases."
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    recyclerView,
                    "Saved artists",
                    "Here you can see all saved artists with their latest releases. Tap on an artist to delete it."
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    recyclerView,
                    "Newest releases",
                    "Select 'Latest Releases' from the dropdown to see the latest releases from all saved artists."
                ).transparentTarget(true).cancelable(false)
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    Toast.makeText(
                        this@SavedArtistsActivity,
                        "Tutorial completed! Have fun with the app.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onSequenceStep(tapTarget: TapTarget, targetClicked: Boolean) {
                }

                override fun onSequenceCanceled(tapTarget: TapTarget?) {
                    Toast.makeText(
                        this@SavedArtistsActivity,
                        "Tutorial canceled.",
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
                    "Latest releases",
                    "Here you can see the latest releases of all saved artists. Tap on a release to view details such as the track list."
                ).transparentTarget(true).cancelable(false),

                TapTarget.forView(
                    swipeRefreshLayout,
                    "Refresh",
                    "Pull down the list to load the latest releases."
                ).transparentTarget(true).cancelable(false)
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    Toast.makeText(
                        this@SavedArtistsActivity,
                        "Tutorial completed! Have fun discovering the releases.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onSequenceStep(tapTarget: TapTarget, targetClicked: Boolean) {
                }

                override fun onSequenceCanceled(tapTarget: TapTarget?) {
                    Toast.makeText(
                        this@SavedArtistsActivity,
                        "Tutorial aborted.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
            .start()
    }
}
