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
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.history.ConnectionMonitorService
import com.ahmad.netguard.model.Device
import com.ahmad.netguard.network.DeviceNameStore
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
import kotlinx.coroutines.launch

private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * AHMAD NetGuard — naya Home screen.
 * 4 tabs: Dashboard / Devices / Advanced / Theme.
 * Poora UI code se bana hai (XML layout nahi), taake build mein resource errors na aayen.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var creds: RouterCredentialStore
    private lateinit var names: DeviceNameStore
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var bar: LinearLayout

    private var tab = 0

    // Dashboard state
    private var cachedModel: String? = null
    private var cachedCpu: Int? = null
    private var connectedCount = 0
    private var blockedCount = 0
    private var dashModel: TextView? = null
    private var dashCpu: TextView? = null
    private var dashStatus: TextView? = null
    private var dashDevicesSub: TextView? = null

    // Devices state
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
        loadDashboardData()
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
        toast("Next stage — is feature ke liye router capture chahiye")
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

    private fun cardBg(): Drawable {
        val surface = ThemeManager.surface()
        val acc = ThemeManager.accent()
        val g = GradientDrawable()
        g.cornerRadius = dpf(ThemeManager.radiusDp())
        when (ThemeManager.style) {
            1 -> { // One UI — glossy gradient
                g.orientation = GradientDrawable.Orientation.TL_BR
                g.colors = intArrayOf(ColorUtils.blendARGB(surface, acc, 0.22f), surface)
            }
            2 -> { // Ice — frosted / translucent
                g.setColor(ColorUtils.setAlphaComponent(surface, 200))
                g.setStroke(dp(1), ColorUtils.setAlphaComponent(Color.WHITE, 140))
            }
            3 -> { // Halo — glowing outline
                g.setColor(surface)
                g.setStroke(dp(2), ColorUtils.setAlphaComponent(acc, 150))
            }
            else -> { // Classic
                g.setColor(surface)
                g.setStroke(dp(1), ThemeManager.border())
            }
        }
        return g
    }

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

    private fun circleEmoji(emoji: String): TextView {
        val t = tv(emoji, 18f, cText())
        t.gravity = Gravity.CENTER
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(ColorUtils.setAlphaComponent(cAcc(), 38))
        t.background = g
        return t
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
        val sv = ScrollView(this)
        sv.clipToPadding = false
        val col = vcol()
        col.setPadding(dp(16), dp(14), dp(16), dp(28))
        build(col)
        sv.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        return sv
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
        r.addView(tv("›", 22f, cSub()))
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
        root.setBackgroundColor(ThemeManager.bg())
        window.statusBarColor = ThemeManager.bg()
        window.navigationBarColor = ThemeManager.bar()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !ThemeManager.dark

        bar.removeAllViews()
        bar.setBackgroundColor(ThemeManager.bar())
        bar.setPadding(dp(8), dp(6), dp(8), dp(6))
        val items = listOf("🏠" to "Dashboard", "📱" to "Devices", "⚙️" to "Advanced", "🎨" to "Theme")
        items.forEachIndexed { i, (emoji, label) ->
            val sel = i == tab
            val item = vcol()
            item.gravity = Gravity.CENTER_HORIZONTAL
            item.setPadding(dp(4), dp(6), dp(4), dp(6))
            item.addView(tv(emoji, 20f, cText()))
            item.addView(tv(label, 11f, if (sel) cAcc() else cSub(), sel))
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
    }

    // ---------------------------------------------------------------- DASHBOARD

    private class Tile(val key: String, val emoji: String, val title: String, val sub: String, val action: () -> Unit)

    private fun tiles(): List<Tile> = listOf(
        Tile("wan", "🌐", "WAN Status", "PPPoE & IP details") { startActivity(Intent(this, WanConfigActivity::class.java)) },
        Tile("devices", "📱", "Devices", "$connectedCount connected") { showTab(1) },
        Tile("optical", "💡", "Optical Info", "Laser power & temp") { startActivity(Intent(this, OpticalInfoActivity::class.java)) },
        Tile("ports", "🔌", "Ethernet Ports", "Link status & speeds") { soon() },
        Tile("devinfo", "🖥️", "Device Info", "CPU, RAM & Versions") { showDeviceInfo() },
        Tile("usage", "📊", "Usage", "Traffic statistics") { startActivity(Intent(this, NetStatsActivity::class.java)) },
        Tile("voip", "📞", "VoIP Status", "SIP line status") { soon() },
        Tile("wifipass", "🔑", "Wi-Fi Password", "Change SSID & Key") { startActivity(Intent(this, WifiSettingsActivity::class.java)) },
        Tile("guest", "👥", "Guest", "Guest WiFi & users") { startActivity(Intent(this, WifiSettingsActivity::class.java)) },
        Tile("macfilter", "🛡️", "MAC Filter", "Allow/Block devices") { startActivity(Intent(this, MacFilterActivity::class.java)) },
        Tile("parental", "👪", "Parental Control", "Templates & restrictions") { soon() },
        Tile("blocknet", "🚫", "Block Internet", "without disconnect") { showTab(1) }
    )

    private fun buildDashboard(): View = scroller { col ->
        // header
        val head = hrow()
        val titleBox = vcol()
        val model = tv(cachedModel ?: "Router", 20f, cText(), true)
        dashModel = model
        titleBox.addView(model)
        titleBox.addView(tv("● " + creds.getGateway(), 12f, cAcc()))
        head.addView(titleBox, lp(0, WRAP, 1f))
        val moon = tv(if (ThemeManager.dark) "☀️" else "🌙", 22f, cText())
        moon.setPadding(dp(10), dp(6), dp(10), dp(6))
        moon.setOnClickListener { setDark(!ThemeManager.dark) }
        head.addView(moon)
        val out = tv("🚪", 22f, cText())
        out.setPadding(dp(10), dp(6), dp(6), dp(6))
        out.setOnClickListener { logout() }
        head.addView(out)
        add(col, head, bottom = 12)

        // hero card
        val hero = card(18)
        val top = hrow()
        val heroTitle = vcol()
        heroTitle.addView(tv(cachedModel ?: "Router", 20f, cText(), true))
        heroTitle.addView(tv(creds.getGateway() + " · connected", 12f, cSub()))
        top.addView(heroTitle, lp(0, WRAP, 1f))
        val status = pill(if (cachedModel == null) "Loading" else "ONLINE", cAcc())
        dashStatus = status
        top.addView(status)
        hero.addView(top)

        val glow = tv("📶", 54f, cAcc())
        glow.gravity = Gravity.CENTER
        glow.setPadding(0, dp(10), 0, dp(10))
        hero.addView(glow, lp(MATCH, WRAP))

        val cpu = tv("CPU: " + (cachedCpu?.let { "$it%" } ?: "—"), 12f, cSub(), true)
        dashCpu = cpu
        hero.addView(cpu)
        hero.addView(divider(), lp(MATCH, dp(1)).also { it.topMargin = dp(10); it.bottomMargin = dp(6) })

        val guestRow = hrow()
        guestRow.addView(tv("📡  Guest WiFi", 14f, cText(), true), lp(0, WRAP, 1f))
        val sw = SwitchCompat(this)
        styleSwitch(sw)
        sw.setOnClickListener {
            val on = sw.isChecked
            val ssid = creds.getGuestSsid()
            val key = creds.getGuestKey()
            if (ssid.isBlank() || key.length < 8) {
                sw.isChecked = !on
                toast("Pehle Guest naam aur password (8+) set karo")
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
                    toast("Guest WiFi change nahi hua")
                }
            }
        }
        guestRow.addView(sw)
        hero.addView(guestRow)
        add(col, hero, bottom = 16)

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
                    tc.addView(tv(t.emoji, 22f, cText()))
                    tc.addView(tv(t.title, 15f, cText(), true))
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
                    Triple("Admin", "DHCP, firewall & ports", { soon() }),
                    Triple("DNS Settings", "DNS policy, custom rules & host entries", { soon() }),
                    Triple("Firewall", "Firewall level", { soon() }),
                    Triple("WAN Connections", "Add, edit, or bind WAN connections", {
                        startActivity(Intent(this, WanConfigActivity::class.java))
                    }),
                    Triple("Reboot Modem", "Restarts the modem — all devices lose internet briefly", { confirmReboot() })
                ),
                dangerTitle = "Reboot Modem"
            )
        )
    }

    private fun loadDashboardData() {
        lifecycleScope.launch {
            val ad = RouterAdapterFactory.getAdapter()
            val model = try { ad.getRouterModel() } catch (e: Exception) { null }
            val cpu = try { ad.getCpuUsagePercent() } catch (e: Exception) { null }
            val devs = try { ad.getDevices() } catch (e: Exception) { emptyList<Device>() }
            if (!model.isNullOrBlank()) cachedModel = model
            cachedCpu = cpu
            connectedCount = devs.size
            dashModel?.text = cachedModel ?: "Router"
            dashCpu?.text = "CPU: " + (cpu?.let { "$it%" } ?: "—")
            dashStatus?.text = if (cachedModel == null) "Loading" else "ONLINE"
            dashDevicesSub?.text = "$connectedCount connected"
        }
    }

    private fun showDeviceInfo() {
        val msg = "Model: " + (cachedModel ?: "—") +
            "\nGateway: " + creds.getGateway() +
            "\nCPU: " + (cachedCpu?.let { "$it%" } ?: "—")
        AlertDialog.Builder(this)
            .setTitle("Device Information")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun confirmReboot() {
        AlertDialog.Builder(this)
            .setTitle("Reboot Modem")
            .setMessage("Saare devices ka internet thodi der ke liye ruk jayega. Continue?")
            .setPositiveButton("Reboot") { _, _ ->
                lifecycleScope.launch {
                    val ok = try {
                        RouterAdapterFactory.getAdapter().restartRouter()
                    } catch (e: Exception) {
                        false
                    }
                    toast(if (ok) "Modem reboot ho raha hai…" else "Reboot fail hua")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        head.addView(tv("Connected Devices", 22f, cText(), true), lp(0, WRAP, 1f))
        val refresh = tv("🔄", 22f, cText())
        refresh.setPadding(dp(10), dp(6), dp(6), dp(6))
        refresh.setOnClickListener { loadDevices() }
        head.addView(refresh)
        add(col, head, bottom = 12)

        val summary = hrow()
        val c1 = card(14)
        val n1 = tv(connectedCount.toString(), 24f, cText(), true)
        devConnected = n1
        c1.addView(n1)
        c1.addView(tv("Connected", 12f, cSub()))
        val c2 = card(14)
        val n2 = tv(blockedCount.toString(), 24f, ThemeManager.danger(), true)
        devBlocked = n2
        c2.addView(n2)
        c2.addView(tv("Blocked", 12f, cSub()))
        val p1 = lp(0, WRAP, 1f)
        p1.marginEnd = dp(10)
        summary.addView(c1, p1)
        summary.addView(c2, lp(0, WRAP, 1f))
        add(col, summary, bottom = 12)

        val hint = card(12)
        hint.addView(tv("✎ dabao device ka naam badalne ke liye. Block Internet se device poori tarah block ho jata hai.", 12f, cSub()))
        add(col, hint, bottom = 12)

        val box = vcol()
        devList = box
        add(col, box, bottom = 0)
        loadDevices()
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

            connectedCount = devs.size
            blockedCount = blocked.size
            if (devList !== box) return@launch // tab badal gaya

            devConnected?.text = devs.size.toString()
            devBlocked?.text = blocked.size.toString()
            box.removeAllViews()
            if (list.isEmpty()) {
                add(box, tv("Koi device nahi mila", 14f, cSub()), bottom = 0)
            }
            for (d in list) add(box, deviceCard(d), bottom = 12)
        }
    }

    private fun deviceCard(d: Device): View {
        val c = card(14)
        val custom = names.getCustomName(d.macAddress)
        val shown = custom ?: d.displayName.ifBlank { "Unknown" }

        val top = hrow()
        top.addView(tv(shown, 16f, cText(), true))
        val edit = tv("✎", 16f, cAcc(), true)
        edit.setPadding(dp(10), dp(4), dp(10), dp(4))
        edit.setOnClickListener { rename(d, shown) }
        top.addView(edit)
        top.addView(View(this), lp(0, 1, 1f))
        val statusText = if (d.isBlocked) "Blocked" else if (d.isOnline) "Online" else "Offline"
        top.addView(tv(statusText, 12f, if (d.isBlocked) ThemeManager.danger() else cAcc(), true))
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
                toast(if (ok) (if (wasBlocked) "Unblocked" else "Blocked") else "Fail hua")
                loadDevices()
            }
        }
        c.addView(btn, lp(WRAP, WRAP))
        return c
    }

    private fun rename(d: Device, current: String) {
        val et = EditText(this)
        et.setText(current)
        et.setSelection(et.text.length)
        AlertDialog.Builder(this)
            .setTitle("Device ka naam")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty()) {
                    names.setCustomName(d.macAddress, n)
                    loadDevices()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------- ADVANCED

    private fun buildAdvanced(): View = scroller { col ->
        add(col, tv("Advanced Settings", 22f, cText(), true), bottom = 14)

        add(
            col,
            listCard(
                listOf(
                    Triple("Parental Control", "Templates & restricted devices", { soon() }),
                    Triple("DNS Settings", "DNS policy, custom rules & host entries", { soon() }),
                    Triple("WAN Connections", "Add, edit, or bind multiple WAN connections", {
                        startActivity(Intent(this, WanConfigActivity::class.java))
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
            switchCard("Fingerprint Login", "Saved router login fingerprint se unlock karo", ThemeManager.fingerprintOn) {
                ThemeManager.fingerprintOn = it
            },
            bottom = 14
        )

        add(col, section("Monitoring"), bottom = 0)
        add(
            col,
            switchCard("New Device Alerts", "Background mein router check karke naye device ka alert", ThemeManager.alertsOn) {
                ThemeManager.alertsOn = it
                if (it) startMonitoring() else stopService(Intent(this, ConnectionMonitorService::class.java))
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
                    Triple("Logout", "Login screen par wapas", { logout() })
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
