package com.dev.tunedetectivex

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BackgroundJobReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("BackgroundJobReceiver", "Received broadcast to schedule background job.")
        context?.let {
            val sharedPreferences = it.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val fetchInterval = sharedPreferences.getInt("fetchInterval", 90)
            WorkManagerUtil.setupFetchReleasesWorker(it, fetchInterval)
            Log.d("BackgroundJobReceiver", "Background job scheduled.")
        }
    }
}