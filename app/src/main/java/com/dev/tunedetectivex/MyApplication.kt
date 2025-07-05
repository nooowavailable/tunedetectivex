package com.dev.tunedetectivex

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MyApplication : Application() {

    companion object {
        private const val PREFS_NAME = "DnsCachePrefs"
        private const val KEY_LAST_CLEAR_TIME = "last_clear_time"
        private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
        private const val TAG = "MyApplication"
        private val hostsToResolve = listOf("api.deezer.com", "itunes.apple.com")

    }

    override fun onCreate() {
        super.onCreate()

        DynamicColors.applyToActivitiesIfAvailable(this)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastClearTime = prefs.getLong(KEY_LAST_CLEAR_TIME, 0L)
        val now = System.currentTimeMillis()
        val db = AppDatabase.getDatabase(this)
        val dnsDao = db.dnsCacheDao()

        CoroutineScope(Dispatchers.IO).launch {
            if (now - lastClearTime > SEVEN_DAYS_MILLIS) {
                dnsDao.clearOld(now - SEVEN_DAYS_MILLIS)
                prefs.edit { putLong(KEY_LAST_CLEAR_TIME, now) }
            }

            var allGood = true
            for (host in hostsToResolve) {
                val cached = dnsDao.get(host)
                val isCacheValid = cached != null && (now - cached.timestamp) < TimeUnit.DAYS.toMillis(1)

                if (isCacheValid) {
                    Log.i(TAG, "DNS (cache): $host → ${cached.ip} (valid)")
                } else {
                    try {
                        val resolved = SafeDns.lookup(host)
                        val ip = resolved.firstOrNull()?.hostAddress
                        if (ip != null) {
                            dnsDao.put(DnsEntry(host, ip, now))
                            Log.i(TAG, "DNS resolved $host to: $ip")
                        } else {
                            Log.w(TAG, "DNS resolution returned empty or null IP for $host")
                            allGood = false
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "DNS failed for $host: ${e.message}", e)
                        allGood = false
                    }
                }
            }

            prefs.edit {
                putBoolean("dns_cache_ok", allGood)
                putLong(KEY_LAST_CLEAR_TIME, now)
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            WorkManagerUtil.reEnqueueIfMissing(this)
        }
    }
}