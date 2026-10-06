package com.ahmad.netguard.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class LoginActivity : AppCompatActivity() {

    private lateinit var credStore: RouterCredentialStore
    private lateinit var inputIp: EditText
    private lateinit var inputUser: EditText
    private lateinit var inputPass: EditText
    private lateinit var checkRemember: CheckBox
    private lateinit var btnConnect: LinearLayout
    private lateinit var btnConnectText: TextView
    private lateinit var btnConnectIcon: IconView
    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var scanBox: LinearLayout
    private lateinit var eyeIcon: IconView

    private var passVisible = false
    private var busy = false

    private fun dp(v: Int): Int = NgKit.dp(this, v)
    private fun dpf(v: Float): Float = NgKit.dpf(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.init(this)
        NgKit.applyNightMode()
        super.onCreate(savedInstanceState)
        credStore = RouterCredentialStore(this)
        NgKit.chrome(this)

        val sv = ScrollView(this)
        sv.background = NgKit.screenBg()
        sv.isFillViewport = true
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(20), dp(40), dp(20), dp(28))
        sv.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val acc = ThemeManager.accent()

        // logo
        val logo = LinearLayout(this)
        logo.gravity = Gravity.CENTER
        logo.background = NgKit.heroBg(this)
        logo.elevation = dpf(6f)
        logo.addView(NgKit.icon(this, NgIcon.ROUTER, Color.WHITE), LinearLayout.LayoutParams(dp(46), dp(46)))
        col.addView(logo, LinearLayout.LayoutParams(dp(84), dp(84)))

        col.addView(text("AHMAD WiFi Manager", 28f, ThemeManager.text(), true).also { it.gravity = Gravity.CENTER }, lpWrap(top = 14))
        col.addView(text("Sign in to manage your router", 14f, ThemeManager.sub(), false).also { it.gravity = Gravity.CENTER }, lpWrap(top = 2, bottom = 22))

        // card
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(18), dp(16), dp(18), dp(18))
        card.background = NgKit.heroBg(this)
        card.elevation = dpf(6f)

        inputIp = EditText(this)
        inputUser = EditText(this)
        inputPass = EditText(this)

        card.addView(text("Modem IP / Host", 13f, Color.WHITE, true), lpWrap(bottom = 6))
        card.addView(field(NgIcon.ROUTER, inputIp, "192.168.100.1", false), lpFull(bottom = 14))
        card.addView(text("Username", 13f, Color.WHITE, true), lpWrap(bottom = 6))
        card.addView(field(NgIcon.USER, inputUser, "admin", false), lpFull(bottom = 14))
        card.addView(text("Password", 13f, Color.WHITE, true), lpWrap(bottom = 6))
        card.addView(field(NgIcon.LOCK, inputPass, "Enter password", true), lpFull(bottom = 10))

        checkRemember = CheckBox(this)
        checkRemember.text = "Save credentials for next time"
        checkRemember.setTextColor(Color.WHITE)
        checkRemember.textSize = 14f
        checkRemember.buttonTintList = ColorStateList.valueOf(Color.WHITE)
        card.addView(checkRemember, lpWrap())
        col.addView(card, lpFull(bottom = 16))

        // error
        errorText = text("", 13f, ThemeManager.danger(), true)
        errorText.gravity = Gravity.CENTER
        errorText.visibility = View.GONE
        col.addView(errorText, lpFull(bottom = 10))

        // connect button
        btnConnect = LinearLayout(this)
        btnConnect.gravity = Gravity.CENTER
        btnConnect.orientation = LinearLayout.HORIZONTAL
        btnConnectIcon = NgKit.icon(this, NgIcon.ARROW_IN, Color.WHITE)
        btnConnect.addView(btnConnectIcon, LinearLayout.LayoutParams(dp(22), dp(22)))
        progress = ProgressBar(this)
        progress.visibility = View.GONE
        progress.indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
        btnConnect.addView(progress, LinearLayout.LayoutParams(dp(22), dp(22)))
        btnConnectText = text("Connect", 16f, Color.WHITE, true)
        btnConnectText.setPadding(dp(10), 0, 0, 0)
        btnConnect.addView(btnConnectText)
        setConnectStyle(true)
        btnConnect.setOnClickListener { onConnectClicked() }
        col.addView(btnConnect, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).also { it.bottomMargin = dp(12) })

        // biometric
        if (ThemeManager.fingerprint(this) && BiometricHelper.canUseBiometrics(this) && credStore.getPassword().isNotBlank()) {
            val bio = LinearLayout(this)
            bio.gravity = Gravity.CENTER
            bio.orientation = LinearLayout.HORIZONTAL
            val bg = GradientDrawable()
            bg.cornerRadius = dpf(40f)
            bg.setStroke(dp(2), ColorUtils.setAlphaComponent(acc, 160))
            bg.setColor(Color.TRANSPARENT)
            bio.background = bg
            bio.addView(NgKit.icon(this, NgIcon.FINGERPRINT, acc), LinearLayout.LayoutParams(dp(22), dp(22)))
            val bt = text("Login with Biometrics", 15f, acc, true)
            bt.setPadding(dp(10), 0, 0, 0)
            bio.addView(bt)
            bio.setOnClickListener {
                BiometricHelper.prompt(
                    activity = this,
                    onSuccess = {
                        attemptLogin(credStore.getGateway(), credStore.getUsername(), credStore.getPassword())
                    },
                    onFailure = { showError("Biometric authentication failed") }
                )
            }
            col.addView(bio, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).also { it.bottomMargin = dp(20) })
        }

        // auto discovery
        val disc = LinearLayout(this)
        disc.orientation = LinearLayout.HORIZONTAL
        disc.gravity = Gravity.CENTER_VERTICAL
        disc.addView(text("Auto-\nDiscovery", 13f, ThemeManager.sub(), true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        disc.addView(linkButton(NgIcon.RADAR, "Scan Modem") { scanModem() })
        val sp = View(this)
        disc.addView(sp, LinearLayout.LayoutParams(dp(12), 1))
        disc.addView(linkButton(NgIcon.SEARCH, "Scan IP") { scanIp() })
        col.addView(disc, lpFull(bottom = 10))

        scanBox = LinearLayout(this)
        scanBox.orientation = LinearLayout.VERTICAL
        col.addView(scanBox, lpFull())

        setContentView(sv)

        // saved values
        val savedGateway = credStore.getGateway()
        val savedUsername = credStore.getUsername()
        if (savedGateway.isBlank() || savedGateway == "192.168.100.1") {
            val detected = detectWifiGatewayIp()
            if (detected != null) inputIp.setText(detected)
            else if (savedGateway.isNotBlank()) inputIp.setText(savedGateway)
        } else {
            inputIp.setText(savedGateway)
        }
        if (savedUsername.isNotBlank()) inputUser.setText(savedUsername)
        checkRemember.isChecked = credStore.isRememberMeEnabled()
        if (credStore.isRememberMeEnabled() && credStore.getPassword().isNotBlank()) {
            inputPass.setText(credStore.getPassword())
        }
    }

    // ------------------------------------------------------------ UI helpers

    private fun text(t: String, sp: Float, color: Int, bold: Boolean): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = sp
        v.setTextColor(color)
        if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
        return v
    }

    private fun lpWrap(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(top)
        p.bottomMargin = dp(bottom)
        return p
    }

    private fun lpFull(bottom: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.bottomMargin = dp(bottom)
        return p
    }

    private fun field(icon: NgIcon, edit: EditText, hint: String, password: Boolean): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), 0, dp(12), 0)
        val bg = GradientDrawable()
        bg.cornerRadius = dpf(30f)
        bg.setColor(Color.WHITE)
        row.background = bg

        row.addView(NgKit.icon(this, icon, Color.parseColor("#334155")), LinearLayout.LayoutParams(dp(22), dp(22)))
        edit.hint = hint
        edit.setHintTextColor(Color.parseColor("#94A3B8"))
        edit.setTextColor(Color.parseColor("#0F172A"))
        edit.textSize = 16f
        edit.background = null
        edit.setSingleLine(true)
        edit.setPadding(dp(14), dp(16), dp(8), dp(16))
        if (password) {
            edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else if (icon == NgIcon.ROUTER) {
            edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        } else {
            edit.inputType = InputType.TYPE_CLASS_TEXT
        }
        row.addView(edit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (password) {
            eyeIcon = NgKit.icon(this, NgIcon.EYE_OFF, Color.parseColor("#334155"))
            val holder = FrameLayout(this)
            holder.setPadding(dp(6), dp(6), dp(6), dp(6))
            holder.addView(eyeIcon, FrameLayout.LayoutParams(dp(22), dp(22)))
            holder.setOnClickListener {
                passVisible = !passVisible
                edit.inputType = if (passVisible)
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                else
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                eyeIcon.icon = if (passVisible) NgIcon.EYE else NgIcon.EYE_OFF
                edit.setSelection(edit.text?.length ?: 0)
            }
            row.addView(holder)
        }
        return row
    }

    private fun linkButton(icon: NgIcon, label: String, action: () -> Unit): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(6), dp(10), dp(6), dp(10))
        r.addView(NgKit.icon(this, icon, ThemeManager.accent()), LinearLayout.LayoutParams(dp(20), dp(20)))
        val t = text(label, 14f, ThemeManager.accent(), true)
        t.setPadding(dp(6), 0, 0, 0)
        r.addView(t)
        r.setOnClickListener { action() }
        return r
    }

    private fun setConnectStyle(enabled: Boolean) {
        val g = GradientDrawable()
        g.cornerRadius = dpf(40f)
        g.setColor(
            if (enabled) ThemeManager.accent()
            else ColorUtils.setAlphaComponent(ThemeManager.sub(), 90)
        )
        btnConnect.background = g
        btnConnect.elevation = if (enabled) dpf(4f) else 0f
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------ actions

    private fun onConnectClicked() {
        if (busy) return
        val gateway = inputIp.text.toString().trim()
        val username = inputUser.text.toString().trim()
        val pass = inputPass.text.toString().trim()
        if (gateway.isEmpty() || pass.isEmpty()) {
            showError("Enter IP and Password")
            return
        }
        attemptLogin(gateway, username.ifEmpty { "admin" }, pass)
    }

    private fun detectWifiGatewayIp(): String? {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val gatewayInt = wifiManager?.dhcpInfo?.gateway ?: return null
            if (gatewayInt == 0) return null
            val bytes = byteArrayOf(
                (gatewayInt and 0xFF).toByte(),
                (gatewayInt shr 8 and 0xFF).toByte(),
                (gatewayInt shr 16 and 0xFF).toByte(),
                (gatewayInt shr 24 and 0xFF).toByte()
            )
            InetAddress.getByAddress(bytes).hostAddress
        } catch (e: Exception) {
            null
        }
    }

    private fun scanIp() {
        val ip = detectWifiGatewayIp()
        scanBox.removeAllViews()
        if (ip != null) {
            inputIp.setText(ip)
            scanBox.addView(routerChip(RouterNames.get(this, ip) ?: "Gateway", ip), lpFull(bottom = 8))
        } else {
            scanBox.addView(text("Not connected to WiFi — gateway not found", 13f, ThemeManager.sub(), false), lpWrap())
        }
    }

    private fun scanModem() {
        scanBox.removeAllViews()
        scanBox.addView(text("Searching…", 13f, ThemeManager.sub(), false), lpWrap())
        lifecycleScope.launch {
            val candidates = LinkedHashSet<String>()
            detectWifiGatewayIp()?.let { candidates.add(it) }
            candidates.addAll(listOf("192.168.100.1", "192.168.1.1", "192.168.0.1", "192.168.8.1", "192.168.2.1", "10.0.0.1"))
            val found: List<Pair<String, String?>> = withContext(Dispatchers.IO) {
                coroutineScope {
                    candidates.map { ip -> async { ip to probeModel(ip) } }.awaitAll()
                }.filter { it.second.first }.map { it.first to it.second.second }
            }
            scanBox.removeAllViews()
            if (found.isEmpty()) {
                scanBox.addView(text("No modem found — enter the IP manually", 13f, ThemeManager.sub(), false), lpWrap())
            } else {
                for ((ip, model) in found) {
                    val title = model ?: RouterNames.get(this@LoginActivity, ip) ?: "Router"
                    scanBox.addView(routerChip(title, ip), lpFull(bottom = 8))
                }
            }
        }
    }

    /** (reachable, model label) — login page se asli model naam nikalta hai. */
    private fun probeModel(ip: String): Pair<Boolean, String?> {
        var reachable = false
        for (path in listOf("/", "/index.asp", "/login.asp")) {
            try {
                val conn = URL("http://$ip$path").openConnection() as HttpURLConnection
                conn.connectTimeout = 1500
                conn.readTimeout = 1500
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                if (code <= 0) {
                    conn.disconnect()
                    continue
                }
                reachable = true
                val server = conn.getHeaderField("Server") ?: ""
                val body = try {
                    conn.inputStream.bufferedReader().use { r ->
                        val buf = CharArray(60000)
                        val n = r.read(buf)
                        if (n > 0) String(buf, 0, n) else ""
                    }
                } catch (e: Exception) {
                    ""
                }
                conn.disconnect()
                val hay = server + " " + body
                val model = Regex("\\b(HG|EG|HS|WS|EchoLife\\s?)[A-Za-z]?\\d{3,4}[A-Za-z0-9\\-]{0,6}\\b")
                    .find(hay)?.value?.trim()
                if (model != null) return true to RouterNames.label(model)
                val title = Regex("<title>([^<]{2,40})</title>", RegexOption.IGNORE_CASE)
                    .find(body)?.groupValues?.get(1)?.trim()
                if (!title.isNullOrBlank() && !title.equals("login", true) && !title.equals("index", true)) {
                    return true to title
                }
                if (hay.contains("huawei", true)) return true to "Huawei Router"
            } catch (e: Exception) {
                // next path
            }
        }
        return reachable to null
    }

    private fun routerChip(title: String, ip: String): View {
        val acc = ThemeManager.accent()
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(14), dp(12), dp(14), dp(12))
        val g = GradientDrawable()
        g.cornerRadius = dpf(16f)
        g.setColor(ColorUtils.setAlphaComponent(acc, 36))
        g.setStroke(dp(1), ColorUtils.setAlphaComponent(acc, 140))
        r.background = g
        r.addView(NgKit.icon(this, NgIcon.ROUTER, acc), LinearLayout.LayoutParams(dp(24), dp(24)))
        val tb = LinearLayout(this)
        tb.orientation = LinearLayout.VERTICAL
        tb.setPadding(dp(12), 0, 0, 0)
        tb.addView(text(title, 14f, ThemeManager.text(), true))
        tb.addView(text(ip, 12f, ThemeManager.sub(), false))
        r.addView(tb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.setOnClickListener { inputIp.setText(ip) }
        return r
    }

    private fun setBusy(on: Boolean) {
        busy = on
        progress.visibility = if (on) View.VISIBLE else View.GONE
        btnConnectIcon.visibility = if (on) View.GONE else View.VISIBLE
        btnConnectText.text = if (on) "Connecting…" else "Connect"
        setConnectStyle(!on)
    }

    private fun attemptLogin(gateway: String, username: String, pass: String) {
        lifecycleScope.launch {
            setBusy(true)
            errorText.visibility = View.GONE

            val adapter = RouterAdapterFactory.getAdapter()
            val success = adapter.login(gateway, username, pass)

            setBusy(false)

            if (success) {
                credStore.saveCredentials(gateway, username, pass)
                credStore.setRememberMe(checkRemember.isChecked)

                com.ahmad.netguard.history.AppDatabase.getInstance(this@LoginActivity).appLogDao().insert(
                    com.ahmad.netguard.history.AppLog(
                        type = "LOGIN",
                        message = "Logged in as $username",
                        success = true,
                        timestampMillis = System.currentTimeMillis()
                    )
                )

                val model = adapter.getRouterModel()
                if (!model.isNullOrBlank()) RouterNames.save(this@LoginActivity, gateway, model)
                val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                if (!model.isNullOrBlank()) intent.putExtra(HomeActivity.EXTRA_MODEL, model)
                startActivity(intent)
                finish()
            } else {
                com.ahmad.netguard.history.AppDatabase.getInstance(this@LoginActivity).appLogDao().insert(
                    com.ahmad.netguard.history.AppLog(
                        type = "LOGIN",
                        message = "Failed login attempt as $username",
                        success = false,
                        timestampMillis = System.currentTimeMillis()
                    )
                )
                showError("Could not connect: check IP, username and password")
            }
        }
    }
}
