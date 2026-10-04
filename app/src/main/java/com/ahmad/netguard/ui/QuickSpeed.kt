package com.ahmad.netguard.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Dashboard ke liye chhota auto speed test (sirf download, ~4 second). */
object QuickSpeed {

    @Volatile var lastMbps: Double? = null
    @Volatile var lastAt: Long = 0L
    @Volatile var running: Boolean = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun isFresh(): Boolean =
        lastMbps != null && System.currentTimeMillis() - lastAt < 5 * 60 * 1000L

    fun format(v: Double?): String = when {
        v == null -> "—"
        v >= 100 -> String.format("%.0f Mbps", v)
        else -> String.format("%.1f Mbps", v)
    }

    suspend fun run(seconds: Double = 4.0): Double? {
        if (running) return lastMbps
        running = true
        try {
            val result = withContext(Dispatchers.IO) {
                coroutineScope {
                    val bytes = AtomicLong(0)
                    val start = System.nanoTime()
                    val deadline = start + (seconds * 1e9).toLong()
                    val workers = (1..3).map {
                        async(Dispatchers.IO) {
                            val buf = ByteArray(64 * 1024)
                            while (isActive && System.nanoTime() < deadline) {
                                try {
                                    val req = Request.Builder()
                                        .url("https://speed.cloudflare.com/__down?bytes=25000000")
                                        .build()
                                    client.newCall(req).execute().use { resp ->
                                        val s = resp.body?.byteStream() ?: return@use
                                        while (isActive && System.nanoTime() < deadline) {
                                            val n = s.read(buf)
                                            if (n < 0) break
                                            bytes.addAndGet(n.toLong())
                                        }
                                    }
                                } catch (e: Exception) {
                                    break
                                }
                            }
                        }
                    }
                    workers.awaitAll()
                    val el = (System.nanoTime() - start) / 1e9
                    if (el <= 0 || bytes.get() == 0L) null else bytes.get() * 8.0 / el / 1e6
                }
            }
            if (result != null) {
                lastMbps = result
                lastAt = System.currentTimeMillis()
            }
            return result
        } finally {
            running = false
        }
    }
}
