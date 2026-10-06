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

    private fun load() {
        col.removeAllViews()
        raws.clear()
        val loading = card(18)
        loading.addView(tv("Loading from router…", 14f, cSub()))
        add(col, loading)

        lifecycleScope.launch {
            val results = ArrayList<Triple<String, PageReader.Result?, Boolean>>()
            for ((label, paths) in kind.sections) {
                val r = try { PageReader.fetchFirst(paths) } catch (e: Exception) { null }
                results.add(Triple(label, r, r != null))
            }
            col.removeAllViews()

            if (kind == InfoKind.DEVICE) {
                val first = results.firstOrNull()?.second
                if (first != null) add(col, deviceHero(first))
            }

            for ((label, r, ok) in results) {
                add(col, section(label), bottom = 0)
                if (!ok || r == null) {
                    val c = card(16)
                    c.addView(tv("This page was not found on your router, or the session expired.", 14f, cText(), true))
                    c.addView(tv("Log in again and pull down to refresh.", 12f, cSub()))
                    add(col, c)
                    continue
                }
                raws.add(r.path to r.html)
                add(col, sectionCard(kind, r))
            }

            // note + raw button
            val note = card(14)
            note.addView(tv("This screen is read-only for now. Values are read directly from your router pages.", 12f, cSub()))
            if (raws.isNotEmpty()) {
                val btn = pill("View / copy raw data", cAcc())
                btn.setOnClickListener { showRaw() }
                val p = LinearLayout.LayoutParams(wrapP, wrapP)
                p.topMargin = dp(10)
                note.addView(btn, p)
            }
            add(col, note, bottom = 0)
            pull.done()
        }
    }

    // ------------------------------------------------------------ rendering

    private fun deviceHero(r: PageReader.Result): View {
        val s = r.scraped
        val st = s.calls.firstOrNull { it.name == "stDeviceInfo" }
        val model = st?.args?.getOrNull(4) ?: ""
        val c = card(20)
        c.gravity = android.view.Gravity.CENTER_HORIZONTAL
        c.addView(NgKit.icon(this, NgIcon.ROUTER, cAcc()), LinearLayout.LayoutParams(dp(56), dp(56)))
        val t = tv(model.ifBlank { "Router" }, 22f, cText(), true)
        t.setPadding(0, dp(8), 0, 0)
        c.addView(t)
        return c
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
        val c = card(16)
        val rows = summaryFor(k, r)
        val s = r.scraped
        var shown = 0

        for ((label, value) in rows) {
            if (label.isEmpty()) {
                c.addView(divider())
                continue
            }
            if (shown > 0 && c.childCount > 0) c.addView(divider())
            c.addView(kvRow(label, value))
            shown++
        }

        // baqi calls (jo summary mein nahi aaye) — raw args
        val extraCalls = s.calls.filter { it.args.size >= 2 }.take(12)
        if (rows.isEmpty() && extraCalls.isNotEmpty()) {
            for (call in extraCalls) {
                if (c.childCount > 0) c.addView(divider())
                val shownArgs = call.args.filter { it.isNotBlank() }.joinToString("  ·  ")
                val title = tv(call.name, 12f, cSub(), true)
                title.setPadding(0, dp(8), 0, dp(2))
                c.addView(title)
                c.addView(tv(shownArgs, 13f, cText()))
            }
        }

        if (rows.isEmpty() && extraCalls.isEmpty() && s.fields.isNotEmpty()) {
            for ((k2, v2) in s.fields.entries.take(25)) {
                if (c.childCount > 0) c.addView(divider())
                c.addView(kvRow(k2, v2))
            }
        }

        if (c.childCount == 0) {
            c.addView(tv("Page found (${r.path}) but its fields could not be parsed on this router.", 13f, cText(), true))
            c.addView(tv("Tap 'View / copy raw data' below and share it so the parser can be fixed.", 12f, cSub()))
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
