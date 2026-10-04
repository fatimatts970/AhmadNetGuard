package com.ahmad.netguard.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.init(this)
        NgKit.applyNightMode()
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.background = NgKit.screenBg()
        NgKit.chrome(this)

        val acc = ThemeManager.accent()

        root.addView(android.view.View(this), LinearLayout.LayoutParams(0, 0, 1f))

        // logo
        val logo = LinearLayout(this)
        logo.gravity = Gravity.CENTER
        val lg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(ColorUtils.blendARGB(acc, Color.BLACK, 0.08f), ColorUtils.blendARGB(acc, Color.WHITE, 0.30f))
        )
        lg.cornerRadius = NgKit.dpf(this, 36f)
        logo.background = lg
        logo.elevation = NgKit.dpf(this, 8f)
        logo.addView(
            NgKit.icon(this, NgIcon.ROUTER, Color.WHITE),
            LinearLayout.LayoutParams(NgKit.dp(this, 68), NgKit.dp(this, 68))
        )
        root.addView(logo, LinearLayout.LayoutParams(NgKit.dp(this, 128), NgKit.dp(this, 128)))

        fun label(text: String, sp: Float, color: Int, bold: Boolean, topMargin: Int): TextView {
            val t = TextView(this)
            t.text = text
            t.textSize = sp
            t.setTextColor(color)
            t.gravity = Gravity.CENTER
            if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = NgKit.dp(this, topMargin)
            root.addView(t, lp)
            return t
        }

        label("Welcome to", 16f, ThemeManager.sub(), false, 32)
        label("AHMAD WiFi Manager", 28f, ThemeManager.text(), true, 6)
        label("𓆩 AHMAD RAHMANI 𓆪", 16f, acc, true, 10)
        label("Take full control of your Huawei router", 14f, ThemeManager.sub(), false, 6)

        root.addView(android.view.View(this), LinearLayout.LayoutParams(0, 0, 1f))
        val ver = label("v1.0", 12f, ThemeManager.sub(), false, 0)
        (ver.layoutParams as LinearLayout.LayoutParams).bottomMargin = NgKit.dp(this, 24)

        setContentView(root)

        lifecycleScope.launch {
            delay(900)
            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            finish()
        }
    }
}
