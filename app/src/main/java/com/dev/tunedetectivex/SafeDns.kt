package com.dev.tunedetectivex

import android.util.Log
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

object SafeDns : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        DnsCache.get(hostname)?.let { return listOf(it) }

        var attempt = 0
        var delayMs = 500L

        while (attempt < 5) {
            try {
                val addresses = Dns.SYSTEM.lookup(hostname)
                if (addresses.isNotEmpty()) {
                    DnsCache.put(hostname, addresses.first())
                    return addresses
                }
            } catch (e: UnknownHostException) {
                Log.w("SafeDns", "DNS failed for $hostname on attempt $attempt: ${e.message}")
            }

            attempt++
            try {
                Thread.sleep(delayMs.coerceAtMost(60_000))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw UnknownHostException("DNS lookup interrupted for $hostname")
            }

            delayMs *= 2
        }

        throw UnknownHostException("DNS lookup failed after $attempt attempts for $hostname")
    }
}
