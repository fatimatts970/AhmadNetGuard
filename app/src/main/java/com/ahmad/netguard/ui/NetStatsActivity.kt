package com.ahmad.netguard.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.history.AppDatabase
import com.ahmad.netguard.history.ConnectionEvent
import com.ahmad.netguard.model.Device
import com.ahmad.netguard.network.DeviceNameStore
import com.ahmad.netguard.network.PageReader
import com.ahmad.netguard.network.RouterAdapterFactory
import kotlinx.coroutines.launch

/**
 * Data Usage screen. Sirf real cheezein dikhata hai:
 *  - router ke WiFi radio counters (agar router padhne de)
 *  - har device ka real online time (app ke tracked connect/disconnect events se)
 * Koi andaza ya fake bytes nahi.
 */
class NetStatsActivity : NgScreen() {

    private var rangeIdx = 1
    private var customFrom = 0L
    private var customTo = 0L
    private lateinit var names: DeviceNameStore

    private class Row(val device: Device, val ms: Long, val sessions: Int)

    private var rows: List<Row> = emptyList()
    private var wifiTotals: Pair<Long, Long>? = null
    private var wifiTried = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        names = DeviceNameStore(this)
        setupScreen("Data Usage") { load() }
        load()
    }

    private fun formatBytes(b: Long): String = when {
        b >= 1024L * 1024 * 1024 -> String.format("%.2f GB", b / (1024.0 * 1024 * 1024))
        b >= 1024L * 1024 -> String.format("%.1f MB", b / (1024.0 * 1024))
        b >= 1024L -> String.format("%.0f KB", b / 1024.0)
        else -> "$b B"
    }

    private fun load() {
        col.removeAllViews()
        val loading = card(18)
        loading.addView(tv("Loading…", 14f, cSub()))
        add(col, loading)

        lifecycleScope.launch {
            val ad = RouterAdapterFactory.getAdapter()
            val devs = SessionKeeper.devices()
            val dao = AppDatabase.getInstance(this@NetStatsActivity).connectionEventDao()
            val now = System.currentTimeMillis()
            val (from, to) = rangeBounds(rangeIdx, customFrom, customTo)

            val out = ArrayList<Row>()
            for (d in devs) {
                val events = try { dao.getHistoryForDevice(d.macAddress).reversed() } catch (e: Exception) { emptyList<ConnectionEvent>() }
                val sessions = SessionCalc.build(events, d.isOnline, now)
                out.add(Row(d, SessionCalc.overlap(sessions, from, to), SessionCalc.countIn(sessions, from, to)))
            }
            rows = out.sortedByDescending { it.ms }

            wifiTotals = readWifiCounters()
            wifiTried = true
            render()
            pull.done()
        }
    }

    /** Router ke WiFi radios ke sent/received counters (agar page parse ho jaye). */
    private suspend fun readWifiCounters(): Pair<Long, Long>? {
        val r: PageReader.Result? = try {
            PageReader.fetchFirst(InfoKind.WIFI_STATS.sections.first().second)
        } catch (e: Exception) {
            null
        }
        if (r == null) return null
        val sentRe = Regex("(?i)(?:TotalBytesSent|BytesSent|TxBytes|SentBytes)\\W{1,12}(\\d{3,})")
        val recvRe = Regex("(?i)(?:TotalBytesReceived|BytesReceived|RxBytes|ReceivedBytes)\\W{1,12}(\\d{3,})")
        val sent = sentRe.findAll(r.html).sumOf { it.groupValues[1].toLongOrNull() ?: 0L }
        val recv = recvRe.findAll(r.html).sumOf { it.groupValues[1].toLongOrNull() ?: 0L }
        return if (sent > 0 || recv > 0) (recv to sent) else null
    }

    private fun render() {
        col.removeAllViews()

        // ---- router WiFi traffic
        add(col, section("Router WiFi traffic"), bottom = 0)
        val w = card(16)
        val t = wifiTotals
        if (t != null) {
            w.addView(kvRow("Downloaded (to devices)", formatBytes(t.first)))
            w.addView(divider())
            w.addView(kvRow("Uploaded (from devices)", formatBytes(t.second)))
            val n = tv("Counted by the router since its last restart, all WiFi bands together.", 12f, cSub())
            n.setPadding(0, dp(8), 0, 0)
            w.addView(n)
        } else {
            w.addView(tv("WiFi counters could not be read from this router yet.", 14f, cText(), true))
            val n = tv("Open the raw data below and share it, so the parser can be matched to your model.", 12f, cSub())
            n.setPadding(0, dp(4), 0, 0)
            w.addView(n)
            val b = pill("View raw WiFi statistics", cAcc())
            b.setOnClickListener { InfoActivity.open(this, InfoKind.WIFI_STATS) }
            val lp = LinearLayout.LayoutParams(wrapP, wrapP)
            lp.topMargin = dp(10)
            w.addView(b, lp)
        }
        add(col, w)

        // ---- per device online time
        add(col, section("Online time by device"), bottom = 0)
        add(col, rangeChips(rangeIdx) { i ->
            if (i == 3) {
                pickRange { f, tt ->
                    customFrom = f
                    customTo = tt
                    rangeIdx = 3
                    load()
                }
            } else {
                rangeIdx = i
                load()
            }
        }, bottom = 10)

        val max = (rows.maxOfOrNull { it.ms } ?: 0L).coerceAtLeast(1L)
        if (rows.isEmpty()) {
            val c = card(16)
            c.addView(tv("No devices found.", 14f, cSub()))
            add(col, c)
        }
        for (r in rows) {
            val d = r.device
            val c = card(14)
            val top = hrow()
            val nm = names.getCustomName(d.macAddress) ?: DeviceNamer.pretty(d.displayName)
            top.addView(tv(nm, 15f, cText(), true), LinearLayout.LayoutParams(0, wrapP, 1f))
            top.addView(tv(formatDuration(r.ms), 14f, cAcc(), true))
            c.addView(top)

            val tg = GradientDrawable()
            tg.cornerRadius = dpf(6f)
            tg.setColor(ColorUtils.setAlphaComponent(cAcc(), 30))
            val fill = View(this)
            val fg = GradientDrawable()
            fg.cornerRadius = dpf(6f)
            fg.setColor(cAcc())
            fill.background = fg
            val bar = LinearLayout(this)
            bar.orientation = LinearLayout.HORIZONTAL
            bar.background = tg
            val frac = r.ms.toFloat() / max.toFloat()
            bar.weightSum = 1f
            bar.addView(fill, LinearLayout.LayoutParams(0, dp(8), frac.coerceIn(0.02f, 1f)))
            val blp = LinearLayout.LayoutParams(ViewGroupMatch, dp(8))
            blp.topMargin = dp(8)
            c.addView(bar, blp)

            val sub = tv(
                (if (d.isOnline) "Online now" else "Offline") + " · " + r.sessions + " sessions · " + d.ipAddress,
                12f, cSub()
            )
            sub.setPadding(0, dp(6), 0, 0)
            c.addView(sub)
            c.setOnClickListener { DeviceDetailActivity.open(this, d.macAddress, d.displayName, d.ipAddress) }
            add(col, c, bottom = 10)
        }

        // ---- honest note
        val note = card(14)
        note.addView(
            tv(
                "Per-device download/upload volume is not reported by this router, so it is not shown. " +
                    "Online times are recorded by the app only while it is open; time with the app closed is never counted.",
                12f, cSub()
            )
        )
        add(col, note, bottom = 0)
    }

    private val ViewGroupMatch = android.view.ViewGroup.LayoutParams.MATCH_PARENT
}
