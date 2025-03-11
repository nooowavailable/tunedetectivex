package com.dev.tunedetectivex

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
        checkNetworkTypeAndSetFlag()
    }

    private fun checkNetworkTypeAndSetFlag() {
        val sharedPreferences =
            applicationContext.getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any") ?: "Any"
        isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkType)
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
                applicationContext.getSharedPreferences("AppPreferences", MODE_PRIVATE)
            val networkType = sharedPreferences.getString("networkType", "Any") ?: "Any"
            isNetworkRequestsAllowed =
                WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkType)

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
            .setContentTitle(applicationContext.getString(R.string.fetching_releases_title))
            .setContentText(applicationContext.getString(R.string.fetching_releases_text))
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
            applicationContext.getSharedPreferences("AppSettings", MODE_PRIVATE)
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
                Log.d(TAG, "API Response for artist ${artist.name}: $albums")

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
                        "album" -> applicationContext.getString(R.string.release_type_album)
                        "single" -> applicationContext.getString(R.string.release_type_single)
                        "ep" -> applicationContext.getString(R.string.release_type_ep)
                        else -> applicationContext.getString(R.string.release_type_default)
                    }

                    val coverUrl = latestRelease.getBestCoverUrl()
                    Log.d(TAG, "Cover URL for latest release ${latestRelease.title}: $coverUrl")

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
                Log.e(
                    TAG,
                    "Failed to fetch releases for artist ${artist.name}: ${
                        response.errorBody()?.string()
                    }"
                )
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
                val albumArtBitmap = BitmapUtils.loadBitmapFromUrlSync(album.getBestCoverUrl())

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
        val notificationTitle = when (releaseType) {
            "Album" -> applicationContext.getString(
                R.string.notification_new_release_title_album,
                releaseType,
                artist.name
            )

            "Single" -> applicationContext.getString(
                R.string.notification_new_release_title_single,
                releaseType,
                artist.name
            )

            "EP" -> applicationContext.getString(
                R.string.notification_new_release_title_ep,
                releaseType,
                artist.name
            )

            else -> applicationContext.getString(
                R.string.notification_new_release_title_default,
                releaseType,
                artist.name
            )
        }

        val notificationText = applicationContext.getString(
            R.string.notification_release_text,
            artist.name,
            album.title
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (albumArtBitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(albumArtBitmap)
                    .bigLargeIcon(null as Bitmap?)
            ).setLargeIcon(albumArtBitmap)
        } else {
            builder.setLargeIcon(
                BitmapFactory.decodeResource(
                    applicationContext.resources,
                    R.drawable.error_image
                )
            )
        }

        return builder.build()
    }

    private fun createNotificationChannels() {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val fetchChannel = NotificationChannel(
            FETCH_CHANNEL_ID,
            applicationContext.getString(R.string.notification_channel_fetch),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description =
                applicationContext.getString(R.string.notification_channel_fetch_description)
        }
        manager.createNotificationChannel(fetchChannel)

        val releaseChannel = NotificationChannel(
            RELEASE_CHANNEL_ID,
            applicationContext.getString(R.string.notification_channel_release),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description =
                applicationContext.getString(R.string.notification_channel_release_description)
        }
        manager.createNotificationChannel(releaseChannel)
    }
}