package com.dev.tunedetectivex

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class FetchReleasesWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    companion object {
        private const val FETCH_CHANNEL_ID = "fetch_channel"
        private const val RELEASE_CHANNEL_ID = "release_channel"
        private const val TAG = "FetchReleasesWorker"
        const val DEBUG_CHANNEL_ID = "debug_channel"
        const val DEBUG_CHANNEL_NAME = "Worker Debug Status"

        private const val FUTURE_RELEASE_MONTHS_KEY = "futureReleaseMonths"
    }

    private val db = AppDatabase.getDatabase(context)
    private val apiService: DeezerApiService
    private val iTunesApiService: ITunesApiService

    init {
        val safeClient = OkHttpClient.Builder()
            .dns(SafeDns)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .client(safeClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(DeezerApiService::class.java)

        iTunesApiService = Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(safeClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ITunesApiService::class.java)

        createNotificationChannels()
    }

    override suspend fun doWork(): Result {
        return supervisorScope {
            Log.i(TAG, "🔄 FetchReleasesWorker started at ${System.currentTimeMillis()}")

            val prefs = applicationContext.getSharedPreferences("AppPreferences", MODE_PRIVATE)
            val fetchIntervalMinutes = getFetchIntervalMinutes(prefs)
            val now = System.currentTimeMillis()

            if (!hasNotificationPermission()) {
                Log.w(TAG, "⛔ Notification permission not granted. Skipping work.")
                updateWorkerPrefs(prefs, now, fetchIntervalMinutes, "failure_no_permission")
                return@supervisorScope Result.failure()
            }


            val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(applicationContext)
            val isNetworkRequestsAllowed = WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkPreference)
            Log.d(TAG, "🌐 Network preference: $networkPreference → allowed=$isNetworkRequestsAllowed")

            if (!isNetworkRequestsAllowed) {
                Log.w(TAG, "🚫 Selected network type '$networkPreference' is not available. Skipping.")
                updateWorkerPrefs(prefs, now, fetchIntervalMinutes, "failure_no_network")
                return@supervisorScope Result.failure()
            }

            val fetchDelayMillis = prefs.getInt("fetchDelay", 1).coerceAtLeast(0) * 1000L
            val fetchBatchSize = prefs.getInt("fetchBatchSize", 10).coerceAtLeast(1)

            Log.i(TAG, "🎯 Starting fetch with batchSize=$fetchBatchSize, delay=${fetchDelayMillis}ms")

            try {
                withTimeout(60_000L) {
                    withContext(Dispatchers.IO) {
                        fetchSavedArtists(fetchBatchSize, fetchDelayMillis)
                    }
                }

                WorkManagerUtil.updateLastSuccessfulWorkerRun(applicationContext)

                val lastRunTimestamp = System.currentTimeMillis()
                updateWorkerPrefs(prefs, lastRunTimestamp, fetchIntervalMinutes, "success")

                WorkManagerUtil.enqueueSmartWorker(applicationContext, fetchIntervalMinutes.toLong())

                Log.i(TAG, "✅ Worker finished successfully.")
                Result.success()
            } catch (e: CancellationException) {
                Log.w(TAG, "🛑 Worker was cancelled: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in FetchReleasesWorker: ${e.message}", e)
                val errorTime = System.currentTimeMillis()
                updateWorkerPrefs(prefs, errorTime, fetchIntervalMinutes, "failure_exception")
                return@supervisorScope Result.retry()
            }
        }
    }

    private fun updateWorkerPrefs(prefs: SharedPreferences, timestamp: Long, intervalMinutes: Int, status: String) {
        val nextFetchTimeMillis = timestamp + intervalMinutes * 60_000L
        prefs.edit {
            putLong("lastWorkerRunTimestamp", timestamp)
            putLong("nextFetchTimeMillis", nextFetchTimeMillis)
            putString("last_worker_status", status)
        }
        showWorkerStatusNotification(applicationContext, status, timestamp, nextFetchTimeMillis)
    }

    private fun getFetchIntervalMinutes(prefs: SharedPreferences): Int {
        return prefs.getInt("fetchInterval", 720)
    }

    private fun showWorkerStatusNotification(context: Context, status: String, lastRunMillis: Long, nextFetchMillis: Long) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "No notification permission, status notification not displayed.")
            return
        }

        val formattedLastRun = android.text.format.DateFormat.format("dd.MM.yyyy HH:mm", lastRunMillis)
        val formattedNextFetch = android.text.format.DateFormat.format("dd.MM.yyyy HH:mm", nextFetchMillis)

        val contentText = "status: $status, last: $formattedLastRun, next fetch: $formattedNextFetch"

        val notification = NotificationCompat.Builder(context, DEBUG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_work)
            .setContentTitle("FetchReleasesWorker Status")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .build()

        NotificationManagerCompat.from(context).notify(1440, notification)
    }


    private suspend fun fetchSavedArtists(batchSize: Int, fetchDelayMillis: Long) = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(applicationContext)

        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkPreference)) {
            Log.w(TAG, "Aborting fetch: network type ($networkPreference) not available.")
            return@withContext
        }

        val totalArtistsCount = db.savedArtistDao().getCount()
        var offset = prefs.getInt("artistFetchOffset", 0)
        val failedArtists = mutableListOf<SavedArtist>()

        while (offset < totalArtistsCount) {
            val savedArtists = db.savedArtistDao().getAllWithNotificationsEnabled()
                .drop(offset)
                .take(batchSize)

            if (savedArtists.isEmpty()) break

            Log.d(TAG, "Processing offset=$offset, batch size=${savedArtists.size}")

            supervisorScope {
                savedArtists.forEach { artist ->
                    launch {
                        try {
                            withTimeout(30_000L) {
                                checkForNewRelease(artist)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to fetch for ${artist.name}: ${e.message}")
                            synchronized(failedArtists) { failedArtists.add(artist) }
                        }
                    }
                }
            }

            if (offset + batchSize < totalArtistsCount) {
                Log.d(TAG, "Delaying $fetchDelayMillis ms")
                delay(fetchDelayMillis)
            }

            offset += batchSize
            prefs.edit { putInt("artistFetchOffset", offset) }
        }

        prefs.edit { putInt("artistFetchOffset", 0) }

        if (failedArtists.isNotEmpty()) {
            Log.w(TAG, "Retrying ${failedArtists.size} failed artists")
            retryFailedArtists(failedArtists)
        }
    }

    private suspend fun retryFailedArtists(failedArtists: MutableList<SavedArtist>) {
        Log.w(TAG, "Retrying ${failedArtists.size} failed artists up to 2 more times...")

        repeat(2) { retryRound ->
            val stillFailing = mutableListOf<SavedArtist>()

            supervisorScope {
                for (artist in failedArtists) {
                    launch {
                        var success = false
                        for (attempt in 1..3) {
                            try {
                                Log.d(TAG, "⏱ Retry round ${retryRound + 1}, attempt $attempt for ${artist.name}")
                                withTimeout(30_000L) {
                                    checkForNewRelease(artist)
                                }
                                success = true
                                break
                            } catch (_: TimeoutCancellationException) {
                                Log.w(TAG, "⏳ Timeout for ${artist.name} (attempt $attempt)")
                            } catch (e: Exception) {
                                Log.w(TAG, "❌ Retry ${retryRound + 1} failed for ${artist.name} (attempt $attempt): ${e.message}")
                            }
                        }
                        if (!success) {
                            stillFailing.add(artist)
                        }
                    }
                }
            }

            if (stillFailing.isEmpty()) {
                Log.i(TAG, "✅ All previously failed artists succeeded in retry round ${retryRound + 1}.")
                return
            }

            failedArtists.clear()
            failedArtists.addAll(stillFailing)
        }

        if (failedArtists.isNotEmpty()) {
            val names = failedArtists.joinToString(separator = "\n") { "• ${it.name}" }
            val errorMessage = applicationContext.getString(R.string.error_failed_artists_message, names)
            Log.e(TAG, errorMessage)
        }
    }

    private suspend fun checkForNewRelease(artist: SavedArtist) {
        val sharedPreferences =
            applicationContext.getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(applicationContext)

        if (!WorkManagerUtil.isSelectedNetworkTypeAvailable(applicationContext, networkPreference)) {
            Log.w(TAG, "Aborting fetch: network type ($networkPreference) not available.")
            return
        }

        val maxReleaseAgeInWeeks = sharedPreferences.getInt("releaseAgeWeeks", 4)
        val maxReleaseAgeInMillis = TimeUnit.DAYS.toMillis(maxReleaseAgeInWeeks.toLong() * 7)
        val currentTime = System.currentTimeMillis()

        val currentCalendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = currentTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentTimeStartOfDayMillis = currentCalendar.timeInMillis

        val isItunesSupportEnabled = sharedPreferences.getBoolean("itunesSupportEnabled", false)

        val futureReleaseMonths = sharedPreferences.getInt(FUTURE_RELEASE_MONTHS_KEY, 0)


        if (!hasNotificationPermission()) {
            Log.w(TAG, "Notification permission not granted.")
            return
        }

        val releases = mutableListOf<UnifiedAlbum>()

        var apiSource: String? = null

        try {
            val deezerResponse =
                apiService.getArtistReleases(artist.deezerId ?: return, 0).execute()
            if (deezerResponse.isSuccessful) {
                val deezerAlbums = deezerResponse.body()?.data ?: emptyList()

                val validDeezerAlbums = deezerAlbums.filter { album ->
                    album.id != 0L && !album.title.isNullOrBlank()
                }
                if (deezerAlbums.size != validDeezerAlbums.size) {
                    Log.w(TAG, "Filtered out ${deezerAlbums.size - validDeezerAlbums.size} invalid Deezer albums for artist ${artist.name} (Deezer ID: ${artist.deezerId}).")
                }

                releases += validDeezerAlbums.mapNotNull {
                    val artistName = it.artist?.name ?: artist.name
                    UnifiedAlbum(
                        id = "${artist.deezerId}_${it.id}",
                        title = cleanTitle(it.title),
                        artistName = artistName,
                        releaseDate = it.release_date,
                        coverUrl = it.getBestCoverUrl(),
                        releaseType = it.record_type,
                        deezerId = it.id,
                        rawTitle = it.title,
                        rawReleaseType = it.record_type
                    )
                }
            } else {
                val errorBody = deezerResponse.errorBody()?.string()
                Log.e(TAG, "Deezer API Error for artist ${artist.name} (ID: ${artist.deezerId}): " +
                        "Code=${deezerResponse.code()}, Message=${deezerResponse.message()}, ErrorBody: $errorBody")
            }

            if (isItunesSupportEnabled && artist.itunesId != null) {
                val itunesResponse = iTunesApiService.lookupArtistWithAlbums(artist.itunesId).execute()
                if (itunesResponse.isSuccessful) {
                    val items = itunesResponse.body()?.results ?: emptyList()
                    val albums = items.filter {
                        it.wrapperType == "collection" && it.collectionName != null
                    }
                    releases += albums.mapNotNull {
                        UnifiedAlbum(
                            id = "${artist.itunesId}_${it.collectionId}",
                            title = cleanTitle(it.collectionName ?: return@mapNotNull null),
                            artistName = it.artistName ?: artist.name,
                            releaseDate = it.releaseDate?.substring(0, 10) ?: return@mapNotNull null,
                            coverUrl = it.getHighResArtwork() ?: "",
                            releaseType = it.collectionType,
                            itunesId = it.collectionId,
                            rawTitle = it.collectionName,
                            rawReleaseType = it.collectionType
                        )
                    }
                } else {
                    val errorBody = itunesResponse.errorBody()?.string()
                    Log.e(TAG, "iTunes API Error for artist ${artist.name} (ID: ${artist.itunesId}): " +
                            "Code=${itunesResponse.code()}, Message=${itunesResponse.message()}, ErrorBody: $errorBody")
                }
            }

            val latest = releases.maxByOrNull {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.releaseDate)?.time
                    ?: 0L
            } ?: return

            apiSource = when {
                latest.deezerId != null -> "Deezer"
                latest.itunesId != null -> "iTunes"
                else -> null
            }


            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            dateFormat.timeZone = TimeZone.getDefault()

            val releaseDateMillis = dateFormat.parse(latest.releaseDate)?.time ?: run {
                Log.e(TAG, "Failed to parse release date: ${latest.releaseDate} for ${latest.title}")
                return
            }

            val releaseCalendar = Calendar.getInstance(TimeZone.getDefault()).apply {
                timeInMillis = releaseDateMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val releaseDateStartOfDayMillis = releaseCalendar.timeInMillis

            val shouldNotify: Boolean

            val isFutureRelease = releaseDateStartOfDayMillis > currentTimeStartOfDayMillis

            if (!isFutureRelease) {
                shouldNotify = (currentTime - releaseDateMillis) <= maxReleaseAgeInMillis
                Log.d(TAG, "Release '${latest.title}' is in the past/present. Condition: (current_actual_time - release_actual_time) <= maxReleaseAgeInMillis -> $shouldNotify. " +
                        "Diff: ${(currentTime - releaseDateMillis) / (1000 * 60 * 60 * 24)} days. Max Age: ${maxReleaseAgeInWeeks * 7} days.")
            } else {
                if (futureReleaseMonths > 0) {
                    val futureNotificationCutoff = Calendar.getInstance(TimeZone.getDefault()).apply {
                        timeInMillis = currentTime
                        add(Calendar.MONTH, futureReleaseMonths)
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }

                    shouldNotify = releaseDateStartOfDayMillis <= futureNotificationCutoff.timeInMillis
                    Log.d(TAG, "Release '${latest.title}' is in the future. Condition: releaseDateStartOfDayMillis <= futureNotificationCutoff.timeInMillis -> $shouldNotify. " +
                            "Release Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(releaseDateStartOfDayMillis)}, Notification Cutoff: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(futureNotificationCutoff.timeInMillis)}")
                } else {
                    shouldNotify = false
                    Log.d(TAG, "Release '${latest.title}' is in the future, but futureReleaseMonths is 0 (future notifications disabled).")
                }
            }

            if (!shouldNotify) {
                Log.d(TAG, "Skipping '${latest.title}': Does not meet notification criteria (too old or not within future notification window).")
                return
            }

            val releaseHash = "${latest.artistName.trim().lowercase()}_${
                latest.title.trim().lowercase()
            }_${latest.releaseDate}".hashCode()
            if (db.savedArtistDao().isNotificationSent(releaseHash)) {
                Log.d(TAG, "Already notified for '${latest.title}' (Hash: $releaseHash)")
                return
            }

            val releaseTypeEnum = determineAccurateReleaseType(latest)

            sendUnifiedReleaseNotification(
                artist,
                latest,
                releaseHash,
                releaseDateMillis,
                releaseTypeEnum,
                isFutureRelease,
                apiSource
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error checking release for ${artist.name}: ${e.message}", e)
            throw e
        }
    }
    private fun sendUnifiedReleaseNotification(
        artist: SavedArtist,
        album: UnifiedAlbum,
        releaseHash: Int,
        releaseDate: Long,
        releaseTypeEnum: ReleaseType,
        isFutureRelease: Boolean,
        apiSource: String?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestOptions = RequestOptions()
                    .override(1024, 1024)
                    .transform(CenterCrop())

                Glide.with(applicationContext)
                    .asBitmap()
                    .load(album.coverUrl)
                    .apply(requestOptions)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(
                            resource: Bitmap,
                            transition: Transition<in Bitmap>?
                        ) {
                            Log.d(TAG, "Bitmap dimensions for ${album.title}: ${resource.width}x${resource.height}")

                            if (!hasNotificationPermission()) {
                                Log.w(
                                    TAG,
                                    "Notification permission not granted. Skipping notification."
                                )
                                return
                            }

                            val notification = createNotification(
                                artist = artist,
                                album = album,
                                albumArtBitmap = resource,
                                releaseTypeEnum = releaseTypeEnum,
                                channelId = RELEASE_CHANNEL_ID,
                                isFutureRelease = isFutureRelease,
                                apiSource = apiSource
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
                                Log.w(
                                    TAG,
                                    "Notification permission not granted. Skipping fallback notification."
                                )
                                return
                            }

                            val fallback = createNotification(
                                artist = artist,
                                album = album,
                                albumArtBitmap = null,
                                releaseTypeEnum = releaseTypeEnum,
                                channelId = RELEASE_CHANNEL_ID,
                                isFutureRelease = isFutureRelease,
                                apiSource = apiSource
                            )

                            try {
                                NotificationManagerCompat.from(applicationContext)
                                    .notify(releaseHash, fallback)
                            } catch (e: SecurityException) {
                                Log.e(
                                    TAG,
                                    "SecurityException while sending fallback notification",
                                    e
                                )
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
        channelId: String,
        isFutureRelease: Boolean,
        apiSource: String?
    ): Notification {
        val releaseTypeString = when (releaseTypeEnum) {
            ReleaseType.ALBUM -> applicationContext.getString(R.string.release_type_album)
            ReleaseType.SINGLE -> applicationContext.getString(R.string.release_type_single)
            ReleaseType.EP -> applicationContext.getString(R.string.release_type_ep)
            ReleaseType.DEFAULT -> applicationContext.getString(R.string.release_type_default)
        }

        val notificationTitle = if (isFutureRelease) {
            when (releaseTypeEnum) {
                ReleaseType.ALBUM -> applicationContext.getString(
                    R.string.notification_upcoming_release_title_album,
                    artist.name,
                    releaseTypeString
                )
                ReleaseType.SINGLE -> applicationContext.getString(
                    R.string.notification_upcoming_release_title_single,
                    artist.name,
                    releaseTypeString
                )
                ReleaseType.EP -> applicationContext.getString(
                    R.string.notification_upcoming_release_title_ep,
                    artist.name,
                    releaseTypeString
                )
                ReleaseType.DEFAULT -> applicationContext.getString(
                    R.string.notification_upcoming_release_title_default,
                    artist.name,
                    releaseTypeString
                )
            }
        } else {
            when (releaseTypeEnum) {
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
        }

        val notificationText = applicationContext.getString(
            R.string.notification_release_text,
            artist.name,
            album.title
        )

        val intent = Intent(applicationContext, ReleaseDetailsActivity::class.java).apply {
            putExtra("releaseId", album.deezerId ?: album.itunesId ?: -1L)
            putExtra("deezerId", album.deezerId ?: -1L)
            putExtra("itunesId", album.itunesId ?: -1L)
            putExtra("apiSource", apiSource)
            putExtra("releaseTitle", album.title)
            putExtra("artistName", artist.name)
            putExtra("albumArtUrl", album.coverUrl)
            putExtra("releaseDate", album.releaseDate)

            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext,
            album.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

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
        val fetchChannel = NotificationChannel(
            FETCH_CHANNEL_ID,
            "Fetch Operations",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for background fetch operations."
        }

        val releaseChannel = NotificationChannel(
            RELEASE_CHANNEL_ID,
            "New Releases",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for new music releases."
        }

        val debugChannel = NotificationChannel(
            DEBUG_CHANNEL_ID,
            DEBUG_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the status of work-manager."
            setShowBadge(false)
        }

        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannels(listOf(fetchChannel, releaseChannel, debugChannel))
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("\\s*-\\s*(Single|EP|Album)\$", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
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