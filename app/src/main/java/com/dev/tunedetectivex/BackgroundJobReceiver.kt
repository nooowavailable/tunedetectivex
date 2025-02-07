package com.dev.tunedetectivex

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BackgroundJobReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            val workRequest = OneTimeWorkRequestBuilder<FetchReleasesWorker>().build()
            WorkManager.getInstance(it).enqueue(workRequest)
        }
    }
}