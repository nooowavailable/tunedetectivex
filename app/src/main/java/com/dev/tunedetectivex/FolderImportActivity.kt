package com.dev.tunedetectivex

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class FolderImportActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FolderImportActivity"
    }

    private val CHANNEL_ID = "import_progress_channel"
    private lateinit var recyclerView: RecyclerView
    private val musicFiles = mutableListOf<MusicFile>()
    private lateinit var db: AppDatabase
    private lateinit var apiService: DeezerApiService
    private lateinit var textViewArtistName: TextView
    private lateinit var imageViewArtistCover: ImageView
    private lateinit var linearProgressIndicator: LinearProgressIndicator
    private lateinit var textViewProgress: TextView
    private lateinit var wakeLock: PowerManager.WakeLock
    private var isImporting = false
    private var currentProgress = 0
    private var currentArtistName: String? = null
    private lateinit var cardCurrentArtist: MaterialCardView
    private var successfulImports = 0
    private var unknownArtists = 0
    private var failedImports = 0
    private val artistAlbumsCache = mutableMapOf<Long, List<DeezerAlbum>>()
    private var isNetworkRequestsAllowed = true
    private lateinit var statusTextView: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_import)

        requestNotificationPermission()
        createNotificationChannel()
        setupApiService()
        acquireWakeLock()
        checkNetworkTypeAndSetFlag()

        db = AppDatabase.getDatabase(applicationContext)
        recyclerView = findViewById(R.id.recyclerViewMusicFiles)
        recyclerView.layoutManager = LinearLayoutManager(this)
        cardCurrentArtist = findViewById(R.id.cardCurrentArtist)
        linearProgressIndicator = findViewById(R.id.linearProgressIndicator)
        textViewProgress = findViewById(R.id.textViewProgress)
        imageViewArtistCover = findViewById(R.id.imageViewArtistCover)
        textViewArtistName = findViewById(R.id.textViewArtistName)
        statusTextView = findViewById(R.id.statusTextView)


        cardCurrentArtist.visibility = View.GONE
        linearProgressIndicator.visibility = View.GONE
        textViewProgress.visibility = View.GONE

        updateImportStatus(false)


        val fabSelectFolder: FloatingActionButton = findViewById(R.id.fabSelectFolder)
        fabSelectFolder.setOnClickListener {
            if (!isNetworkRequestsAllowed) {
                Log.w(TAG, "Selected network type is not available. Skipping folder selection.")
                Toast.makeText(
                    this,
                    "Selected network type is not available. Please check your connection.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            showWarningDialog()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateImportStatus(isImporting: Boolean) {
        if (isImporting) {
            statusTextView.text = "Importing... Please wait."
        } else {
            statusTextView.text = "Press folder icon to start import"
        }
    }

    private fun showWarningDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Warning ⚠️")
            .setMessage(
                "You are about to import a folder. Please note that you use this function at your own risk. 😬\n\n" +
                        "• This feature is in beta and may result in your IP address being blocked by Deezer. 🤡\n" +
                        "• It is recommended to use a VPN. 🕵️‍♂️"
            )
            .setPositiveButton("Proceed") { _, _ ->
                selectMusicFolder()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    private fun checkNetworkTypeAndSetFlag() {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any")

        isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(this, networkType!!)
    }

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val folderName = extractFolderName(uri)
                Toast.makeText(this, "Selected folder: $folderName", Toast.LENGTH_SHORT).show()
                processMusicFolder(uri)
            } else {
                Toast.makeText(this, "No folder selected", Toast.LENGTH_SHORT).show()
            }
        }

    private fun setupApiService() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DeezerApiService::class.java)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TuneDetectiveX:ImportWakeLock"
        )
        wakeLock.acquire(15 * 60 * 1000L)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private suspend fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
            withContext(Dispatchers.Main) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun extractFolderName(folderUri: Uri): String {
        val fullPath = DocumentsContract.getTreeDocumentId(folderUri)
        return fullPath.replace("primary:", "").substringAfterLast("/")
    }

    @SuppressLint("SetTextI18n")
    private fun processMusicFolder(folderUri: Uri) {
        isImporting = true
        updateImportStatus(isImporting)
        statusTextView.visibility = View.GONE

        val fabSelectFolder: FloatingActionButton = findViewById(R.id.fabSelectFolder)
        fabSelectFolder.isEnabled = false

        acquireWakeLock()

        lifecycleScope.launch(Dispatchers.Main) {
            cardCurrentArtist.visibility = View.VISIBLE
            linearProgressIndicator.visibility = View.VISIBLE
            textViewProgress.visibility = View.VISIBLE
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                musicFiles.clear()
                val documentUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    folderUri,
                    DocumentsContract.getTreeDocumentId(folderUri)
                )

                contentResolver.query(
                    documentUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val total = cursor.count
                    var current = 0

                    while (cursor.moveToNext()) {
                        val documentId = cursor.getString(0)
                        val mimeType = cursor.getString(1)

                        if (mimeType.startsWith("audio/")) {
                            current++
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
                            val mainArtistName = extractMainArtistMetadata(fileUri)
                            val releaseTitle = extractReleaseTitleMetadata(fileUri)

                            if (!mainArtistName.isNullOrEmpty()) {
                                val existingArtist =
                                    db.savedArtistDao().getArtistByName(mainArtistName)
                                val artistId = existingArtist?.id

                                if (artistId != null) {
                                    Log.d(
                                        TAG,
                                        "Artist $mainArtistName is already saved. Skipping..."
                                    )
                                    withContext(Dispatchers.Main) {
                                        textViewArtistName.text = "$mainArtistName (Already Saved)"
                                        updateImportProgress(
                                            current,
                                            total,
                                            mainArtistName,
                                            "Skipped"
                                        )
                                    }
                                } else {
                                    val artistIds = fetchArtistIdByName(mainArtistName)
                                    var foundArtist = false

                                    for (newArtistId in artistIds) {
                                        val cachedAlbums = artistAlbumsCache[newArtistId]
                                        val albums = if (cachedAlbums != null) {
                                            Log.d(
                                                TAG,
                                                "Using cached albums for artist: $mainArtistName"
                                            )
                                            cachedAlbums
                                        } else {
                                            val response =
                                                apiService.getArtistReleases(newArtistId).execute()
                                            if (response.isSuccessful) {
                                                val fetchedAlbums =
                                                    response.body()?.data ?: emptyList()
                                                artistAlbumsCache[newArtistId] = fetchedAlbums
                                                Log.d(
                                                    TAG,
                                                    "Caching albums for artist: $mainArtistName"
                                                )
                                                fetchedAlbums
                                            } else {
                                                failedImports++
                                                Log.d(
                                                    TAG,
                                                    "Failed to fetch releases for artist: $mainArtistName with ID: $newArtistId"
                                                )
                                                emptyList()
                                            }
                                        }

                                        if (checkArtistReleases(albums, releaseTitle)) {
                                            saveArtistIfNotExists(newArtistId, mainArtistName)
                                            successfulImports++
                                            Log.d(
                                                TAG,
                                                "Saved new artist: $mainArtistName with ID: $newArtistId because of matching release title: $releaseTitle"
                                            )
                                            foundArtist = true
                                            break
                                        } else {
                                            failedImports++
                                            Log.d(
                                                TAG,
                                                "Did not save artist: $mainArtistName with ID: $newArtistId because no matching release title was found."
                                            )
                                        }
                                    }

                                    if (!foundArtist) {
                                        Log.d(TAG, "No matching artist found for: $mainArtistName")
                                    }
                                }

                                val coverUrl = fetchCoverUrl(mainArtistName)
                                if (!coverUrl.isNullOrEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        Glide.with(this@FolderImportActivity)
                                            .load(coverUrl)
                                            .placeholder(R.drawable.placeholder_image)
                                            .circleCrop()
                                            .into(imageViewArtistCover)

                                        textViewArtistName.text = mainArtistName
                                    }
                                }
                            } else {
                                unknownArtists++
                            }
                            updateImportProgress(current, total, mainArtistName, null)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        updateRecyclerView()
                    }
                }

                withContext(Dispatchers.Main) {
                    updateImportStatus(false)
                    completeImportNotification()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing folder: ${e.message}", e)
            } finally {
                isImporting = false
                releaseWakeLock()
                Log.d(
                    TAG,
                    "Import completed: Successful imports: $successfulImports, Failed imports: $failedImports, Unknown artists: $unknownArtists"
                )

                withContext(Dispatchers.Main) {
                    fabSelectFolder.isEnabled = true
                    statusTextView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun fetchArtistIdByName(artistName: String): List<Long> {
        return try {
            val response = apiService.searchArtist(artistName).execute()
            if (response.isSuccessful) {
                response.body()?.data?.map { it.id } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching artist ID: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun saveArtistIfNotExists(artistId: Long, artistName: String) {
        val existingArtist = db.savedArtistDao().getArtistById(artistId)
        if (existingArtist == null) {
            db.savedArtistDao().insert(
                SavedArtist(
                    id = artistId,
                    name = artistName
                )
            )
        }
    }

    private fun extractReleaseTitleMetadata(fileUri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            contentResolver.openFileDescriptor(fileUri, "r")?.use {
                retriever.setDataSource(it.fileDescriptor)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)

                title?.trim()
                    ?.replace("feat.", "")
                    ?.replace("with", "")
                    ?.replace("(", "")
                    ?.replace(")", "")
            }
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun fetchCoverUrl(artistName: String?): String? {
        return try {
            if (artistName.isNullOrEmpty()) return null

            val cachedUrl = ArtistImageCache.get(artistName)
            if (cachedUrl != null) {
                return cachedUrl
            }

            val response = apiService.searchArtist(artistName).execute()
            if (response.isSuccessful) {
                val artist = response.body()?.data?.firstOrNull()
                val imageUrl = artist?.getBestPictureUrl()
                if (imageUrl != null) {
                    ArtistImageCache.put(artistName, imageUrl)
                }
                imageUrl
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun checkArtistReleases(albums: List<DeezerAlbum>, releaseTitle: String?): Boolean {
        val normalizedInputTitle = releaseTitle?.trim()?.lowercase()
        return albums.any { album ->
            val normalizedReleaseTitle = album.title.trim().lowercase()
            normalizedReleaseTitle == normalizedInputTitle
        }
    }

    private fun extractMainArtistMetadata(fileUri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            contentResolver.openFileDescriptor(fileUri, "r")?.use {
                retriever.setDataSource(it.fileDescriptor)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                artist?.split(";")
                    ?.map { it.trim() }
                    ?.firstOrNull { it.isNotBlank() }
            }
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateRecyclerView() {
        val adapter = MusicFilesAdapter(musicFiles)
        recyclerView.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    private fun selectMusicFolder() {
        folderPickerLauncher.launch(null)
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Import Progress"
            val descriptionText = "Progress notifications for importing artists"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    @SuppressLint("SetTextI18n")
    private suspend fun updateImportProgress(current: Int, total: Int, artistName: String?, status: String?) {
        currentProgress = current
        currentArtistName = artistName

        withContext(Dispatchers.Main) {
            if (isImporting) {
                cardCurrentArtist.visibility = View.VISIBLE
                linearProgressIndicator.visibility = View.VISIBLE
                textViewProgress.visibility = View.VISIBLE
            }

            if (total > 0) {
                val progressPercentage = (current * 100) / total
                linearProgressIndicator.progress = progressPercentage

                val name = artistName ?: "Unknown Artist"
                val artistStatus = status ?: ""
                textViewProgress.text = "Progress: $progressPercentage% ($current/$total)"
                textViewArtistName.text = name

                sendNotification(progressPercentage, "$name $artistStatus")
            } else {
                textViewProgress.text = "Preparing import..."
                textViewArtistName.text = "Loading artist..."
                linearProgressIndicator.isIndeterminate = true
            }

            if (isImporting) {
                acquireWakeLock()
            } else {
                releaseWakeLock()
            }
        }
    }

    private fun sendNotification(progressPercentage: Int, artistName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = NotificationManagerCompat.from(this)

        val isFinished = progressPercentage == 100
        val title = if (isFinished) "Import completed" else "Import artists"
        val contentText = if (isFinished) {
            "All artists successfully imported."
        } else {
            "Progress: $progressPercentage% - $artistName"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setProgress(100, progressPercentage, false)
            .setOngoing(!isFinished)
        if (isFinished) {
            builder.setProgress(0, 0, false)
            builder.setOngoing(false)
        }
        notificationManager.notify(1, builder.build())
        if (isFinished) {
            Handler(Looper.getMainLooper()).postDelayed({
                notificationManager.cancel(1)
            }, 3000)
        }
    }

    @SuppressLint("SetTextI18n")
    private suspend fun completeImportNotification() {
        withContext(Dispatchers.Main) {
            cardCurrentArtist.visibility = View.GONE
            linearProgressIndicator.visibility = View.GONE
            textViewProgress.visibility = View.GONE
            textViewProgress.text = "Import completed!"
            linearProgressIndicator.progress = 0

            val notificationManager = NotificationManagerCompat.from(this@FolderImportActivity)
            notificationManager.cancel(1)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }
}