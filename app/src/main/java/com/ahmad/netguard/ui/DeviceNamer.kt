package com.ahmad.netguard.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Device ka behtar naam: router hostname ko saaf karna, aur zarurat par reverse-DNS. */
object DeviceNamer {

    private val cache = ConcurrentHashMap<String, String>()
    private val pool = Executors.newFixedThreadPool(4)

    fun isGeneric(name: String?): Boolean {
        val n = name?.trim().orEmpty()
        return n.isEmpty() || n.startsWith("Unknown", true) || n.equals("Blocked device", true)
    }

    /** "android-b7ed99534a20c1ed" jaisa hostname -> "Android Phone". */
    fun pretty(name: String?): String {
        val n = name?.trim().orEmpty()
        val lower = n.lowercase()
        return when {
            isGeneric(n) -> "Mobile Device"
            Regex("^android[-_][0-9a-f]{6,}$").matches(lower) -> "Android Phone"
            Regex("^(iphone|ipad)[-_ ]?[0-9a-f]{0,8}$").matches(lower) -> if (lower.startsWith("ipad")) "iPad" else "iPhone"
            lower.startsWith("desktop-") || lower.startsWith("laptop-") -> "Windows PC"
            else -> n
        }
    }

    suspend fun reverseDns(ip: String): String? = withContext(Dispatchers.IO) {
        val cached = cache[ip]
        if (cached != null) return@withContext cached.ifEmpty { null }
        val fut = pool.submit(Callable<String?> {
            try {
                val h = InetAddress.getByName(ip).canonicalHostName
                if (h == ip || h.isBlank()) null else h.substringBefore('.')
            } catch (e: Exception) {
                null
            }
        })
        val r = try {
            fut.get(1200, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            fut.cancel(true)
            null
        }
        cache[ip] = r ?: ""
        r
    }
}
