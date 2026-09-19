package com.ahmad.netguard.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.R
import com.ahmad.netguard.databinding.ActivityWifiSettingsBinding
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class WifiSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWifiSettingsBinding
    private lateinit var credStore: RouterCredentialStore
    private var guestListPasswordVisible = false
    private var editingProfileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWifiSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credStore = RouterCredentialStore(this)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnToggleWifiPassword.setOnClickListener {
            togglePasswordVisibility(binding.inputWifiPassword, binding.btnToggleWifiPassword)
        }
        binding.btnToggleGuestPassword.setOnClickListener {
            togglePasswordVisibility(binding.inputGuestPassword, binding.btnToggleGuestPassword)
        }

        binding.btnAddGuestWifi.setOnClickListener {
            val ssid = binding.inputGuestSsid.text.toString().trim()
            val pass = binding.inputGuestPassword.text.toString().trim()
            if (ssid.isEmpty() || pass.length < 8) {
                Toast.makeText(this, "Enter a guest name and a password (8+ characters)", Toast.LENGTH_SHORT).show()
            } else {
                lifecycleScope.launch {
                    binding.progressGuestWifi.visibility = View.VISIBLE
                    val router = RouterAdapterFactory.getAdapter()
                    val diagnostic = router.setGuestWifiDiagnostic(ssid, pass, true)
                    binding.progressGuestWifi.visibility = View.GONE
                    val success = diagnostic == "SUCCESS" || diagnostic == "APPLIED"
                    val msg = when (diagnostic) {
                        "SUCCESS" -> "Guest WiFi is on: $ssid"
                        "APPLIED" -> "Applying — router WiFi will restart for a few seconds, then guest WiFi will be on."
                        else -> diagnostic
                    }
                    if (success) {
                        credStore.saveGuestProfile(ssid, pass, editingProfileName)
                        editingProfileName = null
                        binding.inputGuestSsid.setText("")
                        binding.inputGuestPassword.setText("")
                        renderGuestList()
                    }
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        binding.btnSaveWifiSettings.setOnClickListener {
            val ssid = binding.inputSsid.text.toString().trim()
            val password = binding.inputWifiPassword.text.toString().trim()

            if (ssid.isEmpty() || password.isEmpty()) {
                binding.textWifiError.text = "SSID and Password required"
                binding.textWifiError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            lifecycleScope.launch {
                binding.btnSaveWifiSettings.isEnabled = false
                binding.progressWifiSave.visibility = View.VISIBLE
                binding.textWifiError.visibility = View.GONE

                val router = RouterAdapterFactory.getAdapter()
                val success = router.updateWifiSettings(ssid, password)

                binding.btnSaveWifiSettings.isEnabled = true
                binding.progressWifiSave.visibility = View.GONE

                if (success) {
                    Toast.makeText(this@WifiSettingsActivity, "Wi-Fi Settings Updated!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    binding.textWifiError.text = "Update Failed! Check credentials."
                    binding.textWifiError.visibility = View.VISIBLE
                }
            }
        }

        binding.btnRebootRouter.setOnClickListener {
            lifecycleScope.launch {
                binding.btnRebootRouter.isEnabled = false
                val router = RouterAdapterFactory.getAdapter()
                val success = router.restartRouter()
                binding.btnRebootRouter.isEnabled = true
                val msg = if (success) "Router is rebooting..." else "Reboot failed! Check connection."
                Toast.makeText(this@WifiSettingsActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        renderGuestList()
    }

    // Router hardware sirf EK guest WLAN slot support karta hai — jo bhi profile
    // last "Save/Turn On" hua wahi actually broadcast ho raha hai. Baaki profiles
    // yahan sirf local yaad rakhe hue hain taake dobara on karna aasan ho.
    private fun renderGuestList() {
        val profiles = credStore.getGuestProfiles()
        binding.layoutGuestList.removeAllViews()

        if (profiles.isEmpty()) {
            binding.textNoGuests.visibility = View.VISIBLE
            return
        }
        binding.textNoGuests.visibility = View.GONE
        val activeSsid = credStore.getGuestSsid()
        profiles.forEach { (ssid, pass) ->
            binding.layoutGuestList.addView(buildGuestCard(ssid, pass, ssid == activeSsid))
        }
    }

    private fun buildGuestCard(ssid: String, pass: String, isActive: Boolean): CardView {
        val density = resources.displayMetrics.density
        val card = CardView(this).apply {
            radius = 16 * density
            setCardBackgroundColor(getColor(R.color.card_white))
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * density).toInt() }
        }

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = ssid
            setTextColor(getColor(R.color.text_primary))
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (isActive) {
            titleRow.addView(TextView(this).apply {
                text = "● ON"
                setTextColor(getColor(R.color.green_online))
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_pill_badge)
                setPadding((10 * density).toInt(), (5 * density).toInt(), (10 * density).toInt(), (5 * density).toInt())
            })
        }
        outer.addView(titleRow)

        val passRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            (layoutParams as? LinearLayout.LayoutParams)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * density).toInt() }
        }
        val passText = TextView(this).apply {
            text = if (guestListPasswordVisible) pass else "•".repeat(pass.length)
            setTextColor(getColor(R.color.text_secondary))
            textSize = 13f
        }
        val eyeBtn = android.widget.ImageButton(this).apply {
            setImageResource(if (guestListPasswordVisible) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_menu_view)
            background = null
            layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), (28 * density).toInt()).apply {
                marginStart = (8 * density).toInt()
            }
            setOnClickListener {
                guestListPasswordVisible = !guestListPasswordVisible
                renderGuestList()
            }
        }
        passRow.addView(passText)
        passRow.addView(eyeBtn)
        outer.addView(passRow)

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply {
                topMargin = (12 * density).toInt(); bottomMargin = (12 * density).toInt()
            }
            setBackgroundColor(getColor(R.color.bg_screen))
        }
        outer.addView(divider)

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        fun pillButton(label: String, bg: Int, textColor: Int): TextView = TextView(this).apply {
            text = label
            setTextColor(getColor(textColor))
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundResource(bg)
            val padH = (14 * density).toInt(); val padV = (9 * density).toInt()
            setPadding(padH, padV, padH, padV)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * density).toInt()
            }
        }

        val editBtn = pillButton("Edit", R.drawable.bg_pill_outline, R.color.brand_dark).apply {
            setOnClickListener {
                editingProfileName = ssid
                binding.inputGuestSsid.setText(ssid)
                binding.inputGuestPassword.setText(pass)
                Toast.makeText(this@WifiSettingsActivity, "Edit above, then tap Save / Turn On to update this guest", Toast.LENGTH_SHORT).show()
            }
        }
        val deleteBtn = pillButton("Delete / Turn Off", R.drawable.bg_pill_outline_red, R.color.danger).apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = 0
            setOnClickListener {
                lifecycleScope.launch {
                    binding.progressGuestWifi.visibility = View.VISIBLE
                    val router = RouterAdapterFactory.getAdapter()
                    val diagnostic = router.setGuestWifiDiagnostic(ssid, pass, false)
                    binding.progressGuestWifi.visibility = View.GONE
                    val success = diagnostic == "SUCCESS" || diagnostic == "APPLIED"
                    val msg = when (diagnostic) {
                        "SUCCESS" -> "Guest WiFi turned off"
                        "APPLIED" -> "Turning off — router WiFi will restart for a few seconds."
                        else -> diagnostic
                    }
                    if (success) {
                        credStore.deleteGuestProfile(ssid)
                        if (editingProfileName == ssid) editingProfileName = null
                        renderGuestList()
                    }
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        btnRow.addView(editBtn)
        btnRow.addView(deleteBtn)
        outer.addView(btnRow)

        card.addView(outer)
        return card
    }

    private val passwordVisibilityState = mutableMapOf<Int, Boolean>()

    private fun togglePasswordVisibility(input: android.widget.EditText, icon: android.widget.ImageView) {
        val isCurrentlyVisible = passwordVisibilityState[input.id] ?: false
        if (isCurrentlyVisible) {
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            icon.setImageResource(android.R.drawable.ic_menu_view)
        } else {
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            icon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        }
        passwordVisibilityState[input.id] = !isCurrentlyVisible
        input.setSelection(input.text.length)
    }
}
