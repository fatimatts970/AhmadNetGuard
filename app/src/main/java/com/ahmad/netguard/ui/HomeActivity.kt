package com.ahmad.netguard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.history.ConnectionMonitorService
import com.ahmad.netguard.model.Device
import com.ahmad.netguard.network.DeviceNameStore
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.net.TrafficStats

private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * AHMAD WiFi Manager — naya Home screen.
 * 4 tabs: Dashboard / Devices / Advanced / Theme.
 * Poora UI code se bana hai (XML layout nahi), taake build mein resource errors na aayen.
 */
class HomeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODEL = "extra_model"
        private var cachedModel: String? = null
        private var cachedCpu: Int? = null
        private var cachedSsid: String? = null
        private var connectedCount = 0
        private var blockedCount = 0
        private var devAll: List<Device> = emptyList()
    }

    private lateinit var creds: RouterCredentialStore
    private lateinit var names: DeviceNameStore
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var bar: LinearLayout

    private var tab = 0

    // Dashboard state
    private var liveJob: Job? = null
    private var currentPull: PullRefreshLayout? = null
    private var dashOnline: TextView? = null
    private var dashLive: TextView? = null
    private var liveLabel = "Live  ↓ —   ↑ —"
    private var prevRx = -1L
    private var prevTx = -1L
    private var prevAt = 0L
    private var devSig = ""
    private var dashHero: TextView? = null
    private var dashSpeed: TextView? = null
    private var dashModel: TextView? = null
    private var dashCpu: TextView? = null
    private var dashStatus: TextView? = null
    private var dashDevicesSub: TextView? = null

    // Devices state
    private var devFilter = 0 // 0 = online/offline, 1 = blocked
    private var devCardOnline: LinearLayout? = null
    private var devCardBlocked: LinearLayout? = null
    private var devList: LinearLayout? = null
    private var devConnected: TextView? = null
    private var devBlocked: TextView? = null

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.init(this)
        AppCompatDelegate.setDefaultNightMode(
            if (ThemeManager.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)

        creds = RouterCredentialStore(this)
        names = DeviceNameStore(this)
        tab = savedInstanceState?.getInt("tab") ?: 0
        intent.getStringExtra(EXTRA_MODEL)?.let { cachedModel = it }

        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        content = FrameLayout(this)
        bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        setContentView(root)

        if (ThemeManager.alertsOn) startMonitoring()

        showTab(tab)
    }

    override fun onResume() {
        super.onResume()
        startLive()
    }

    override fun onPause() {
        liveJob?.cancel()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    // ---------------------------------------------------------------- small UI helpers

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun dpf(v: Float): Float = v * resources.displayMetrics.density
    private fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight)

    private fun cText(): Int = ThemeManager.text()
    private fun cSub(): Int = ThemeManager.sub()
    private fun cAcc(): Int = ThemeManager.accent()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun soon() {
        toast("Coming soon")
    }

    private fun tv(text: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun vcol(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        return l
    }

    private fun hrow(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.HORIZONTAL
        l.gravity = Gravity.CENTER_VERTICAL
        return l
    }

    private fun add(
        parent: LinearLayout, v: View, w: Int = MATCH, h: Int = WRAP,
        weight: Float = 0f, bottom: Int = 12, end: Int = 0
    ) {
        val p = LinearLayout.LayoutParams(w, h, weight)
        p.bottomMargin = dp(bottom)
        p.marginEnd = dp(end)
        parent.addView(v, p)
    }

    private fun cardBg(): Drawable = NgKit.cardBg(this)

    private fun card(pad: Int = 16): LinearLayout {
        val c = vcol()
        c.setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
        c.background = cardBg()
        c.elevation = if (ThemeManager.effect3d) dpf(6f) else 0f
        return c
    }

    private fun pill(text: String, color: Int, filled: Boolean = false): TextView {
        val t = tv(text, 12f, if (filled) Color.WHITE else color, true)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        val g = GradientDrawable()
        g.cornerRadius = dpf(40f)
        g.setColor(if (filled) color else ColorUtils.setAlphaComponent(color, 40))
        t.background = g
        return t
    }

    private fun section(title: String): TextView {
        val t = tv(title.uppercase(), 12f, cSub(), true)
        t.letterSpacing = 0.08f
        t.setPadding(dp(4), dp(8), 0, dp(8))
        return t
    }

    private fun ic(icon: NgIcon, color: Int, sizeDp: Int, filled: Boolean = false): IconView {
        val v = NgKit.icon(this, icon, color, filled)
        v.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        return v
    }

    private fun styleSwitch(sw: SwitchCompat) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val acc = cAcc()
        val grey = Color.parseColor("#9AA5A0")
        sw.thumbTintList = ColorStateList(states, intArrayOf(acc, grey))
        sw.trackTintList = ColorStateList(
            states,
            intArrayOf(ColorUtils.setAlphaComponent(acc, 120), ColorUtils.setAlphaComponent(grey, 120))
        )
    }

    private fun scroller(build: (LinearLayout) -> Unit): View {
        val pl = PullRefreshLayout(this)
        pl.retint()
        val col = vcol()
        col.setPadding(dp(16), dp(14), dp(16), dp(28))
        build(col)
        pl.scroll.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        pl.onRefresh = { refreshCurrent() }
        currentPull = pl
        return pl
    }

    private fun refreshCurrent() {
        when (tab) {
            0 -> refreshDashboard(startSpeed = false, force = true)
            1 -> loadDevices()
            else -> currentPull?.postDelayed({ currentPull?.done() }, 400)
        }
    }

    private fun backButton(onClick: () -> Unit): View {
        val outer = FrameLayout(this)
        outer.setPadding(0, dp(4), dp(10), dp(4))
        val inner = FrameLayout(this)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(ColorUtils.setAlphaComponent(cAcc(), 40))
        inner.background = bg
        val iv = ic(NgIcon.BACK, cAcc(), 22)
        val ivp = FrameLayout.LayoutParams(dp(22), dp(22))
        ivp.gravity = Gravity.CENTER
        inner.addView(iv, ivp)
        outer.addView(inner, FrameLayout.LayoutParams(dp(40), dp(40)))
        outer.setOnClickListener { onClick() }
        return outer
    }

    private fun switchLine(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val r = hrow()
        val tb = vcol()
        tb.addView(tv(title, 15f, cText(), true))
        if (sub != null) tb.addView(tv(sub, 12f, cSub()))
        val sw = SwitchCompat(this)
        sw.isChecked = checked
        styleSwitch(sw)
        sw.setOnCheckedChangeListener { _, on -> onChange(on) }
        r.addView(tb, lp(0, WRAP, 1f))
        r.addView(sw)
        r.setPadding(0, dp(6), 0, dp(6))
        return r
    }

    private fun switchCard(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val c = card(14)
        c.addView(switchLine(title, sub, checked, onChange))
        return c
    }

    private fun listRow(title: String, sub: String, danger: Boolean, action: () -> Unit): View {
        val r = hrow()
        r.setPadding(0, dp(12), 0, dp(12))
        val tb = vcol()
        tb.addView(tv(title, 15f, if (danger) ThemeManager.danger() else cText(), true))
        tb.addView(tv(sub, 12f, cSub()))
        r.addView(tb, lp(0, WRAP, 1f))
        r.addView(ic(NgIcon.CHEVRON, cSub(), 20))
        r.setOnClickListener { action() }
        return r
    }

    private fun divider(): View {
        val v = View(this)
        v.setBackgroundColor(ThemeManager.border())
        v.layoutParams = LinearLayout.LayoutParams(MATCH, dp(1))
        return v
    }

    private fun listCard(items: List<Triple<String, String, () -> Unit>>, dangerTitle: String? = null): View {
        val c = card(14)
        items.forEachIndexed { i, (title, sub, action) ->
            if (i > 0) c.addView(divider())
            c.addView(listRow(title, sub, title == dangerTitle, action))
        }
        return c
    }

    // ---------------------------------------------------------------- chrome (status bar, bottom bar)

    private fun applyChrome() {
        root.background = NgKit.screenBg()
        NgKit.chrome(this)
        window.navigationBarColor = ThemeManager.bar()

        bar.removeAllViews()
        bar.setBackgroundColor(ThemeManager.bar())
        bar.setPadding(dp(8), dp(6), dp(8), dp(6))
        val items = listOf(
            NgIcon.GRID to "Dashboard", NgIcon.DEVICES to "Devices",
            NgIcon.GEAR to "Advanced", NgIcon.PALETTE to "Theme"
        )
        items.forEachIndexed { i, (navIcon, label) ->
            val sel = i == tab
            val item = vcol()
            item.gravity = Gravity.CENTER_HORIZONTAL
            item.setPadding(dp(4), dp(6), dp(4), dp(6))
            val navView = ic(navIcon, if (sel) cAcc() else cSub(), 24, sel)
            item.addView(navView)
            val navLabel = tv(label, 11f, if (sel) cAcc() else cSub(), sel)
            navLabel.setPadding(0, dp(2), 0, 0)
            item.addView(navLabel)
            if (sel) {
                val g = GradientDrawable()
                g.cornerRadius = dpf(18f)
                g.setColor(ColorUtils.setAlphaComponent(cAcc(), 38))
                item.background = g
            }
            item.setOnClickListener { if (tab != i) showTab(i) }
            val p = LinearLayout.LayoutParams(0, WRAP, 1f)
            p.marginStart = dp(3)
            p.marginEnd = dp(3)
            bar.addView(item, p)
        }
    }

    private fun showTab(i: Int) {
        tab = i
        applyChrome()
        content.removeAllViews()
        dashModel = null
        dashOnline = null
        dashLive = null
        currentPull = null
        dashHero = null
        dashSpeed = null
        devCardOnline = null
        devCardBlocked = null
        dashCpu = null
        dashStatus = null
        dashDevicesSub = null
        devList = null
        devConnected = null
        devBlocked = null
        val v = when (i) {
            0 -> buildDashboard()
            1 -> buildDevices()
            2 -> buildAdvanced()
            else -> buildTheme()
        }
        content.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        if (i == 0) refreshDashboard(startSpeed = false, force = false)
    }

    // ---------------------------------------------------------------- DASHBOARD

    private class Tile(
        val key: String, val icon: NgIcon, val tint: Int,
        val title: String, val sub: String, val action: () -> Unit
    )

    private fun tiles(): List<Tile> = listOf(
        Tile("wan", NgIcon.GLOBE, Color.parseColor("#6D4FC2"), "WAN Status", "PPPoE & IP details") { InfoActivity.open(this, InfoKind.WAN) },
        Tile("devices", NgIcon.DEVICES, Color.parseColor("#A0600F"), "Devices", "$connectedCount online") { showTab(1) },
        Tile("optical", NgIcon.SUN, Color.parseColor("#16A34A"), "Optical Info", "Laser power & temp") { startActivity(Intent(this, OpticalActivity::class.java)) },
        Tile("ports", NgIcon.NETWORK, Color.parseColor("#1E5BB8"), "Ethernet Ports", "Link status & speeds") { InfoActivity.open(this, InfoKind.ETHERNET) },
        Tile("devinfo", NgIcon.ROUTER, Color.parseColor("#A0600F"), "Device Info", "CPU, RAM & Versions") { InfoActivity.open(this, InfoKind.DEVICE) },
        Tile("usage", NgIcon.USAGE, Color.parseColor("#1E78C8"), "Usage", "Traffic statistics") { startActivity(Intent(this, NetStatsActivity::class.java)) },
        Tile("voip", NgIcon.HEADSET, Color.parseColor("#0F8A4B"), "VoIP Status", "SIP line status") { InfoActivity.open(this, InfoKind.VOIP) },
        Tile("wifipass", NgIcon.WIFI, Color.parseColor("#6D4FC2"), "Wi-Fi Password", "Change SSID & Key") { startActivity(Intent(this, WifiSettingsActivity::class.java)) },
        Tile("guest", NgIcon.PEOPLE, Color.parseColor("#475569"), "Guest", "Guest WiFi & users") { startActivity(Intent(this, WifiSettingsActivity::class.java)) },
        Tile("macfilter", NgIcon.SHIELD, Color.parseColor("#0F9D6E"), "MAC Filter", "Allow/Block devices") { startActivity(Intent(this, MacFilterActivity::class.java)) },
        Tile("parental", NgIcon.FAMILY, Color.parseColor("#D13B3B"), "Parental Control", "Templates & restrictions") { InfoActivity.open(this, InfoKind.PARENTAL) },
        Tile("blocknet", NgIcon.BLOCK, Color.parseColor("#D13B3B"), "Block Internet", "without disconnect") { showTab(1) }
    )

    private fun buildDashboard(): View = scroller { col ->
        // header
        val head = hrow()
        val titleBox = vcol()
        val model = tv(cachedModel ?: "Router", 20f, cText(), true)
        dashModel = model
        titleBox.addView(model)
        titleBox.addView(tv("● " + creds.getGateway(), 12f, cAcc(), true))
        head.addView(titleBox, lp(0, WRAP, 1f))

        val bell = FrameLayout(this)
        bell.setPadding(dp(8), dp(8), dp(8), dp(8))
        bell.addView(ic(NgIcon.BELL, cText(), 24, true))
        bell.setOnClickListener { startActivity(Intent(this, NotificationsActivity::class.java)) }
        head.addView(bell)

        val moon = FrameLayout(this)
        moon.setPadding(dp(8), dp(8), dp(8), dp(8))
        moon.addView(ic(if (ThemeManager.dark) NgIcon.SUN else NgIcon.MOON, cText(), 24, !ThemeManager.dark))
        moon.setOnClickListener { setDark(!ThemeManager.dark) }
        head.addView(moon)

        val out = FrameLayout(this)
        out.setPadding(dp(8), dp(8), dp(4), dp(8))
        out.addView(ic(NgIcon.LOGOUT, cText(), 24))
        out.setOnClickListener { logout() }
        head.addView(out)
        add(col, head, bottom = 12)

        // hero card (accent gradient, white text)
        val white = Color.WHITE
        val soft = ColorUtils.setAlphaComponent(Color.WHITE, 190)
        val hero = vcol()
        hero.setPadding(dp(20), dp(18), dp(20), dp(16))
        hero.background = NgKit.heroBg(this)
        hero.elevation = if (ThemeManager.effect3d) dpf(10f) else dpf(3f)

        val top = hrow()
        val heroTitle = vcol()
        val heroName = tv(cachedSsid ?: cachedModel ?: "Router", 22f, white, true)
        dashHero = heroName
        heroTitle.addView(heroName)
        heroTitle.addView(tv(creds.getGateway() + " · connected", 12f, soft))
        top.addView(heroTitle, lp(0, WRAP, 1f))
        val status = tv(if (cachedModel == null) "Loading" else "ONLINE", 12f, white, true)
        status.setPadding(dp(14), dp(7), dp(14), dp(7))
        val sg = GradientDrawable()
        sg.cornerRadius = dpf(30f)
        sg.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 50))
        sg.setStroke(dp(1), ColorUtils.setAlphaComponent(Color.WHITE, 170))
        status.background = sg
        dashStatus = status
        top.addView(status)
        hero.addView(top)

        val art = RouterArt(this)
        hero.addView(art, lp(MATCH, dp(130)))

        val stats = hrow()
        stats.weightSum = 3.1f
        fun stat(label: String, value: String, bind: (TextView) -> Unit): LinearLayout {
            val b = vcol()
            b.addView(tv(label, 12f, soft))
            val v = tv(value, 18f, white, true)
            bind(v)
            b.addView(v)
            return b
        }
        stats.addView(stat("Online", connectedCount.toString()) { dashOnline = it }, lp(0, WRAP, 0.8f))
        stats.addView(stat("CPU", cachedCpu?.let { "$it%" } ?: "—") { dashCpu = it }, lp(0, WRAP, 0.8f))
        val speedBox = vcol()
        speedBox.addView(tv("Run Speed Test", 12f, soft))
        val speedVal = tv(speedText(), 15f, white, true)
        dashSpeed = speedVal
        speedBox.addView(speedVal)
        speedBox.setOnClickListener { startActivity(Intent(this, SpeedTestActivity::class.java)) }
        stats.addView(speedBox, lp(0, WRAP, 1.5f))
        hero.addView(stats)

        val live = tv(liveLabel, 12f, soft, true)
        live.setPadding(0, dp(10), 0, 0)
        dashLive = live
        hero.addView(live)

        val line = View(this)
        line.setBackgroundColor(ColorUtils.setAlphaComponent(Color.WHITE, 70))
        hero.addView(line, lp(MATCH, dp(1)).also { it.topMargin = dp(14); it.bottomMargin = dp(10) })

        val guestRow = hrow()
        guestRow.addView(ic(NgIcon.WIFI, white, 22))
        val gl = tv("Guest WiFi", 15f, white, true)
        gl.setPadding(dp(12), 0, 0, 0)
        guestRow.addView(gl, lp(0, WRAP, 1f))
        val sw = SwitchCompat(this)
        val swStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(swStates, intArrayOf(white, Color.parseColor("#E2E8F0")))
        sw.trackTintList = ColorStateList(
            swStates,
            intArrayOf(ColorUtils.setAlphaComponent(Color.WHITE, 150), ColorUtils.setAlphaComponent(Color.BLACK, 70))
        )
        sw.setOnClickListener {
            val on = sw.isChecked
            val ssid = creds.getGuestSsid()
            val key = creds.getGuestKey()
            if (ssid.isBlank() || key.length < 8) {
                sw.isChecked = !on
                toast("Set a guest name and password (8+ characters) first")
                startActivity(Intent(this, WifiSettingsActivity::class.java))
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val ok = try {
                    RouterAdapterFactory.getAdapter().setGuestWifi(ssid, key, on)
                } catch (e: Exception) {
                    false
                }
                if (ok) {
                    toast(if (on) "Guest WiFi ON" else "Guest WiFi OFF")
                } else {
                    sw.isChecked = !on
                    toast("Could not change Guest WiFi")
                }
            }
        }
        guestRow.addView(sw)
        hero.addView(guestRow)
        add(col, hero, bottom = 18)

        // tiles
        add(col, section("Stats & Diagnostics"), bottom = 0)
        val enabled = tiles().filter { ThemeManager.widgetOn(it.key) }
        var i = 0
        while (i < enabled.size) {
            val row = hrow()
            row.weightSum = 2f
            for (j in 0..1) {
                val idx = i + j
                if (idx < enabled.size) {
                    val t = enabled[idx]
                    val tc = card(14)
                    tc.addView(ic(t.icon, t.tint, 28))
                    val tt = tv(t.title, 15f, cText(), true)
                    tt.setPadding(0, dp(8), 0, 0)
                    tc.addView(tt)
                    val sub = tv(t.sub, 12f, cSub())
                    if (t.key == "devices") dashDevicesSub = sub
                    tc.addView(sub)
                    tc.setOnClickListener { t.action() }
                    val p = lp(0, WRAP, 1f)
                    p.marginEnd = if (j == 0) dp(10) else 0
                    row.addView(tc, p)
                } else {
                    row.addView(View(this), lp(0, 1, 1f))
                }
            }
            add(col, row, bottom = 10)
            i += 2
        }

        // more
        add(col, section("More"), bottom = 0)
        add(
            col,
            listCard(
                listOf(
                    Triple("Admin", "DHCP, firewall & ports", { InfoActivity.open(this, InfoKind.DHCP) }),
                    Triple("DNS Settings", "DNS policy, custom rules & host entries", { InfoActivity.open(this, InfoKind.DNS) }),
                    Triple("Firewall", "Firewall level", { InfoActivity.open(this, InfoKind.FIREWALL) }),
                    Triple("WAN Connections", "WAN connection details", {
                        InfoActivity.open(this, InfoKind.WAN)
                    }),
                    Triple("Reboot Modem", "Restarts the modem — all devices lose internet briefly", { confirmReboot() })
                ),
                dangerTitle = "Reboot Modem"
            )
        )
    }

    private fun speedText(): String =
        if (QuickSpeed.running && QuickSpeed.lastDown == null) "Testing…" else QuickSpeed.summary()

    private fun refreshDashboard(startSpeed: Boolean, force: Boolean) {
        lifecycleScope.launch {
            val ad = RouterAdapterFactory.getAdapter()
            val devs = try { ad.getDevices() } catch (e: Exception) { emptyList<Device>() }
            connectedCount = devs.count { it.isOnline }
            dashOnline?.text = connectedCount.toString()
            dashDevicesSub?.text = "$connectedCount online"
            dashStatus?.text = "ONLINE"

            val cpu = try { ad.getCpuUsagePercent() } catch (e: Exception) { null }
            if (cpu != null) cachedCpu = cpu
            dashCpu?.text = cachedCpu?.let { "$it%" } ?: "—"

            if (cachedSsid == null || force) {
                val ssid = try { ad.getWifiSsidName() } catch (e: Exception) { null }
                if (!ssid.isNullOrBlank()) cachedSsid = ssid
            }
            if (cachedModel == null || force) {
                val model = try { ad.getRouterModel() } catch (e: Exception) { null }
                if (!model.isNullOrBlank()) cachedModel = model
            }
            dashModel?.text = cachedModel ?: "Router"
            dashHero?.text = cachedSsid ?: cachedModel ?: "Router"
            if (tab == 0) currentPull?.done()
        }
        val wantSpeed = ThemeManager.autoSpeed && !QuickSpeed.running &&
            ((startSpeed && !QuickSpeed.isFresh()) || force)
        if (wantSpeed) runSpeed()
    }

    private fun runSpeed() {
        dashSpeed?.text = "Testing…"
        lifecycleScope.launch {
            QuickSpeed.run { stage -> dashSpeed?.text = stage }
            dashSpeed?.text = speedText()
        }
    }

    // ---- live loop: traffic every second, router data every few seconds
    private fun formatRate(bytesPerSec: Double): String = when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024 * 1024))
        bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024)
        else -> String.format("%.0f B/s", bytesPerSec)
    }

    private fun updateLiveTraffic() {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = System.currentTimeMillis()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            liveLabel = "Live  ↓ n/a   ↑ n/a"
        } else if (prevRx >= 0 && now > prevAt) {
            val sec = (now - prevAt) / 1000.0
            val down = (rx - prevRx) / sec
            val up = (tx - prevTx) / sec
            liveLabel = "Live  ↓ " + formatRate(down.coerceAtLeast(0.0)) + "   ↑ " + formatRate(up.coerceAtLeast(0.0))
        }
        prevRx = rx
        prevTx = tx
        prevAt = now
        dashLive?.text = liveLabel
    }

    private fun startLive() {
        liveJob?.cancel()
        prevRx = -1L
        liveJob = lifecycleScope.launch {
            var tick = 0
            while (isActive) {
                updateLiveTraffic()
                if (tick % 5 == 0 && tab == 0) refreshDashboard(startSpeed = tick == 0, force = false)
                if (tick % 6 == 3 && tab == 1) loadDevices()
                tick++
                delay(1000)
            }
        }
    }

    private fun confirmReboot() {
        NgDialog.confirm(
            this, "Reboot modem",
            "All connected devices will lose internet for a short time. Continue?",
            "Reboot", true
        ) {
            lifecycleScope.launch {
                val ok = try {
                    RouterAdapterFactory.getAdapter().restartRouter()
                } catch (e: Exception) {
                    false
                }
                toast(if (ok) "Modem is rebooting…" else "Reboot failed")
            }
        }
    }

    private fun logout() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun setDark(on: Boolean) {
        ThemeManager.dark = on
        AppCompatDelegate.setDefaultNightMode(
            if (on) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        showTab(tab)
    }

    // ---------------------------------------------------------------- DEVICES

    private fun buildDevices(): View = scroller { col ->
        val head = hrow()
        if (devFilter == 1) {
            head.addView(backButton { devFilter = 0; showTab(1) })
        }
        head.addView(tv(if (devFilter == 1) "Blocked Users" else "Devices", 22f, cText(), true), lp(0, WRAP, 1f))
        val refresh = FrameLayout(this)
        refresh.setPadding(dp(8), dp(8), dp(4), dp(8))
        refresh.addView(ic(NgIcon.REFRESH, cText(), 24))
        refresh.setOnClickListener { loadDevices() }
        head.addView(refresh)
        add(col, head, bottom = 12)

        val summary = hrow()
        val c1 = card(14)
        val n1 = tv(connectedCount.toString(), 24f, cText(), true)
        devConnected = n1
        c1.addView(n1)
        c1.addView(tv("Online", 12f, cSub()))
        c1.setOnClickListener { if (devFilter != 0) { devFilter = 0; showTab(1) } }
        devCardOnline = c1
        val c2 = card(14)
        val n2 = tv(blockedCount.toString(), 24f, ThemeManager.danger(), true)
        devBlocked = n2
        c2.addView(n2)
        c2.addView(tv("Blocked  ›", 12f, cSub()))
        c2.setOnClickListener { if (devFilter != 1) { devFilter = 1; showTab(1) } }
        devCardBlocked = c2
        val p1 = lp(0, WRAP, 1f)
        p1.marginEnd = dp(10)
        summary.addView(c1, p1)
        summary.addView(c2, lp(0, WRAP, 1f))
        add(col, summary, bottom = 14)

        val box = vcol()
        devList = box
        devSig = ""
        add(col, box, bottom = 0)
        if (devAll.isNotEmpty()) renderDevices()
        loadDevices()
    }

    private fun groupTitle(title: String, count: Int, color: Int): View {
        val r = hrow()
        r.setPadding(dp(2), dp(6), dp(2), 0)
        val dot = View(this)
        val dg = GradientDrawable()
        dg.shape = GradientDrawable.OVAL
        dg.setColor(color)
        dot.background = dg
        r.addView(dot, lp(dp(10), dp(10)))
        val t = tv(title, 20f, cText(), true)
        t.setPadding(dp(10), 0, 0, 0)
        r.addView(t, lp(0, WRAP, 1f))
        r.addView(pill(count.toString(), color))
        return r
    }

    private fun summaryBg(selected: Boolean, color: Int): Drawable {
        if (!selected) return NgKit.cardBg(this)
        val g = GradientDrawable()
        g.cornerRadius = dpf(ThemeManager.radiusDp())
        g.setColor(ColorUtils.blendARGB(ThemeManager.surface(), color, 0.12f))
        g.setStroke(dp(2), color)
        return g
    }

    private fun renderDevices() {
        val box = devList ?: return
        box.removeAllViews()
        devConnected?.text = connectedCount.toString()
        devBlocked?.text = blockedCount.toString()
        devCardOnline?.background = summaryBg(devFilter == 0, cAcc())
        devCardBlocked?.background = summaryBg(devFilter == 1, ThemeManager.danger())

        val list = devAll
        if (devFilter == 1) {
            val bl = list.filter { it.isBlocked }
            if (bl.isEmpty()) add(box, tv("No blocked users", 14f, cSub()), bottom = 0)
            for (d in bl) add(box, deviceCard(d), bottom = 12)
            return
        }

        val online = list.filter { it.isOnline && !it.isBlocked }
        val offline = list.filter { !it.isOnline && !it.isBlocked }

        add(box, groupTitle("Online Users", online.size, cAcc()), bottom = 10)
        if (online.isEmpty()) add(box, tv("No devices online right now", 14f, cSub()), bottom = 12)
        for (d in online) add(box, deviceCard(d), bottom = 12)

        val gap = View(this)
        add(box, gap, h = dp(8), bottom = 0)
        add(box, groupTitle("Offline Users", offline.size, cSub()), bottom = 10)
        if (offline.isEmpty()) add(box, tv("No offline devices", 14f, cSub()), bottom = 0)
        for (d in offline) add(box, deviceCard(d), bottom = 12)
    }

    private fun loadDevices() {
        val box = devList ?: return
        lifecycleScope.launch {
            val ad = RouterAdapterFactory.getAdapter()
            val devs = try { ad.getDevices() } catch (e: Exception) { emptyList<Device>() }
            val blocked = try { ad.getBlockedMacs() } catch (e: Exception) { emptySet<String>() }

            val list = ArrayList<Device>()
            for (d in devs) {
                list.add(d.copy(isBlocked = blocked.contains(d.macAddress.uppercase())))
            }
            val known = devs.map { it.macAddress.uppercase() }.toSet()
            for (m in blocked) {
                if (m !in known) {
                    list.add(Device(macAddress = m, displayName = "Blocked device", ipAddress = "—", isOnline = false, isBlocked = true))
                }
            }

            devAll = list
            connectedCount = devs.count { it.isOnline && !blocked.contains(it.macAddress.uppercase()) }
            blockedCount = blocked.size
            if (devList !== box) return@launch // tab changed
            val sig = list.joinToString("|") { it.macAddress + it.isOnline + it.isBlocked + it.displayName + it.ipAddress } +
                "#" + devFilter + names.hashCode()
            if (sig != devSig || box.childCount == 0) {
                devSig = sig
                renderDevices()
            } else {
                devConnected?.text = connectedCount.toString()
                devBlocked?.text = blockedCount.toString()
            }
            currentPull?.done()
        }
    }

    private fun deviceCard(d: Device): View {
        val c = card(14)
        val custom = names.getCustomName(d.macAddress)
        val shown = custom ?: DeviceNamer.pretty(d.displayName)

        val top = hrow()
        val nameView = tv(shown, 17f, cText(), true)
        top.addView(nameView)
        if (custom == null && DeviceNamer.isGeneric(d.displayName) && d.ipAddress.contains(".")) {
            lifecycleScope.launch {
                val host = DeviceNamer.reverseDns(d.ipAddress)
                if (!host.isNullOrBlank()) nameView.text = DeviceNamer.pretty(host)
            }
        }
        val edit = FrameLayout(this)
        edit.setPadding(dp(10), dp(4), dp(10), dp(4))
        edit.addView(ic(NgIcon.EDIT, cAcc(), 18))
        edit.setOnClickListener { rename(d, nameView.text.toString()) }
        top.addView(edit)
        top.addView(View(this), lp(0, 1, 1f))
        val statusText = if (d.isBlocked) "Blocked" else if (d.isOnline) "Online" else "Offline"
        top.addView(tv(statusText, 12f, if (d.isBlocked) ThemeManager.danger() else if (d.isOnline) cAcc() else cSub(), true))
        c.addView(top)

        val info = tv(d.ipAddress + " • " + d.macAddress, 12f, cSub())
        info.setPadding(0, dp(2), 0, dp(10))
        c.addView(info)

        val wasBlocked = d.isBlocked
        val btn = pill(if (wasBlocked) "Unblock" else "Block Internet", if (wasBlocked) cAcc() else ThemeManager.danger())
        btn.setOnClickListener {
            btn.isEnabled = false
            lifecycleScope.launch {
                val ad = RouterAdapterFactory.getAdapter()
                val ok = try {
                    if (wasBlocked) ad.unblockDevice(d.macAddress) else ad.blockDevice(d.macAddress)
                } catch (e: Exception) {
                    false
                }
                toast(if (ok) (if (wasBlocked) "Device unblocked" else "Device blocked") else "Action failed")
                loadDevices()
            }
        }
        c.addView(btn, lp(WRAP, WRAP))
        return c
    }

    private fun rename(d: Device, current: String) {
        NgDialog.input(this, "Rename device", d.ipAddress + " · " + d.macAddress, current, "Device name") { n ->
            names.setCustomName(d.macAddress, n)
            devSig = ""
            loadDevices()
        }
    }

    // ---------------------------------------------------------------- ADVANCED

    private fun buildAdvanced(): View = scroller { col ->
        add(col, tv("Advanced Settings", 22f, cText(), true), bottom = 14)

        add(
            col,
            listCard(
                listOf(
                    Triple("Parental Control", "Templates & restricted devices", { InfoActivity.open(this, InfoKind.PARENTAL) }),
                    Triple("DNS Settings", "DNS policy, custom rules & host entries", { InfoActivity.open(this, InfoKind.DNS) }),
                    Triple("WAN Connections", "WAN connection details", {
                        InfoActivity.open(this, InfoKind.WAN)
                    }),
                    Triple("Reboot Modem", "Restarts the modem — all devices lose internet briefly", { confirmReboot() })
                ),
                dangerTitle = "Reboot Modem"
            ),
            bottom = 14
        )

        add(col, section("Security"), bottom = 0)
        add(
            col,
            switchCard("Fingerprint Login", "Unlock your saved router login with your fingerprint", ThemeManager.fingerprintOn) {
                ThemeManager.fingerprintOn = it
            },
            bottom = 14
        )

        add(col, section("Monitoring"), bottom = 0)
        add(
            col,
            switchCard("New Device Alerts", "Check the router in the background and alert on new devices", ThemeManager.alertsOn) {
                ThemeManager.alertsOn = it
                if (it) startMonitoring() else stopService(Intent(this, ConnectionMonitorService::class.java))
            },
            bottom = 14
        )

        add(
            col,
            switchCard("Auto Speed Test", "Measure download and upload when the dashboard opens (~10 MB)", ThemeManager.autoSpeed) {
                ThemeManager.autoSpeed = it
            },
            bottom = 14
        )

        add(col, section("Tools"), bottom = 0)
        add(
            col,
            listCard(
                listOf(
                    Triple("MAC Filter", "Allow / block devices", {
                        startActivity(Intent(this, MacFilterActivity::class.java))
                    }),
                    Triple("Wi-Fi Settings", "SSID, password & guest WiFi", {
                        startActivity(Intent(this, WifiSettingsActivity::class.java))
                    }),
                    Triple("Logs", "Login & action history", {
                        startActivity(Intent(this, LogsActivity::class.java))
                    }),
                    Triple("Logout", "Back to the login screen", { logout() })
                )
            ),
            bottom = 0
        )
    }

    private fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        try {
            val svc = Intent(this, ConnectionMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------------------------------------------------------------- THEME

    private fun optionRow(items: List<Pair<String, String>>, selected: Int, onPick: (Int) -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        items.forEachIndexed { i, (title, sub) ->
            val sel = i == selected
            val b = vcol()
            b.setPadding(dp(10), dp(12), dp(10), dp(12))
            val g = GradientDrawable()
            g.cornerRadius = dpf(14f)
            g.setColor(ThemeManager.surface())
            g.setStroke(dp(if (sel) 2 else 1), if (sel) cAcc() else ThemeManager.border())
            b.background = g
            b.addView(tv(title, 13f, cText(), true))
            b.addView(tv(sub, 10f, cSub()))
            b.setOnClickListener { onPick(i) }
            val p = LinearLayout.LayoutParams(0, WRAP, 1f)
            p.marginEnd = dp(6)
            row.addView(b, p)
        }
        return row
    }

    private fun buildTheme(): View = scroller { col ->
        add(col, tv("Theme", 22f, cText(), true), bottom = 14)

        add(col, section("Appearance"), bottom = 0)
        add(
            col,
            switchCard("Dark Mode", "Toggle application theme", ThemeManager.dark) { setDark(it) },
            bottom = 12
        )

        // accent colour
        val accCard = card(14)
        accCard.addView(tv("Accent Color", 15f, cText(), true))
        accCard.addView(tv("Pick the app's brand color", 12f, cSub()))
        val swatches = LinearLayout(this)
        swatches.orientation = LinearLayout.HORIZONTAL
        swatches.setPadding(0, dp(12), 0, 0)
        for ((i, color) in ThemeManager.accents.withIndex()) {
            val sel = i == ThemeManager.accentIndex
            val box = vcol()
            box.gravity = Gravity.CENTER_HORIZONTAL
            val dot = TextView(this)
            dot.gravity = Gravity.CENTER
            dot.text = if (sel) "✓" else ""
            dot.setTextColor(Color.WHITE)
            dot.textSize = 16f
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(color)
            dot.background = g
            box.addView(dot, lp(dp(38), dp(38)))
            val label = tv(ThemeManager.accentNames[i], 10f, if (sel) cAcc() else cSub(), sel)
            label.setPadding(0, dp(4), 0, 0)
            box.addView(label)
            box.setOnClickListener {
                ThemeManager.accentIndex = i
                showTab(3)
            }
            swatches.addView(box, lp(0, WRAP, 1f))
        }
        accCard.addView(swatches)
        add(col, accCard, bottom = 14)

        add(col, section("Theme Style"), bottom = 0)
        add(
            col,
            optionRow(
                listOf(
                    "Classic" to "Simple flat",
                    "One UI" to "Glossy",
                    "Ice" to "Frosted",
                    "Halo" to "Glow outline"
                ),
                ThemeManager.style
            ) {
                ThemeManager.style = it
                showTab(3)
            },
            bottom = 12
        )
        add(
            col,
            switchCard("3D Effect", "Lifted, glowing cards. Off for flat look.", ThemeManager.effect3d) {
                ThemeManager.effect3d = it
                showTab(3)
            },
            bottom = 14
        )

        add(col, section("Corner Style"), bottom = 0)
        add(
            col,
            optionRow(
                listOf(
                    "Rounded" to "Softer corners",
                    "Compact" to "Modern, tighter",
                    "Square" to "Sharp corners"
                ),
                ThemeManager.corner
            ) {
                ThemeManager.corner = it
                showTab(3)
            },
            bottom = 14
        )

        add(col, section("Dashboard Widgets"), bottom = 0)
        val wc = card(14)
        ThemeManager.widgetKeys.forEach { (key, title) ->
            wc.addView(switchLine(title, null, ThemeManager.widgetOn(key)) { ThemeManager.setWidget(key, it) })
        }
        add(col, wc, bottom = 0)
    }
}
