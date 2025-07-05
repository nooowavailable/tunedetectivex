package com.dev.tunedetectivex

import java.net.InetAddress

object DnsCache {
    private val dnsCache = mutableMapOf<String, InetAddress>()

    fun get(hostname: String): InetAddress? {
        return dnsCache[hostname]
    }

    fun put(hostname: String, address: InetAddress) {
        dnsCache[hostname] = address
    }

    fun clear() {
        dnsCache.clear()
    }
}
