package com.dev.tunedetectivex

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit


object WorkManagerUtil {

    private const val PREFS_NAME = "AppPreferences"
    private const val FETCH_INTERVAL_KEY = "fetchInterval"
    private const val LAST_RUN_TIMESTAMP_KEY = "lastWorkerRunTimestamp"
    private const val TAG = "WorkManagerUtil"
    internal const val WORK_NAME = "FetchReleasesWork"

    enum class NetworkPreference(val value: String) {
        WIFI_ONLY("Wi-Fi Only"),
        ANY("Any")
    }

    fun enqueueFetchReleasesWorker(context: Context) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fetchInterval = sharedPreferences.getInt(FETCH_INTERVAL_KEY, 720).coerceAtLeast(15)
        val lastEnqueuedInterval = sharedPreferences.getInt("lastEnqueuedInterval", -1)

        val networkPrefStr = sharedPreferences.getString("networkType", NetworkPreference.ANY.value) ?: NetworkPreference.ANY.value
        val networkPreference = NetworkPreference.values().find { it.value == networkPrefStr } ?: NetworkPreference.ANY
        val networkTypeConstraint = when (networkPreference) {
            NetworkPreference.WIFI_ONLY -> NetworkType.UNMETERED
            NetworkPreference.ANY -> NetworkType.CONNECTED
        }

        if (fetchInterval != lastEnqueuedInterval) {
            Log.d(TAG, "🔄 Interval changed ($lastEnqueuedInterval → $fetchInterval), re-enqueueing worker.")
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WORK_NAME).get()
        val isActive = workInfos.any {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
        }

        if (!isActive || fetchInterval != lastEnqueuedInterval) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(networkTypeConstraint)
                .build()

            val request = PeriodicWorkRequestBuilder<FetchReleasesWorker>(fetchInterval.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )

            sharedPreferences.edit { putInt("lastEnqueuedInterval", fetchInterval) }
            Log.d(TAG, "✅ Worker enqueued with interval $fetchInterval minutes.")
        } else {
            Log.d(TAG, "✅ Worker already active with same interval – skipping re-enqueue.")
        }
    }

    fun enqueueSmartWorker(context: Context, delayMinutes: Long) {
        Log.d(TAG, "📅 Enqueue smart worker with delay: $delayMinutes minutes")

        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val networkPrefStr = sharedPreferences.getString("networkType", NetworkPreference.ANY.value) ?: NetworkPreference.ANY.value
        val networkPreference = NetworkPreference.entries.find { it.value == networkPrefStr } ?: NetworkPreference.ANY

        val networkTypeConstraint = when (networkPreference) {
            NetworkPreference.WIFI_ONLY -> NetworkType.UNMETERED
            NetworkPreference.ANY -> NetworkType.CONNECTED
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(networkTypeConstraint)
            .build()

        val request = OneTimeWorkRequestBuilder<FetchReleasesWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun reEnqueueIfMissing(context: Context) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fetchInterval = sharedPreferences.getInt(FETCH_INTERVAL_KEY, 720).coerceAtLeast(15)
        val lastEnqueuedInterval = sharedPreferences.getInt("lastEnqueuedInterval", -1)

        val workManager = WorkManager.getInstance(context)
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME).get()
        val isAlreadyQueuedOrRunning = workInfos.any {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
        }

        if (!isAlreadyQueuedOrRunning || fetchInterval != lastEnqueuedInterval) {
            Log.d(TAG, "🔁 Worker missing or interval changed – re-enqueueing.")
            enqueueFetchReleasesWorker(context)
        } else {
            Log.d(TAG, "✅ Worker already active or queued – skipping re-enqueue.")
        }
    }

    fun updateLastSuccessfulWorkerRun(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putLong(LAST_RUN_TIMESTAMP_KEY, System.currentTimeMillis())
        }
        Log.d(TAG, "🕒 Updated last successful worker run timestamp.")
    }

    fun getNetworkPreferenceFromPrefs(context: Context): NetworkPreference {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val networkTypeStr = prefs.getString("networkType", "Any") ?: "Any"
        return NetworkPreference.entries
            .find { it.value.equals(networkTypeStr, ignoreCase = true) }
            ?: NetworkPreference.ANY
    }

    fun isSelectedNetworkTypeAvailable(context: Context, selectedType: NetworkPreference): Boolean {
        Log.d(TAG, "🔌 Checking selected network type availability: ${selectedType.value}")

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: run {
                    Log.w(TAG, "⚠️ ConnectivityManager is not available.")
                    return false
                }

        val activeNetwork = connectivityManager.activeNetwork ?: run {
            Log.d(TAG, "📡 No active network found.")
            return false
        }

        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: run {
            Log.d(TAG, "⚠️ No NetworkCapabilities available.")
            return false
        }

        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        Log.d(TAG, "🌐 Network State → Wi-Fi: $isWifi, Cellular: $isCellular, Selected: ${selectedType.value}")

        return when (selectedType) {
            NetworkPreference.WIFI_ONLY -> isWifi
            NetworkPreference.ANY -> isWifi || isCellular
        }
    }
}
