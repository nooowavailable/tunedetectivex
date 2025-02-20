package com.dev.tunedetectivex

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkManagerUtil {

    private const val PREFS_NAME = "AppPreferences"
    private const val NETWORK_TYPE_KEY = "networkType"
    private const val TAG = "WorkManagerUtil"
    private const val WORK_NAME = "FetchReleasesWork"

    fun setupFetchReleasesWorker(context: Context, intervalInMinutes: Int) {
        val constraints = createConstraints(context)

        if (constraints == null) {
            Log.w(TAG, "Worker not scheduled due to lack of valid constraints.")
            return
        }

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
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        return when (selectedType) {
            "Wi-Fi Only" -> networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            "Mobile Data Only" -> networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            "Any" -> networkCapabilities != null && (
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    )

            else -> {
                Log.w(
                    "WorkManagerUtil",
                    "Unknown network type: $selectedType. Defaulting to 'Any'."
                )
                true
            }
        }
    }

    private fun createConstraints(context: Context): Constraints? {
        if (!isNetworkConnected(context)) {
            Log.w(TAG, "No network connected. Cannot create constraints for FetchReleasesWorker.")
            return null
        }

        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val networkType = sharedPreferences.getString(NETWORK_TYPE_KEY, "Any")

        val constraintsBuilder = Constraints.Builder()
            .setRequiresBatteryNotLow(true)

        when (networkType) {
            "Wi-Fi Only" -> constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
            "Mobile Data Only" -> constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
            "Any" -> constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
            else -> {
                Log.w(TAG, "Unknown network type: $networkType. Worker will not be scheduled.")
                return null
            }
        }

        return constraintsBuilder.build()
    }

    private fun isNetworkConnected(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities != null && (
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                )
    }
}