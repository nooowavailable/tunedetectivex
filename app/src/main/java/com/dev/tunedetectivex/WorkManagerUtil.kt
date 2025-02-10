package com.dev.tunedetectivex

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkManagerUtil {

    fun setupFetchReleasesWorker(context: Context, intervalInMinutes: Int) {
        val sharedPreferences = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Both")

        val constraintsBuilder = Constraints.Builder()

        when (networkType) {
            "Wi-Fi Only" -> constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
            "Mobile Data Only" -> constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
            "Both" -> constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
        }

        val constraints = constraintsBuilder
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<FetchReleasesWorker>(
            intervalInMinutes.toLong(), TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "FetchReleasesWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}