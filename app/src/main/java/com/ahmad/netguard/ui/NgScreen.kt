package com.ahmad.netguard.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils

/**
 * Naye themed screens ka base: back + title + refresh header, scrolling column,
 * aur cards/pills ke helpers. Theme (accent/dark/style/corner) Home jaisi hi lagti hai.
 */
abstract class NgScreen : AppCompatActivity() {

    protected lateinit var col: LinearLayout
    private lateinit var rootView: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.init(this)
        NgKit.applyNightMode()
        super.onCreate(savedInstanceState)
    }

    protected val matchP = ViewGroup.LayoutParams.MATCH_PARENT
    protected val wrapP = ViewGroup.LayoutParams.WRAP_CONTENT

    protected fun dp(v: Int): Int = NgKit.dp(this, v)
    protected fun dpf(v: Float): Float = NgKit.dpf(this, v)
    protected fun cText(): Int = ThemeManager.text()
    protected fun cSub(): Int = ThemeManager.sub()
    protected fun cAcc(): Int = ThemeManager.accent()

    protected fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    protected fun tv(text: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    protected fun vcol(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        return l
    }

    protected fun hrow(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.HORIZONTAL
        l.gravity = Gravity.CENTER_VERTICAL
        return l
    }

    protected fun ic(icon: NgIcon, color: Int, sizeDp: Int, filled: Boolean = false): IconView {
        val v = NgKit.icon(this, icon, color, filled)
        v.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        return v
    }

    protected fun add(
        parent: LinearLayout, v: View, w: Int = matchP, h: Int = wrapP,
        weight: Float = 0f, bottom: Int = 12, end: Int = 0
    ) {
        val p = LinearLayout.LayoutParams(w, h, weight)
        p.bottomMargin = dp(bottom)
        p.marginEnd = dp(end)
        parent.addView(v, p)
    }

    protected fun card(pad: Int = 16): LinearLayout {
        val c = vcol()
        c.setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
        c.background = NgKit.cardBg(this)
        c.elevation = if (ThemeManager.effect3d) dpf(6f) else 0f
        return c
    }

    protected fun pill(text: String, color: Int, filled: Boolean = false): TextView {
        val t = tv(text, 12f, if (filled) Color.WHITE else color, true)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        val g = GradientDrawable()
        g.cornerRadius = dpf(40f)
        g.setColor(if (filled) color else ColorUtils.setAlphaComponent(color, 40))
        t.background = g
        return t
    }

    protected fun section(title: String): TextView {
        val t = tv(title.uppercase(), 12f, cSub(), true)
        t.letterSpacing = 0.08f
        t.setPadding(dp(4), dp(8), 0, dp(8))
        return t
    }

    protected fun divider(): View {
        val v = View(this)
        v.setBackgroundColor(ThemeManager.border())
        v.layoutParams = LinearLayout.LayoutParams(matchP, dp(1))
        return v
    }

    /** key : value ki ek line (card ke andar). */
    protected fun kvRow(label: String, value: String): View {
        val r = hrow()
        r.setPadding(0, dp(9), 0, dp(9))
        r.addView(tv(label, 13f, cSub()), LinearLayout.LayoutParams(0, wrapP, 0.9f))
        val v = tv(value.ifBlank { "—" }, 14f, cText(), true)
        v.gravity = Gravity.END
        r.addView(v, LinearLayout.LayoutParams(0, wrapP, 1.1f))
        return r
    }

    /** Header (back + title + optional refresh) aur scrolling column banata hai. */
    protected fun setupScreen(title: String, onRefresh: (() -> Unit)?) {
        NgKit.chrome(this)
        rootView = LinearLayout(this)
        rootView.orientation = LinearLayout.VERTICAL
        rootView.background = NgKit.screenBg()

        val head = hrow()
        head.setPadding(dp(8), dp(10), dp(12), dp(6))
        val back = FrameLayout(this)
        back.setPadding(dp(10), dp(10), dp(10), dp(10))
        val bi = ic(NgIcon.CHEVRON, cText(), 24)
        bi.rotation = 180f
        back.addView(bi)
        back.setOnClickListener { finish() }
        head.addView(back)
        head.addView(tv(title, 20f, cText(), true), LinearLayout.LayoutParams(0, wrapP, 1f))
        if (onRefresh != null) {
            val rf = FrameLayout(this)
            rf.setPadding(dp(10), dp(10), dp(10), dp(10))
            rf.addView(ic(NgIcon.REFRESH, cText(), 24))
            rf.setOnClickListener { onRefresh() }
            head.addView(rf)
        }
        rootView.addView(head, LinearLayout.LayoutParams(matchP, wrapP))

        val sv = ScrollView(this)
        sv.clipToPadding = false
        col = vcol()
        col.setPadding(dp(16), dp(8), dp(16), dp(28))
        sv.addView(col, FrameLayout.LayoutParams(matchP, wrapP))
        rootView.addView(sv, LinearLayout.LayoutParams(matchP, 0, 1f))
        setContentView(rootView)
    }
}
