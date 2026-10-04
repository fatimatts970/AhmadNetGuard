package com.ahmad.netguard.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Dashboard ka auto speed test: real download + upload (Cloudflare servers). */
object QuickSpeed {

    @Volatile var lastDown: Double? = null
    @Volatile var lastUp: Double? = null
    @Volatile var lastAt: Long = 0L
    @Volatile var running: Boolean = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    fun isFresh(): Boolean =
        lastDown != null && System.currentTimeMillis() - lastAt < 5 * 60 * 1000L

    private fun fmt(v: Double): String = if (v >= 100) String.format("%.0f", v) else String.format("%.1f", v)

    /** "↓ 12.4  ↑ 3.1 Mbps" */
    fun summary(): String {
        val d = lastDown
        val u = lastUp
        return when {
            d == null -> "Tap to test"
            u == null -> "↓ ${fmt(d)} Mbps"
            else -> "↓ ${fmt(d)}  ↑ ${fmt(u)} Mbps"
        }
    }

    suspend fun run(onStage: (String) -> Unit = {}): Boolean {
        if (running) return false
        running = true
        try {
            val down = measureDown(3.5)
            if (down != null) {
                lastDown = down
                lastUp = null
                lastAt = System.currentTimeMillis()
                onStage(summary())
            }
            val up = measureUp(3.0)
            if (up != null) lastUp = up
            return down != null
        } finally {
            running = false
        }
    }

    private suspend fun measureDown(seconds: Double): Double? = withContext(Dispatchers.IO) {
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

    private suspend fun measureUp(seconds: Double): Double? = withContext(Dispatchers.IO) {
        coroutineScope {
            val bytes = AtomicLong(0)
            val start = System.nanoTime()
            val deadline = start + (seconds * 1e9).toLong()
            val chunk = ByteArray(32 * 1024)
            val workers = (1..3).map {
                async(Dispatchers.IO) {
                    while (isActive && System.nanoTime() < deadline) {
                        try {
                            val body = object : RequestBody() {
                                override fun contentType() = "application/octet-stream".toMediaType()
                                override fun contentLength(): Long = 2L * 1024 * 1024
                                override fun writeTo(sink: BufferedSink) {
                                    var left = contentLength()
                                    while (left > 0 && System.nanoTime() < deadline) {
                                        val n = minOf(left, chunk.size.toLong()).toInt()
                                        sink.write(chunk, 0, n)
                                        sink.flush()
                                        bytes.addAndGet(n.toLong())
                                        left -= n
                                    }
                                }
                            }
                            val req = Request.Builder().url("https://speed.cloudflare.com/__up").post(body).build()
                            client.newCall(req).execute().use { it.body?.bytes() }
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
}
