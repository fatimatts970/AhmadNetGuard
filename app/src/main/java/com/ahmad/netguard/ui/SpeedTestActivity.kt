package com.ahmad.netguard.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class SpeedTestActivity : NgScreen() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private lateinit var tvDown: TextView
    private lateinit var tvUp: TextView
    private lateinit var tvPhase: TextView
    private lateinit var tvPing: TextView
    private lateinit var btn: TextView
    private lateinit var infoBox: LinearLayout
    private lateinit var statusBox: LinearLayout
    private var testJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("Speed Test") {
            loadInfo()
            loadStatus()
            pull.postDelayed({ pull.done() }, 1200)
        }

        // gauges
        val g = card(18)
        val row = hrow()
        row.weightSum = 2f
        val d = vcol()
        d.gravity = Gravity.CENTER_HORIZONTAL
        tvDown = tv("0.0", 34f, cText(), true)
        d.addView(tvDown)
        d.addView(tv("Mbps", 12f, cSub()))
        val dl = tv("Download", 13f, cAcc(), true)
        dl.setPadding(0, dp(6), 0, 0)
        d.addView(dl)
        val u = vcol()
        u.gravity = Gravity.CENTER_HORIZONTAL
        tvUp = tv("0.0", 34f, cText(), true)
        u.addView(tvUp)
        u.addView(tv("Mbps", 12f, cSub()))
        val ul = tv("Upload", 13f, Color.parseColor("#7C3AED"), true)
        ul.setPadding(0, dp(6), 0, 0)
        u.addView(ul)
        row.addView(d, LinearLayout.LayoutParams(0, wrapP, 1f))
        row.addView(u, LinearLayout.LayoutParams(0, wrapP, 1f))
        g.addView(row)
        tvPing = tv("Ping —", 13f, cSub(), true)
        tvPing.gravity = Gravity.CENTER
        tvPing.setPadding(0, dp(14), 0, 0)
        g.addView(tvPing, LinearLayout.LayoutParams(matchP, wrapP))
        tvPhase = tv("Ready", 12f, cSub())
        tvPhase.gravity = Gravity.CENTER
        tvPhase.setPadding(0, dp(4), 0, 0)
        g.addView(tvPhase, LinearLayout.LayoutParams(matchP, wrapP))
        add(col, g)

        btn = pill("Start Speed Test", cAcc(), true)
        btn.textSize = 15f
        btn.setPadding(dp(14), dp(14), dp(14), dp(14))
        btn.setOnClickListener { startTest() }
        add(col, btn, bottom = 16)

        add(col, section("Device info"), bottom = 0)
        infoBox = card(16)
        add(col, infoBox)

        add(col, section("Live service status"), bottom = 0)
        statusBox = card(16)
        add(col, statusBox, bottom = 0)

        loadInfo()
        loadStatus()
    }

    override fun onDestroy() {
        testJob?.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ speed test

    private fun startTest() {
        if (testJob?.isActive == true) {
            testJob?.cancel()
            return
        }
        btn.text = "Stop"
        tvDown.text = "0.0"
        tvUp.text = "0.0"
        tvPing.text = "Ping —"
        testJob = lifecycleScope.launch {
            try {
                tvPhase.text = "Measuring ping…"
                val ping = measurePing()
                if (ping != null) tvPing.text = "Ping %.0f ms · Jitter %.0f ms".format(ping.first, ping.second)

                tvPhase.text = "Download test…"
                val down = measureDown { mbps -> tvDown.text = "%.1f".format(mbps) }
                tvDown.text = "%.1f".format(down)

                tvPhase.text = "Upload test…"
                val up = measureUp { mbps -> tvUp.text = "%.1f".format(mbps) }
                tvUp.text = "%.1f".format(up)

                tvPhase.text = "Completed"
            } catch (e: Exception) {
                tvPhase.text = if (isActive) "Test failed — check your internet" else "Stopped"
            } finally {
                btn.text = "Start Speed Test"
            }
        }
    }

    private suspend fun measurePing(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val times = ArrayList<Double>()
            repeat(7) {
                val req = Request.Builder().url("https://speed.cloudflare.com/__down?bytes=0").build()
                val t0 = System.nanoTime()
                client.newCall(req).execute().use { it.body?.bytes() }
                times.add((System.nanoTime() - t0) / 1e6)
            }
            val used = times.drop(1).sorted()
            if (used.isEmpty()) return@withContext null
            val median = used[used.size / 2]
            val jitter = used.zipWithNext { a, b -> Math.abs(a - b) }.average()
            median to jitter
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun measureDown(onProgress: (Double) -> Unit): Double = coroutineScope {
        val bytes = AtomicLong(0)
        val seconds = 8.0
        val start = System.nanoTime()
        val deadline = start + (seconds * 1e9).toLong()

        val ticker = launch {
            while (isActive) {
                delay(300)
                val el = (System.nanoTime() - start) / 1e9
                if (el > 0.5) onProgress(bytes.get() * 8.0 / el / 1e6)
            }
        }
        val workers = (1..4).map {
            async(Dispatchers.IO) {
                val buf = ByteArray(64 * 1024)
                while (isActive && System.nanoTime() < deadline) {
                    try {
                        val req = Request.Builder().url("https://speed.cloudflare.com/__down?bytes=25000000").build()
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
        ticker.cancel()
        val el = (System.nanoTime() - start) / 1e9
        if (el <= 0) 0.0 else bytes.get() * 8.0 / el / 1e6
    }

    /** Real upload: sirf poore hue requests ke bytes (server ke jawab ke baad) gine jaate hain. */
    private suspend fun measureUp(onProgress: (Double) -> Unit): Double = coroutineScope {
        val done = AtomicLong(0)
        val lastDone = AtomicLong(0)
        val seconds = 6.0
        val start = System.nanoTime()
        val deadline = start + (seconds * 1e9).toLong()
        val payload = ByteArray(4 * 1024 * 1024)

        val ticker = launch {
            while (isActive) {
                delay(300)
                val el = (System.nanoTime() - start) / 1e9
                if (el > 0.5) onProgress(done.get() * 8.0 / el / 1e6)
            }
        }
        val workers = (1..3).map {
            async(Dispatchers.IO) {
                var size = 256 * 1024
                while (isActive && System.nanoTime() < deadline) {
                    try {
                        val body = payload.toRequestBody("application/octet-stream".toMediaType(), 0, size)
                        val t0 = System.nanoTime()
                        val req = Request.Builder().url("https://speed.cloudflare.com/__up").post(body).build()
                        client.newCall(req).execute().use { it.body?.bytes() }
                        val t1 = System.nanoTime()
                        done.addAndGet(size.toLong())
                        lastDone.accumulateAndGet(t1) { a, b -> maxOf(a, b) }
                        if (t1 - t0 < 400_000_000L && size < 4 * 1024 * 1024) size *= 2
                    } catch (e: Exception) {
                        break
                    }
                }
            }
        }
        workers.awaitAll()
        ticker.cancel()
        val el = (lastDone.get() - start) / 1e9
        if (el <= 0 || done.get() == 0L) 0.0 else done.get() * 8.0 / el / 1e6
    }

    // ------------------------------------------------------------ device info

    private fun loadInfo() {
        infoBox.removeAllViews()
        infoBox.addView(tv("Loading…", 13f, cSub()))
        lifecycleScope.launch {
            val info: Map<String, String> = withContext(Dispatchers.IO) {
                val m = LinkedHashMap<String, String>()
                fun get(url: String): String? = try {
                    client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                        if (r.isSuccessful) r.body?.string() else null
                    }
                } catch (e: Exception) {
                    null
                }
                // 1) Cloudflare meta
                try {
                    val j = JSONObject(get("https://speed.cloudflare.com/meta") ?: "{}")
                    m["ip"] = j.optString("clientIp", "")
                    m["isp"] = j.optString("asOrganization", "")
                    m["city"] = j.optString("city", "")
                    m["region"] = j.optString("region", "")
                    m["country"] = j.optString("country", "")
                    m["colo"] = j.optString("colo", "")
                } catch (e: Exception) {
                }
                // 2) Cloudflare trace (ip / country / colo)
                if (m["ip"].isNullOrBlank()) {
                    val t = get("https://speed.cloudflare.com/cdn-cgi/trace") ?: get("https://www.cloudflare.com/cdn-cgi/trace")
                    t?.lines()?.forEach { line ->
                        val p = line.split("=", limit = 2)
                        if (p.size == 2) when (p[0]) {
                            "ip" -> m["ip"] = p[1]
                            "loc" -> if (m["country"].isNullOrBlank()) m["country"] = p[1]
                            "colo" -> if (m["colo"].isNullOrBlank()) m["colo"] = p[1]
                        }
                    }
                }
                // 3) ISP / region from a public lookup
                if (m["isp"].isNullOrBlank() || m["region"].isNullOrBlank()) {
                    try {
                        val j = JSONObject(get("https://ipwho.is/") ?: "{}")
                        if (j.optBoolean("success", false)) {
                            if (m["ip"].isNullOrBlank()) m["ip"] = j.optString("ip", "")
                            m["isp"] = j.optJSONObject("connection")?.optString("isp", "") ?: ""
                            if (m["city"].isNullOrBlank()) m["city"] = j.optString("city", "")
                            if (m["region"].isNullOrBlank()) m["region"] = j.optString("region", "")
                            if (m["country"].isNullOrBlank()) m["country"] = j.optString("country_code", "")
                        }
                    } catch (e: Exception) {
                    }
                }
                m
            }
            infoBox.removeAllViews()
            fun v(key: String): String = info[key] ?: ""
            if (v("ip").isBlank() && v("country").isBlank()) {
                infoBox.addView(tv("Internet info unavailable right now", 13f, cSub()))
                return@launch
            }
            infoBox.addView(kvRow("IP Address", v("ip")))
            infoBox.addView(divider())
            infoBox.addView(kvRow("ISP", v("isp")))
            infoBox.addView(divider())
            infoBox.addView(kvRow("Region", listOf(v("city"), v("region")).filter { it.isNotBlank() }.joinToString(", ")))
            infoBox.addView(divider())
            infoBox.addView(kvRow("Country", v("country")))
            infoBox.addView(divider())
            infoBox.addView(kvRow("Server", ("Cloudflare " + v("colo")).trim()))
        }
    }

    // ------------------------------------------------------------ service status

    private val services = listOf(
        "Cloudflare" to "https://www.cloudflarestatus.com/api/v2/status.json",
        "Zoom" to "https://www.zoomstatus.com/api/v2/status.json",
        "GitHub" to "https://www.githubstatus.com/api/v2/status.json",
        "Discord" to "https://discordstatus.com/api/v2/status.json",
        "Reddit" to "https://www.redditstatus.com/api/v2/status.json",
        "OpenAI" to "https://status.openai.com/api/v2/status.json",
        "Dropbox" to "https://status.dropbox.com/api/v2/status.json",
        "Twitch" to "https://status.twitch.com/api/v2/status.json"
    )

    private fun loadStatus() {
        statusBox.removeAllViews()
        statusBox.addView(tv("Checking…", 13f, cSub()))
        lifecycleScope.launch {
            val results: List<Pair<String, String>> = withContext(Dispatchers.IO) {
                coroutineScope {
                    services.map { (name, url) ->
                        async {
                            val ind = try {
                                val req = Request.Builder().url(url).build()
                                client.newCall(req).execute().use { r ->
                                    val b = r.body?.string() ?: ""
                                    JSONObject(b).getJSONObject("status").getString("indicator")
                                }
                            } catch (e: Exception) {
                                "unknown"
                            }
                            name to ind
                        }
                    }.awaitAll()
                }
            }
            statusBox.removeAllViews()
            results.forEachIndexed { i, (name, ind) ->
                if (i > 0) statusBox.addView(divider())
                val (text, color) = when (ind) {
                    "none" -> "Operational" to Color.parseColor("#16A34A")
                    "minor" -> "Degraded" to Color.parseColor("#B45309")
                    "major", "critical" -> "Outage" to ThemeManager.danger()
                    else -> "Unknown" to cSub()
                }
                val r = hrow()
                r.setPadding(0, dp(10), 0, dp(10))
                val dot = View(this@SpeedTestActivity)
                val dg = android.graphics.drawable.GradientDrawable()
                dg.shape = android.graphics.drawable.GradientDrawable.OVAL
                dg.setColor(color)
                dot.background = dg
                r.addView(dot, LinearLayout.LayoutParams(dp(9), dp(9)))
                val nm = tv(name, 14f, cText(), true)
                nm.setPadding(dp(12), 0, 0, 0)
                r.addView(nm, LinearLayout.LayoutParams(0, wrapP, 1f))
                r.addView(tv(text, 12f, color, true))
                statusBox.addView(r)
            }
        }
    }
}
