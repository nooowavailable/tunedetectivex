package com.dev.tunedetectivex

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale

class FetchReleasesWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val FETCH_CHANNEL_ID = "fetch_channel"
        private const val RELEASE_CHANNEL_ID = "release_channel"
        private const val TAG = "FetchReleasesWorker"
    }

    private val db = AppDatabase.getDatabase(context)
    private val apiService: DeezerApiService
    private var isNetworkRequestsAllowed = true

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DeezerApiService::class.java)

        createNotificationChannels()
    }

    override suspend fun doWork(): Result {
        wakeDevice()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Notification permission not granted. Skipping work.")
                return Result.failure()
            }
        }

        return try {
            val sharedPreferences =
                applicationContext.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            val networkType = sharedPreferences.getString("networkType", "Any")
            isNetworkRequestsAllowed =
                WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkType!!)

            if (!isNetworkRequestsAllowed) {
                Log.w(TAG, "Selected network type is not available. Skipping network requests.")
                return Result.failure()
            }

            val fetchDelay = sharedPreferences.getInt("fetchDelay", 0) * 1000L
            if (fetchDelay > 0) {
                delay(fetchDelay)
            }

            fetchSavedArtists()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in FetchReleasesWorker: ${e.message}", e)
            Result.failure()
        } finally {
            NotificationManagerCompat.from(applicationContext).cancel(1)
        }
    }

    private fun wakeDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Notification permission not granted. Skipping wake device.")
                return
            }
        }

        val powerManager =
            applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TuneDetectiveX:ImportWakeLock"
        )
        wakeLock.acquire(5 * 60 * 1000L /*5 minutes*/)

        val notification = createForegroundNotification()
        NotificationManagerCompat.from(applicationContext).notify(1, notification)

        wakeLock.release()
    }

    private fun createForegroundNotification(): Notification {
        val builder = NotificationCompat.Builder(applicationContext, FETCH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Fetching Releases")
            .setContentText("Fetching new releases from your saved artists.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)

        return builder.build()
    }

    private suspend fun fetchSavedArtists() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching saved artists from database...")
        val savedArtists = db.savedArtistDao().getAll()
        Log.d(TAG, "Found ${savedArtists.size} saved artists.")

        savedArtists.chunked(10).forEach { artistBatch ->
            coroutineScope {
                val deferredResults = artistBatch.map { artist ->
                    async { checkForNewRelease(artist) }
                }
                deferredResults.awaitAll()
            }
        }
    }

    private suspend fun checkForNewRelease(artist: SavedArtist) {
        val sharedPreferences =
            applicationContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val maxReleaseAgeInWeeks = sharedPreferences.getInt("releaseAgeWeeks", 4)
        val maxReleaseAgeInMillis = maxReleaseAgeInWeeks * 7 * 24 * 60 * 60 * 1000L
        val currentTime = System.currentTimeMillis()

        try {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Notification permission not granted.")
                return
            }
            val response = apiService.getArtistReleases(artist.id, 0).execute()
            if (response.isSuccessful) {
                val albums = response.body()?.data ?: emptyList()
                val latestRelease = albums.maxByOrNull { release ->
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(release.release_date)?.time ?: 0L
                }

                if (latestRelease != null) {
                    val releaseDateMillis = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .parse(latestRelease.release_date)?.time ?: 0L

                    if (currentTime - releaseDateMillis > maxReleaseAgeInMillis) {
                        Log.d(TAG, "Release ${latestRelease.title} is older than the set threshold.")
                        return
                    }

                    val releaseHash = (artist.id.toString() + latestRelease.title + latestRelease.release_date).hashCode()
                    if (db.savedArtistDao().isNotificationSent(releaseHash)) {
                        Log.d(TAG, "Notification already sent for release: ${latestRelease.title}")
                        return
                    }

                    val releaseType = when (latestRelease.record_type) {
                        "album" -> "Album"
                        "single" -> "Single"
                        "ep" -> "EP"
                        else -> "Release"
                    }

                    sendReleaseNotification(
                        artist,
                        latestRelease,
                        releaseHash,
                        releaseDateMillis,
                        releaseType
                    )
                } else {
                    Log.d(TAG, "No latest release found for artist ${artist.name}")
                }
            } else {
                Log.e(TAG, "Failed to fetch releases for artist ${artist.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for new release: ${e.message}", e)
        }
    }

    private fun sendReleaseNotification(
        artist: SavedArtist,
        album: DeezerAlbum,
        releaseHash: Int,
        releaseDate: Long,
        releaseType: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Notification permission not granted. Skipping notification.")
                return
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val albumArtBitmap = Glide.with(applicationContext)
                    .asBitmap()
                    .load(album.getBestCoverUrl())
                    .submit()
                    .get()

                val notification = createNotification(
                    artist = artist,
                    album = album,
                    albumArtBitmap = albumArtBitmap,
                    releaseType = releaseType,
                    channelId = RELEASE_CHANNEL_ID
                )

                Log.d(TAG, "Sending notification with channel ID: $RELEASE_CHANNEL_ID")
                NotificationManagerCompat.from(applicationContext).notify(releaseHash, notification)

                db.savedArtistDao()
                    .markNotificationAsSent(SentNotification(releaseHash, releaseDate))
            } catch (e: Exception) {
                Log.e(TAG, "Error loading album artwork for notification: ${e.message}", e)

                val fallbackNotification = createNotification(
                    artist = artist,
                    album = album,
                    albumArtBitmap = null,
                    releaseType = releaseType,
                    channelId = RELEASE_CHANNEL_ID
                )
                NotificationManagerCompat.from(applicationContext)
                    .notify(releaseHash, fallbackNotification)
            }
        }
    }

    private fun createNotification(
        artist: SavedArtist,
        album: DeezerAlbum,
        albumArtBitmap: Bitmap?,
        releaseType: String,
        channelId: String
    ): Notification {
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New $releaseType from ${artist.name}")
            .setContentText("${artist.name} released ${album.title}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (albumArtBitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(albumArtBitmap)
                    .bigLargeIcon(null as Bitmap?)
            ).setLargeIcon(albumArtBitmap)
        }

        return builder.build()
    }

    private fun createNotificationChannels() {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val fetchChannel = NotificationChannel(
            FETCH_CHANNEL_ID,
            "Background Fetch Notifications",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Silent notifications for background fetching of artist releases"
        }
        manager.createNotificationChannel(fetchChannel)

        val releaseChannel = NotificationChannel(
            RELEASE_CHANNEL_ID,
            "New Release Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for new releases from artists"
        }
        manager.createNotificationChannel(releaseChannel)
    }
}