package com.dev.tunedetectivex

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.dev.tunedetectivex.api.ITunesApiService
import com.dev.tunedetectivex.models.ITunesAlbumSearchResponse
import com.dev.tunedetectivex.models.UnifiedAlbum
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale

class ArtistDiscographyActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var apiService: DeezerApiService
    private lateinit var db: AppDatabase
    private var selectedArtist: DeezerArtist? = null

    private lateinit var artistName: String
    private lateinit var artistImageUrl: String
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artist_discography)


        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerViewDiscography)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val deezerId = intent.getLongExtra("deezerId", -1L)
        val itunesId = intent.getLongExtra("itunesId", -1L)
        artistName = intent.getStringExtra("artistName") ?: "Unknown Artist"
        artistImageUrl = intent.getStringExtra("artistImageUrl") ?: ""

        Log.d("DiscographyInit", "Deezer Artist ID: $deezerId")
        Log.d("DiscographyInit", "iTunes Artist ID: $itunesId")


        selectedArtist = DeezerArtist(
            id = deezerId.takeIf { it > 0 } ?: -1L,
            name = artistName,
            picture = artistImageUrl,
            picture_small = artistImageUrl,
            picture_medium = artistImageUrl,
            picture_big = artistImageUrl,
            picture_xl = artistImageUrl,
            itunesId = itunesId.takeIf { it > 0 }
        )

        progressBar = findViewById(R.id.progressBarLoading)
        supportActionBar?.title = artistName


        setupApiService()
        db = AppDatabase.getDatabase(applicationContext)
        loadCombinedDiscography(selectedArtist?.id ?: -1L, selectedArtist?.itunesId ?: -1L)
    }

    private fun loadCombinedDiscography(deezerId: Long, itunesId: Long) {
        getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(applicationContext)

        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkPreference)) {
            Toast.makeText(this, getString(R.string.network_type_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        val unifiedAlbums = mutableListOf<UnifiedAlbum>()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val iTunesService = retrofit.create(ITunesApiService::class.java)

        var deezerLoaded = false
        var itunesLoaded = false

        fun maybeDisplayCombined() {
            if (deezerLoaded && itunesLoaded) {
                showLoading(false)

                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                val uniqueAlbums = unifiedAlbums.distinctBy {
                    normalizeTitle(it.title) + "|" + it.releaseDate.take(10)
                }

                val sorted = uniqueAlbums.sortedByDescending {
                    try {
                        format.parse(it.releaseDate)?.time
                    } catch (_: Exception) {
                        null
                    }
                }

                recyclerView.adapter = UnifiedDiscographyAdapter(sorted) { album ->
                    album.deezerId?.let { selectedArtist?.id = it }
                    album.itunesId?.let { selectedArtist?.itunesId = it }

                    val determinedApiSource = when {
                        album.deezerId != null && album.deezerId > 0 -> "Deezer"
                        album.itunesId != null && album.itunesId > 0 -> "iTunes"
                        else -> {
                            Log.e("ArtistDiscographyActivity", "Cannot determine API source for album: ${album.title}. DeezerID=${album.deezerId}, iTunesID=${album.itunesId}")
                            null
                        }
                    }

                    val releaseIdForDetails = when (determinedApiSource) {
                        "Deezer" -> album.deezerId ?: -1L
                        "iTunes" -> album.itunesId ?: -1L
                        else -> -1L
                    }

                    val validCoverUrl =
                        album.coverUrl.takeIf { it.isNotBlank() && it.startsWith("http") } ?: ""

                    Intent(this, ReleaseDetailsActivity::class.java).apply {
                        putExtra("releaseId", releaseIdForDetails)
                        putExtra("releaseTitle", album.title)
                        putExtra("artistName", album.artistName)
                        putExtra("albumArtUrl", validCoverUrl)
                        putExtra("deezerId", album.deezerId ?: -1L)
                        putExtra("itunesId", album.itunesId ?: -1L)
                        putExtra("apiSource", determinedApiSource)

                        Log.d("ArtistDiscographyActivity", "Sending to ReleaseDetails: " +
                                "releaseId=${releaseIdForDetails}, " +
                                "releaseTitle=${album.title}, " +
                                "deezerId=${album.deezerId}, " +
                                "itunesId=${album.itunesId}, " +
                                "apiSource=${determinedApiSource}")

                    }.also { startActivity(it) }
                }

                Glide.with(this)
                    .load(artistImageUrl)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.error_image)
                    .transform(RoundedCorners(30))
                    .into(findViewById(R.id.imageViewArtist))

                recyclerView.visibility = View.VISIBLE
            }
        }

        apiService.getArtistReleases(deezerId).enqueue(object : Callback<DeezerAlbumsResponse> {
            override fun onResponse(
                call: Call<DeezerAlbumsResponse>,
                response: Response<DeezerAlbumsResponse>
            ) {
                response.body()?.data.orEmpty().forEach { album ->
                    unifiedAlbums.add(
                        UnifiedAlbum(
                            id = album.id.toString(),
                            title = album.title,
                            releaseDate = album.release_date,
                            coverUrl = album.getBestCoverUrl(),
                            artistName = artistName,
                            releaseType = album.record_type?.replaceFirstChar { it.uppercaseChar() },
                            deezerId = album.id,
                            itunesId = null
                        )
                    )
                }

                deezerLoaded = true
                maybeDisplayCombined()
            }

            override fun onFailure(call: Call<DeezerAlbumsResponse>, t: Throwable) {
                Log.e("Discography", "Deezer-Error", t)
                deezerLoaded = true
                maybeDisplayCombined()
            }
        })

        if (itunesId > 0) {
            iTunesService.lookupArtistWithAlbums(itunesId)
                .enqueue(object : Callback<ITunesAlbumSearchResponse> {
                    override fun onResponse(
                        call: Call<ITunesAlbumSearchResponse>,
                        response: Response<ITunesAlbumSearchResponse>
                    ) {
                        response.body()?.results.orEmpty()
                            .filter {
                                it.collectionType in listOf(
                                    "Album",
                                    "EP",
                                    "Single"
                                ) && it.collectionName != null
                            }
                            .forEach { album ->
                                val cleanedTitle = album.collectionName!!
                                    .replace(
                                        Regex(" - (Single|EP|Album)", RegexOption.IGNORE_CASE),
                                        ""
                                    )
                                    .trim()

                                unifiedAlbums.add(
                                    UnifiedAlbum(
                                        id = album.collectionId.toString(),
                                        title = cleanedTitle,
                                        releaseDate = album.releaseDate ?: "Unknown Date",
                                        coverUrl = album.artworkUrl100?.replace(
                                            "100x100bb",
                                            "1200x1200bb"
                                        ) ?: "",
                                        artistName = album.artistName ?: artistName,
                                        releaseType = extractReleaseType(
                                            album.collectionName ?: ""
                                        ),
                                        deezerId = null,
                                        itunesId = album.collectionId
                                    )
                                )
                            }
                        itunesLoaded = true
                        maybeDisplayCombined()
                    }

                    override fun onFailure(call: Call<ITunesAlbumSearchResponse>, t: Throwable) {
                        Log.e("Discography", "iTunes-Error", t)
                        itunesLoaded = true
                        maybeDisplayCombined()
                    }
                })
        } else {
            itunesLoaded = true
            maybeDisplayCombined()
        }
    }

    private fun extractReleaseType(title: String): String? {
        val parenMatch = Regex("\\((Single|EP|Album)\\)", RegexOption.IGNORE_CASE).find(title)
        val dashMatch = Regex("-(\\s*)(Single|EP|Album)", RegexOption.IGNORE_CASE).find(title)

        return when {
            parenMatch != null -> parenMatch.groupValues[1]
            dashMatch != null -> dashMatch.groupValues[2]
            else -> null
        }?.replaceFirstChar { it.uppercaseChar() }
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase(Locale.getDefault())
            .replace(Regex("\\((feat\\.?|featuring)[^)]*\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*(single|ep|album)"), "")
            .replace(Regex("\\s*\\((single|ep|album)\\)"), "")
            .trim()
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun setupApiService() {
        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(applicationContext)

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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}