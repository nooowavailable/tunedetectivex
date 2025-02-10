package com.dev.tunedetectivex

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device rebooted. Setting up WorkManager for FetchReleasesWorker.")

            Toast.makeText(context, "TDX", Toast.LENGTH_LONG).show()

            val sharedPreferences =
                context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val fetchInterval = sharedPreferences.getInt("fetchInterval", 90)

            WorkManagerUtil.setupFetchReleasesWorker(context, fetchInterval)
        }
    }
}