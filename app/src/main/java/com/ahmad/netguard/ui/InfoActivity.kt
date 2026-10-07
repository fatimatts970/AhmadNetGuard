package com.ahmad.netguard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.network.PageReader
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
import com.ahmad.netguard.model.Device
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Router ke woh pages jo sirf padhne ke liye dikhaye jate hain. */
enum class InfoKind(
    val title: String,
    val icon: NgIcon,
    val sections: List<Pair<String, List<String>>>
) {
    WAN(
        "WAN Status", NgIcon.GLOBE,
        listOf(
            "Connections" to listOf(
                "/html/bbsp/common/wan_list.asp",
                "/html/bbsp/common/wan_list_cache_wan.asp",
                "/html/bbsp/wan/wan.asp",
                "/html/common/wan_list.asp"
            )
        )
    ),
    ETHERNET(
        "Ethernet Ports", NgIcon.NETWORK,
        listOf("LAN ports" to listOf("/html/amp/ethinfo/ethinfo.asp", "/html/status/ethinfo.asp"))
    ),
    VOIP(
        "VoIP Status", NgIcon.HEADSET,
        listOf(
            "SIP lines" to listOf(
                "/html/voip/status/smartsipvoipmaintain.asp",
                "/html/voip/voipmaintain.asp"
            )
        )
    ),
    DEVICE(
        "Device Info", NgIcon.ROUTER,
        listOf("Hardware" to listOf("/html/ssmp/deviceinfo/deviceinfo.asp", "/html/status/deviceinfo.asp"))
    ),
    FIREWALL(
        "Firewall", NgIcon.SHIELD,
        listOf(
            "Firewall level" to listOf(
                "/html/bbsp/firewalllevel/firewalllevel.asp",
                "/html/security/firewalllevel.asp"
            )
        )
    ),
    DNS(
        "DNS Settings", NgIcon.GLOBE,
        listOf(
            "DNS" to listOf(
                "/html/bbsp/dnsconfig/dnsconfig.asp",
                "/html/bbsp/dns/dns.asp",
                "/html/bbsp/common/wan_list.asp"
            )
        )
    ),
    DHCP(
        "Admin · DHCP", NgIcon.GEAR,
        listOf(
            "Static IP reservations" to listOf("/html/bbsp/dhcpstatic/dhcpstatic.asp"),
            "DHCP server" to listOf("/html/bbsp/dhcpservercfg/dhcpservercfg.asp")
        )
    ),
    WIFI_STATS(
        "WiFi Statistics", NgIcon.WIFI,
        listOf(
            "Radios" to listOf(
                "/html/amp/wlaninfo/wlaninfo.asp",
                "/amp/wlaninfo/wlaninfo.asp",
                "/html/status/wlaninfo.asp",
                "/status/wlaninfo.asp"
            )
        )
    ),
    PARENTAL(
        "Parental Control", NgIcon.FAMILY,
        listOf(
            "Templates" to listOf(
                "/html/bbsp/parentalctrl/parentalctrltemplate.asp",
                "/html/bbsp/parentalctrl/parentalctrl.asp"
            ),
            "Devices" to listOf(
                "/html/bbsp/parentalctrl/parentalctrlmac.asp",
                "/html/security/parentalctrlmac.asp"
            )
        )
    )
}

class InfoActivity : NgScreen() {

    companion object {
        private const val EXTRA_KIND = "kind"

        fun open(context: Context, kind: InfoKind) {
            context.startActivity(Intent(context, InfoActivity::class.java).putExtra(EXTRA_KIND, kind.name))
        }
    }

    private lateinit var kind: InfoKind
    private val raws = ArrayList<Pair<String, String>>() // title/path to html

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kind = try {
            InfoKind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "DEVICE")
        } catch (e: Exception) {
            InfoKind.DEVICE
        }
        setupScreen(kind.title) { load() }
        load()
    }

    private class Live(
        val model: String?, val ssid: String?, val cpu: Int?,
        val online: Int?, val known: Int?, val blocked: Int?
    )

    private var liveHolder: LinearLayout? = null
    private var devicePage: PageReader.Result? = null
    private var lastLive: Live? = null

    private fun ensureHolder(): LinearLayout {
        liveHolder?.let { return it }
        val h = vcol()
        liveHolder = h
        val lp = LinearLayout.LayoutParams(matchP, wrapP)
        lp.bottomMargin = dp(12)
        col.addView(h, 0, lp)
        return h
    }

    private fun load() {
        raws.clear()
        liveHolder = null
        devicePage = null
        col.removeAllViews()

        if (kind == InfoKind.DEVICE) {
            if (RouterCache.model != null) {
                val cached = Live(RouterCache.model, RouterCache.ssid, RouterCache.cpu, RouterCache.online, RouterCache.known, RouterCache.blocked)
                lastLive = cached
                fillLive(ensureHolder(), cached)
            } else {
                showLoading()
            }
            lifecycleScope.launch {
                val fresh = fetchLive()
                lastLive = fresh
                fillLive(ensureHolder(), fresh)
                pull.done()
            }
        } else {
            showLoading()
        }

        lifecycleScope.launch {
            val results = ArrayList<Triple<String, PageReader.Result?, Boolean>>()
            for ((label, paths) in kind.sections) {
                val r = try { PageReader.fetchFirst(paths) } catch (e: Exception) { null }
                results.add(Triple(label, r, r != null))
            }
            if (kind == InfoKind.DEVICE) {
                devicePage = results.firstOrNull()?.second
                devicePage?.let { raws.add(it.path to it.html) }
                val l = lastLive
                if (l != null) fillLive(ensureHolder(), l)
                return@launch
            }
            col.removeAllViews()
            for ((label, r, ok) in results) {
                add(col, section(label), bottom = 0)
                if (!ok || r == null) {
                    add(col, unavailableCard())
                    continue
                }
                raws.add(r.path to r.html)
                add(col, sectionCard(kind, r))
            }
            pull.done()
        }
        titleView.setOnLongClickListener {
            if (raws.isNotEmpty()) showRaw()
            true
        }
    }

    private fun unavailableCard(): View {
        val c = card(18)
        c.gravity = android.view.Gravity.CENTER_HORIZONTAL
        c.addView(NgKit.icon(this, kind.icon, cSub()), LinearLayout.LayoutParams(dp(34), dp(34)))
        val t = tv("Not available on this router", 15f, cText(), true)
        t.setPadding(0, dp(8), 0, 0)
        c.addView(t)
        val m = tv("Your router model does not share this information with the app.", 12f, cSub())
        m.gravity = android.view.Gravity.CENTER_HORIZONTAL
        c.addView(m)
        return c
    }

    // ------------------------------------------------------------ rendering

    private suspend fun fetchLive(): Live = coroutineScope {
        val ad = RouterAdapterFactory.getAdapter()
        val m = async { try { ad.getRouterModel() } catch (e: Exception) { null } }
        val s = async { try { ad.getWifiSsidName() } catch (e: Exception) { null } }
        val c = async { try { ad.getCpuUsagePercent() } catch (e: Exception) { null } }
        val d = async { SessionKeeper.devices() }
        val b = async { try { ad.getBlockedMacs() } catch (e: Exception) { emptySet<String>() } }
        val devs = d.await()
        val model = m.await() ?: RouterCache.model
        val ssid = s.await() ?: RouterCache.ssid
        val cpu = c.await() ?: RouterCache.cpu
        val blocked = b.await().size
        RouterCache.model = model
        RouterCache.ssid = ssid
        RouterCache.cpu = cpu
        if (devs.isNotEmpty()) {
            RouterCache.online = devs.count { it.isOnline }
            RouterCache.known = devs.size
        }
        RouterCache.blocked = blocked
        Live(model, ssid, cpu, RouterCache.online, RouterCache.known, blocked)
    }

    private fun fillLive(holder: LinearLayout, l: Live) {
        holder.removeAllViews()
        val gateway = RouterCredentialStore(this).getGateway()
        val c = card(18)
        val head = hrow()
        head.addView(NgKit.icon(this, NgIcon.ROUTER, cAcc()), LinearLayout.LayoutParams(dp(44), dp(44)))
        val tb = vcol()
        tb.setPadding(dp(12), 0, 0, 0)
        tb.addView(tv(RouterNames.label(l.model ?: "Router"), 20f, cText(), true))
        tb.addView(tv("Huawei optical ONT router", 12f, cSub()))
        head.addView(tb, LinearLayout.LayoutParams(0, wrapP, 1f))
        c.addView(head)
        c.addView(android.view.View(this), LinearLayout.LayoutParams(1, dp(8)))

        fun row(label: String, value: String?) {
            if (c.childCount > 2) c.addView(divider())
            c.addView(kvRow(label, value ?: "—"))
        }
        row("Model", l.model)
        row("WiFi name", l.ssid)
        row("Gateway", gateway)
        row("CPU usage", l.cpu?.let { "$it%" })
        row("Devices online", l.online?.toString())
        row("Devices known", l.known?.toString())
        row("Blocked devices", l.blocked?.toString())

        val st = devicePage?.scraped?.calls?.firstOrNull { it.name == "stDeviceInfo" }
        if (st != null) {
            st.args.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { row("Serial number", it) }
            st.args.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { row("Hardware version", it) }
            st.args.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { row("Firmware", it) }
        }
        val html = devicePage?.html ?: ""
        Regex("memUsed\\s*=\\s*'(\\d+)%'").find(html)?.let { row("Memory usage", it.groupValues[1] + "%") }
        holder.addView(c, LinearLayout.LayoutParams(matchP, wrapP))
    }

    private fun summaryFor(k: InfoKind, r: PageReader.Result): List<Pair<String, String>> {
        val html = r.html
        val s = r.scraped
        val rows = ArrayList<Pair<String, String>>()
        when (k) {
            InfoKind.DEVICE -> {
                val st = s.calls.firstOrNull { it.name == "stDeviceInfo" }
                if (st != null) {
                    rows.add("Model Name" to (st.args.getOrNull(4) ?: ""))
                    rows.add("Serial Number" to (st.args.getOrNull(1) ?: ""))
                    rows.add("Hardware Version" to (st.args.getOrNull(2) ?: ""))
                    rows.add("Firmware" to (st.args.getOrNull(3) ?: ""))
                }
                Regex("cpuUsed\\s*=\\s*'(\\d+)%'").find(html)?.let { rows.add("CPU Usage" to it.groupValues[1] + "%") }
                Regex("memUsed\\s*=\\s*'(\\d+)%'").find(html)?.let { rows.add("Memory Usage" to it.groupValues[1] + "%") }
            }
            InfoKind.WAN, InfoKind.DNS -> {
                for (c in s.calls.filter { it.name == "PolicyRouteItem" && it.args.size >= 4 }) {
                    val wanName = c.args[3]
                    if (wanName.isBlank()) continue
                    val isPpp = wanName.contains("ppp", true)
                    rows.add("Connection" to wanName)
                    rows.add("Type" to (if (isPpp) "PPPoE" else "IP / Bridge"))
                    val bound = c.args.getOrNull(5)?.replace(",", ", ") ?: ""
                    if (bound.isNotBlank()) rows.add("Used by" to bound)
                    rows.add("" to "")
                }
                for (c in s.calls.filter { it.name == "WanPPP" || it.name == "WanIP" }) {
                    val a = c.args
                    val domain = a.firstOrNull { it.contains("WAN", ignoreCase = true) && it.contains(".") } ?: ""
                    val name = a.firstOrNull { Regex("^\\d+_\\w+").containsMatchIn(it) } ?: ""
                    val vlan = Regex("VID_(\\d+)").find(name)?.groupValues?.get(1)
                    val ips = a.filter { Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(it) && it != "0.0.0.0" }
                    val mac = a.firstOrNull { Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$").matches(it) }
                    val status = a.firstOrNull { it.equals("Connected", true) || it.equals("Disconnected", true) || it.equals("Up", true) || it.equals("Down", true) }
                    val proto = if (c.name == "WanPPP" || domain.contains("WANPPPConnection")) "PPPoE" else "IP"
                    if (name.isNotBlank()) rows.add("Connection" to name)
                    rows.add("Protocol" to proto)
                    if (vlan != null) rows.add("VLAN" to vlan)
                    if (status != null) rows.add("Status" to status)
                    if (mac != null) rows.add("MAC Address" to mac)
                    ips.forEachIndexed { i, ip ->
                        val label = when (i) {
                            0 -> "IP Address"
                            1 -> "Gateway / next"
                            else -> "Address ${i + 1}"
                        }
                        rows.add(label to ip)
                    }
                    rows.add("" to "")
                }
                if (rows.isNotEmpty() && rows.last().first.isEmpty()) rows.removeAt(rows.size - 1)
            }
            InfoKind.ETHERNET -> {
                var n = 0
                for (c in s.calls) {
                    val a = c.args
                    val domain = a.firstOrNull { it.contains("LANEthernetInterfaceConfig") }
                    val port = domain?.let { Regex("LANEthernetInterfaceConfig\\.?(\\d+)").find(it)?.groupValues?.get(1) }
                    val status = a.firstOrNull { it.equals("Up", true) || it.equals("Down", true) || it.equals("NoLink", true) || it.equals("Disabled", true) }
                    val speed = a.firstOrNull { Regex("^(10|100|1000|2500|10000)(M|Mbps)?$", RegexOption.IGNORE_CASE).matches(it) }
                    val duplex = a.firstOrNull { it.equals("Full", true) || it.equals("Half", true) || it.equals("Auto", true) }
                    if (port == null && status == null) continue
                    n++
                    val title = "Port " + (port ?: n.toString())
                    val line = listOfNotNull(
                        status,
                        speed?.let { if (it.contains("M", true)) it else it + " Mbps" },
                        duplex
                    ).joinToString(" · ")
                    rows.add(title to line.ifBlank { a.joinToString(", ") })
                }
            }
            InfoKind.VOIP -> {
                var n = 0
                for (c in s.calls) {
                    val status = c.args.firstOrNull {
                        it.equals("Up", true) || it.equals("Registered", true) || it.equals("Disabled", true) ||
                            it.equals("Error", true) || it.equals("Idle", true) || it.equals("Registering", true) ||
                            it.equals("Disable", true)
                    } ?: continue
                    n++
                    rows.add("Line $n" to status)
                }
            }
            InfoKind.FIREWALL -> {
                val lvl = Regex("(?i)(?:firewall_?level|FirewallLevel|level)\\W{0,14}(disable|low|medium|high|custom|off)").find(html)
                if (lvl != null) rows.add("Firewall level" to lvl.groupValues[1].replaceFirstChar { it.uppercase() })
            }
            else -> {}
        }
        return rows
    }

    private fun sectionCard(k: InfoKind, r: PageReader.Result): View {
        val rows = summaryFor(k, r).toMutableList()
        while (rows.isNotEmpty() && rows.last().first.isEmpty()) rows.removeAt(rows.size - 1)
        if (rows.isEmpty()) return unavailableCard()
        val c = card(16)
        var prevWasRow = false
        for ((label, value) in rows) {
            if (label.isEmpty()) {
                c.addView(divider())
                prevWasRow = false
                continue
            }
            if (prevWasRow) c.addView(divider())
            c.addView(kvRow(label, value))
            prevWasRow = true
        }
        return c
    }

    // ------------------------------------------------------------ raw viewer

    private fun showRaw() {
        val sb = StringBuilder()
        for ((path, html) in raws) {
            sb.append("### ").append(path).append('\n').append(PageReader.sanitize(html)).append("\n\n")
        }
        val full = sb.toString()

        val tvRaw = TextView(this)
        tvRaw.text = if (full.length > 12000) full.substring(0, 12000) + "\n…(baqi copy mein)" else full
        tvRaw.textSize = 10f
        tvRaw.typeface = Typeface.MONOSPACE
        tvRaw.setTextColor(Color.parseColor("#1F2937"))
        tvRaw.setTextIsSelectable(true)
        tvRaw.setPadding(dp(16), dp(8), dp(16), dp(8))
        val sv = ScrollView(this)
        sv.addView(tvRaw)

        AlertDialog.Builder(this)
            .setTitle("Raw — " + kind.title)
            .setMessage("Note: raw data may contain your router details (IP, MAC, sometimes the PPPoE name). Review it before sharing.")
            .setView(sv)
            .setPositiveButton("Copy") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("raw", full.take(60000)))
                toast("Copied")
            }
            .setNeutralButton("Share") { _, _ ->
                val i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(Intent.EXTRA_TEXT, full.take(60000))
                startActivity(Intent.createChooser(i, "Share raw data"))
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
