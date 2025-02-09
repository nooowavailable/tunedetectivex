package com.dev.tunedetectivex

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BackgroundJobReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("BackgroundJobReceiver", "Received broadcast to schedule background job.")
        context?.let {
            val workRequest = OneTimeWorkRequestBuilder<FetchReleasesWorker>().build()
            WorkManager.getInstance(it).enqueue(workRequest)
            Log.d("BackgroundJobReceiver", "Background job scheduled.")
        }
    }
}