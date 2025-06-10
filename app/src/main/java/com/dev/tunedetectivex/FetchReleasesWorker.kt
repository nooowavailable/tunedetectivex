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
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.dev.tunedetectivex.api.ITunesApiService
import com.dev.tunedetectivex.model.enums.ReleaseType
import com.dev.tunedetectivex.models.UnifiedAlbum
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
    private val iTunesApiService: ITunesApiService
    private var isNetworkRequestsAllowed = true

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(DeezerApiService::class.java)

        iTunesApiService = Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ITunesApiService::class.java)

        createNotificationChannels()
    }

    override suspend fun doWork(): Result {
        wakeDevice()

        if (!hasNotificationPermission()) {
            Log.w(TAG, "Notification permission not granted. Skipping work.")
            return Result.failure()
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

            val isManual = inputData.getBoolean("manual", false)
            val fetchDelay = sharedPreferences.getInt("fetchDelay", 1) * 1000L
            if (!isManual && fetchDelay > 0) {
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
        if (!hasNotificationPermission()) {
            Log.w(TAG, "Notification permission not granted. Skipping wake device.")
            return
        }

        try {
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
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in wakeDevice(): ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in wakeDevice(): ${e.message}", e)
        }
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
        val sharedPreferences = applicationContext.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any") ?: "Any"

        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkType)) {
            Log.w(TAG, "Aborting fetch: selected network type ($networkType) not available.")
            return@withContext
        }

        Log.d(TAG, "Fetching saved artists from database (notifications enabled only)...")
        val savedArtists = db.savedArtistDao().getAllWithNotificationsEnabled()
        Log.d(TAG, "Found ${savedArtists.size} artists with notifications enabled.")

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
        val sharedPreferences = applicationContext.getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val networkPrefs = applicationContext.getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val networkType = networkPrefs.getString("networkType", "Any") ?: "Any"

        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkType)) {
            Log.w(TAG, "Skipping check: network type '$networkType' not available.")
            return
        }

        val maxReleaseAgeInWeeks = sharedPreferences.getInt("releaseAgeWeeks", 4)
        val maxReleaseAgeInMillis = maxReleaseAgeInWeeks * 7 * 24 * 60 * 60 * 1000L
        val currentTime = System.currentTimeMillis()

        val isItunesSupportEnabled = sharedPreferences.getBoolean("itunesSupportEnabled", false)

        try {
            if (!hasNotificationPermission()) {
                Log.w(TAG, "Notification permission not granted.")
                return
            }

            val releases = mutableListOf<UnifiedAlbum>()

            val deezerResponse =
                apiService.getArtistReleases(artist.deezerId ?: return, 0).execute()
            if (deezerResponse.isSuccessful) {
                val deezerAlbums = deezerResponse.body()?.data ?: emptyList()
                releases += deezerAlbums.mapNotNull {
                    val artistName =
                        it.artist?.name ?: artist.name

                    UnifiedAlbum(
                        id = "${artist.deezerId}_${it.id}",
                        title = cleanTitle(it.title),
                        artistName = artistName,
                        releaseDate = it.release_date,
                        coverUrl = it.getBestCoverUrl(),
                        releaseType = it.record_type,
                        deezerId = artist.deezerId,
                        rawTitle = it.title,
                        rawReleaseType = it.record_type
                    )
                }
            }

            if (isItunesSupportEnabled && artist.itunesId != null) {
                val itunesResponse =
                    iTunesApiService.lookupArtistWithAlbums(artist.itunesId).execute()
                if (itunesResponse.isSuccessful) {
                    val items = itunesResponse.body()?.results ?: emptyList()
                    val albums =
                        items.filter { it.wrapperType == "collection" && it.collectionName != null }
                    releases += albums.mapNotNull {
                        UnifiedAlbum(
                            id = "${artist.itunesId}_${it.collectionId}",
                            title = cleanTitle(it.collectionName ?: return@mapNotNull null),
                            artistName = it.artistName ?: artist.name,
                            releaseDate = it.releaseDate?.substring(0, 10)
                                ?: return@mapNotNull null,
                            coverUrl = it.getHighResArtwork() ?: "",
                            releaseType = it.collectionType,
                            itunesId = artist.itunesId,
                            rawTitle = it.collectionName,
                            rawReleaseType = it.collectionType
                        )

                    }
                }
            }

            val latest = releases.maxByOrNull {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.releaseDate)?.time
                    ?: 0L
            } ?: return

            val releaseDateMillis = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .parse(latest.releaseDate)?.time ?: return

            if (currentTime - releaseDateMillis > maxReleaseAgeInMillis) {
                Log.d(TAG, "Release ${latest.title} is too old.")
                return
            }

            val releaseHash = "${latest.artistName.trim().lowercase()}_${
                latest.title.trim().lowercase()
            }_${latest.releaseDate}".hashCode()
            if (db.savedArtistDao().isNotificationSent(releaseHash)) {
                Log.d(TAG, "Notification already sent for ${latest.title}")
                return
            }

            val releaseTypeEnum = determineAccurateReleaseType(latest)

            sendUnifiedReleaseNotification(
                artist,
                latest,
                releaseHash,
                releaseDateMillis,
                releaseTypeEnum
            )

            sendUnifiedReleaseNotification(
                artist,
                latest,
                releaseHash,
                releaseDateMillis,
                releaseTypeEnum
            )


        } catch (e: Exception) {
            Log.e(TAG, "Error checking for new release: ${e.message}", e)
        }
    }

    private fun sendUnifiedReleaseNotification(
        artist: SavedArtist,
        album: UnifiedAlbum,
        releaseHash: Int,
        releaseDate: Long,
        releaseTypeEnum: ReleaseType
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Glide.with(applicationContext)
                    .asBitmap()
                    .load(album.coverUrl)
                    .override(512, 512)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(
                            resource: Bitmap,
                            transition: Transition<in Bitmap>?
                        ) {
                            if (!hasNotificationPermission()) {
                                Log.w(TAG, "Notification permission not granted. Skipping notification.")
                                return
                            }

                            val notification = createNotification(
                                artist = artist,
                                album = album,
                                albumArtBitmap = resource,
                                releaseTypeEnum = releaseTypeEnum,
                                channelId = RELEASE_CHANNEL_ID
                            )

                            try {
                                NotificationManagerCompat.from(applicationContext)
                                    .notify(releaseHash, notification)
                            } catch (e: SecurityException) {
                                Log.e(TAG, "SecurityException while sending notification", e)
                                return
                            }

                            CoroutineScope(Dispatchers.IO).launch {
                                db.savedArtistDao().markNotificationAsSent(
                                    SentNotification(releaseHash, releaseDate)
                                )
                            }
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {}

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            if (!hasNotificationPermission()) {
                                Log.w(TAG, "Notification permission not granted. Skipping fallback notification.")
                                return
                            }

                            val fallback = createNotification(
                                artist = artist,
                                album = album,
                                albumArtBitmap = null,
                                releaseTypeEnum = releaseTypeEnum,
                                channelId = RELEASE_CHANNEL_ID
                            )

                            try {
                                NotificationManagerCompat.from(applicationContext)
                                    .notify(releaseHash, fallback)
                            } catch (e: SecurityException) {
                                Log.e(TAG, "SecurityException while sending fallback notification", e)
                            }
                        }
                    })
            } catch (e: Exception) {
                Log.e(TAG, "Error loading artwork or sending notification", e)
            }
        }
    }

    private fun createNotification(
        artist: SavedArtist,
        album: UnifiedAlbum,
        albumArtBitmap: Bitmap?,
        releaseTypeEnum: ReleaseType,
        channelId: String
    ): Notification {
        val releaseTypeString = when (releaseTypeEnum) {
            ReleaseType.ALBUM -> applicationContext.getString(R.string.release_type_album)
            ReleaseType.SINGLE -> applicationContext.getString(R.string.release_type_single)
            ReleaseType.EP -> applicationContext.getString(R.string.release_type_ep)
            ReleaseType.DEFAULT -> applicationContext.getString(R.string.release_type_default)
        }

        val notificationTitle = when (releaseTypeEnum) {
            ReleaseType.ALBUM -> applicationContext.getString(
                R.string.notification_new_release_title_album,
                artist.name,
                releaseTypeString
            )
            ReleaseType.SINGLE -> applicationContext.getString(
                R.string.notification_new_release_title_single,
                artist.name,
                releaseTypeString
            )
            ReleaseType.EP -> applicationContext.getString(
                R.string.notification_new_release_title_ep,
                artist.name,
                releaseTypeString
            )
            ReleaseType.DEFAULT -> applicationContext.getString(
                R.string.notification_new_release_title_default,
                artist.name,
                releaseTypeString
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

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("\\s*-\\s*(Single|EP|Album)\$", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun determineAccurateReleaseType(album: UnifiedAlbum): ReleaseType {
        val rawTitle = album.rawTitle?.lowercase(Locale.getDefault()) ?: ""
        val rawType = album.rawReleaseType?.lowercase(Locale.getDefault()) ?: ""

        return when {
            rawType.contains("single") || rawTitle.contains("single") -> ReleaseType.SINGLE
            rawType.contains("ep") || rawTitle.contains("ep") -> ReleaseType.EP
            rawType.contains("album") || rawTitle.contains("album") -> ReleaseType.ALBUM
            else -> ReleaseType.from(album.releaseType)
        }
    }

}