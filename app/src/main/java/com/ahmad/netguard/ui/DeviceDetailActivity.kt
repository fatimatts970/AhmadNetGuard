package com.ahmad.netguard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.history.AppDatabase
import com.ahmad.netguard.history.ConnectionEvent
import com.ahmad.netguard.model.Device
import com.ahmad.netguard.network.DeviceNameStore
import com.ahmad.netguard.network.HuaweiRouterAdapter
import com.ahmad.netguard.network.PageReader
import com.ahmad.netguard.network.RouterAdapterFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ek device ki poori detail: online status, sessions, online time, router record. */
class DeviceDetailActivity : NgScreen() {

    companion object {
        private const val EXTRA_MAC = "mac"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_IP = "ip"

        fun open(context: Context, mac: String, name: String, ip: String) {
            context.startActivity(
                Intent(context, DeviceDetailActivity::class.java)
                    .putExtra(EXTRA_MAC, mac)
                    .putExtra(EXTRA_NAME, name)
                    .putExtra(EXTRA_IP, ip)
            )
        }
    }

    private lateinit var mac: String
    private var name = ""
    private var ip = ""
    private lateinit var names: DeviceNameStore

    private var rangeIdx = 1
    private var customFrom = 0L
    private var customTo = 0L

    private var online = false
    private var blocked = false
    private var sessions: List<SessionCalc.Session> = emptyList()
    private var firstEventAt: Long? = null
    private var recordFields: List<String> = emptyList()

    private val dateFmt = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())
    private val dayFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mac = intent.getStringExtra(EXTRA_MAC) ?: ""
        name = intent.getStringExtra(EXTRA_NAME) ?: ""
        ip = intent.getStringExtra(EXTRA_IP) ?: ""
        names = DeviceNameStore(this)
        setupScreen("Device details") { load() }
        load()
    }

    private fun shownName(): String =
        names.getCustomName(mac) ?: DeviceNamer.pretty(name)

    private fun load() {
        col.removeAllViews()
        val loading = card(18)
        loading.addView(tv("Loading…", 14f, cSub()))
        add(col, loading)

        lifecycleScope.launch {
            val ad = RouterAdapterFactory.getAdapter()
            val devs = SessionKeeper.devices()
            val blockedSet = try { ad.getBlockedMacs() } catch (e: Exception) { emptySet<String>() }
            val me = devs.firstOrNull { it.macAddress.equals(mac, true) }
            online = me?.isOnline == true
            blocked = blockedSet.contains(mac.uppercase())
            if (me != null) {
                if (ip.isBlank() || ip == "—") ip = me.ipAddress
                if (name.isBlank()) name = me.displayName
            }

            val dao = AppDatabase.getInstance(this@DeviceDetailActivity).connectionEventDao()
            val events: List<ConnectionEvent> = try {
                dao.getHistoryForDevice(mac).reversed()
            } catch (e: Exception) {
                emptyList()
            }
            firstEventAt = events.firstOrNull()?.timestampMillis
            sessions = SessionCalc.build(events, online, System.currentTimeMillis())

            recordFields = try {
                val html = (ad as? HuaweiRouterAdapter)
                    ?.fetchPagePost("/html/bbsp/common/GetLanUserDevInfo.asp")
                if (html == null) emptyList() else {
                    val calls = PageReader.scrape(html).calls.filter { it.name == "USERDevice" }
                    val hit = calls.firstOrNull { c -> c.args.any { it.equals(mac, true) } }
                    hit?.args ?: emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }

            render()
            pull.done()
        }
    }

    private fun render() {
        col.removeAllViews()

        // ---- header
        val hero = card(18)
        val top = hrow()
        val nm = tv(shownName(), 20f, cText(), true)
        top.addView(nm, LinearLayout.LayoutParams(0, wrapP, 1f))
        val statusColor = when {
            blocked -> ThemeManager.danger()
            online -> cAcc()
            else -> cSub()
        }
        top.addView(pill(if (blocked) "Blocked" else if (online) "Online" else "Offline", statusColor))
        hero.addView(top)
        val sub = tv(listOf(ip, mac).filter { it.isNotBlank() && it != "—" }.joinToString(" • "), 12f, cSub())
        sub.setPadding(0, dp(6), 0, 0)
        hero.addView(sub)
        val ongoing = sessions.lastOrNull { it.ongoing }
        if (ongoing != null) {
            val on = tv(
                "Online since " + dateFmt.format(Date(ongoing.start)) + "  (" + formatDuration(ongoing.duration) + ")",
                13f, cAcc(), true
            )
            on.setPadding(0, dp(10), 0, 0)
            hero.addView(on)
        } else if (!online) {
            val last = sessions.lastOrNull()
            if (last != null) {
                val off = tv("Last online " + dateFmt.format(Date(last.end)), 13f, cSub(), true)
                off.setPadding(0, dp(10), 0, 0)
                hero.addView(off)
            }
        }
        add(col, hero)

        // ---- range + summary
        add(col, section("Online time"), bottom = 0)
        add(col, rangeChips(rangeIdx) { i ->
            if (i == 3) {
                pickRange { f, t ->
                    customFrom = f
                    customTo = t
                    rangeIdx = 3
                    render()
                }
            } else {
                rangeIdx = i
                render()
            }
        }, bottom = 10)

        val (from, to) = rangeBounds(rangeIdx, customFrom, customTo)
        val total = SessionCalc.overlap(sessions, from, to)
        val count = SessionCalc.countIn(sessions, from, to)
        val longest = SessionCalc.longestIn(sessions, from, to)

        val sum = card(16)
        if (rangeIdx == 3) {
            val r = tv(dayFmt.format(Date(from)) + "  →  " + dayFmt.format(Date(to)), 12f, cSub(), true)
            r.setPadding(0, 0, 0, dp(6))
            sum.addView(r)
        }
        sum.addView(kvRow("Total online time", formatDuration(total)))
        sum.addView(divider())
        sum.addView(kvRow("Sessions", count.toString()))
        sum.addView(divider())
        sum.addView(kvRow("Longest session", formatDuration(longest)))
        sum.addView(divider())
        val first = firstEventAt
        sum.addView(kvRow("Tracking since", if (first != null) dateFmt.format(Date(first)) else "—"))
        add(col, sum)

        // ---- data usage (honest)
        add(col, section("Data used"), bottom = 0)
        val du = card(16)
        du.addView(kvRow("Downloaded / uploaded", "Not reported"))
        val note = tv(
            "This router does not report per-device traffic, so no number is shown here. " +
                "Online time and sessions above are real, recorded while the app is open.",
            12f, cSub()
        )
        note.setPadding(0, dp(6), 0, 0)
        du.addView(note)
        add(col, du)

        // ---- sessions
        add(col, section("Sessions"), bottom = 0)
        val list = sessions.filter { it.end > from && it.start < to }.reversed().take(40)
        if (list.isEmpty()) {
            val c = card(16)
            c.addView(tv("No sessions recorded in this period.", 14f, cSub()))
            add(col, c)
        } else {
            val c = card(8)
            list.forEachIndexed { i, s ->
                if (i > 0) c.addView(divider())
                val row = vcol()
                row.setPadding(dp(8), dp(10), dp(8), dp(10))
                val line1 = hrow()
                line1.addView(
                    tv(dateFmt.format(Date(s.start)), 14f, cText(), true),
                    LinearLayout.LayoutParams(0, wrapP, 1f)
                )
                line1.addView(tv(formatDuration(s.duration), 13f, if (s.ongoing) cAcc() else cSub(), true))
                row.addView(line1)
                row.addView(
                    tv(
                        if (s.ongoing) "Still online" else "Went offline " + dateFmt.format(Date(s.end)),
                        12f, cSub()
                    )
                )
                c.addView(row)
            }
            add(col, c)
        }

        // ---- router record (diagnostics)
        add(col, section("Router record"), bottom = 0)
        val rec = card(16)
        if (recordFields.isEmpty()) {
            rec.addView(tv("The router did not return a record for this device.", 13f, cSub()))
        } else {
            recordFields.forEachIndexed { i, v ->
                if (v.isNotBlank()) {
                    if (rec.childCount > 0) rec.addView(divider())
                    rec.addView(kvRow("Field $i", v))
                }
            }
            val copy = pill("Copy record", cAcc())
            copy.setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("record", recordFields.joinToString("\n")))
                toast("Copied")
            }
            val lp = LinearLayout.LayoutParams(wrapP, wrapP)
            lp.topMargin = dp(10)
            rec.addView(copy, lp)
        }
        add(col, rec)

        // ---- actions
        val actions = hrow()
        val rename = pill("Rename", cAcc())
        rename.setOnClickListener {
            NgDialog.input(this, "Rename device", listOf(ip, mac).filter { it.isNotBlank() }.joinToString(" · "), shownName(), "Device name") { n ->
                names.setCustomName(mac, n)
                render()
            }
        }
        val blk = pill(if (blocked) "Unblock" else "Block internet", if (blocked) cAcc() else ThemeManager.danger(), true)
        blk.setOnClickListener {
            blk.isEnabled = false
            lifecycleScope.launch {
                val ad = RouterAdapterFactory.getAdapter()
                val ok = try {
                    if (blocked) ad.unblockDevice(mac) else ad.blockDevice(mac)
                } catch (e: Exception) {
                    false
                }
                if (ok) blocked = !blocked
                toast(if (ok) (if (blocked) "Device blocked" else "Device unblocked") else "Action failed")
                render()
            }
        }
        val p1 = LinearLayout.LayoutParams(0, wrapP, 1f)
        p1.marginEnd = dp(10)
        actions.addView(rename, p1)
        actions.addView(blk, LinearLayout.LayoutParams(0, wrapP, 1f))
        add(col, actions, bottom = 0)
    }
}
