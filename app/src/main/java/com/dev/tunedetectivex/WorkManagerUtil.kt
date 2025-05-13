package com.dev.tunedetectivex

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkManagerUtil {

    private const val PREFS_NAME = "AppPreferences"
    private const val NETWORK_TYPE_KEY = "networkType"
    private const val TAG = "WorkManagerUtil"
    private const val WORK_NAME = "FetchReleasesWork"

    fun setupFetchReleasesWorker(context: Context, intervalInMinutes: Int) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val networkType = sharedPreferences.getString(NETWORK_TYPE_KEY, "Any") ?: "Any"

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(
                when (networkType) {
                    "Wi-Fi Only" -> NetworkType.UNMETERED
                    // Mobile Data Only is not possible and who would use that...
                    "Mobile Data Only", "Any" -> NetworkType.CONNECTED
                    else -> NetworkType.CONNECTED
                }
            )
            .build()

        val workRequest = PeriodicWorkRequestBuilder<FetchReleasesWorker>(
            intervalInMinutes.toLong(), TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Log.d(TAG, "Scheduled FetchReleasesWorker with interval: $intervalInMinutes minutes")
    }

    fun isSelectedNetworkTypeAvailable(context: Context, selectedType: String): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        val isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        Log.d(TAG, "Network check → Wi-Fi: $isWifi | Mobile: $isCellular | Selected: $selectedType")

        return when (selectedType) {
            "Wi-Fi Only" -> isWifi && !isCellular
            "Mobile Data Only" -> isCellular && !isWifi
            "Any" -> isWifi || isCellular
            else -> {
                Log.w(TAG, "Unknown network type: $selectedType. Defaulting to 'Any'.")
                true
            }
        }
    }
}
