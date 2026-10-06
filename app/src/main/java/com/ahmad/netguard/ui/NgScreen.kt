package com.ahmad.netguard.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.app.DatePickerDialog
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import java.util.Calendar
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
    protected lateinit var pull: PullRefreshLayout
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


    // ------------------------------------------------------------ shared helpers

    /** Rounded input box. Returns (container, editText). */
    protected fun inputField(hint: String, password: Boolean = false, initial: String = ""): Pair<View, EditText> {
        val et = EditText(this)
        et.setText(initial)
        et.hint = hint
        et.setSingleLine(true)
        et.textSize = 15f
        et.setTextColor(cText())
        et.setHintTextColor(cSub())
        et.inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT
        et.background = null
        et.setPadding(dp(14), dp(12), dp(14), dp(12))
        val box = hrow()
        val g = GradientDrawable()
        g.cornerRadius = dpf(16f)
        g.setColor(ColorUtils.setAlphaComponent(cAcc(), 18))
        g.setStroke(dp(1), ColorUtils.setAlphaComponent(cAcc(), 140))
        box.background = g
        box.addView(et, LinearLayout.LayoutParams(0, wrapP, 1f))
        if (password) {
            val eye = ic(NgIcon.EYE_OFF, cSub(), 22)
            val holder = FrameLayout(this)
            holder.setPadding(dp(8), dp(8), dp(12), dp(8))
            holder.addView(eye, FrameLayout.LayoutParams(dp(22), dp(22)))
            var shown = false
            holder.setOnClickListener {
                shown = !shown
                et.inputType = if (shown) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                eye.icon = if (shown) NgIcon.EYE else NgIcon.EYE_OFF
                et.setSelection(et.text.length)
            }
            box.addView(holder)
        }
        return box to et
    }

    protected fun fieldLabel(text: String): TextView {
        val t = tv(text, 12f, cSub(), true)
        t.setPadding(dp(4), dp(8), 0, dp(6))
        return t
    }

    /** Today / 7 days / 30 days / Custom chips. */
    protected fun rangeChips(selected: Int, onPick: (Int) -> Unit): View {
        val row = hrow()
        val labels = listOf("Today", "7 days", "30 days", "Custom")
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            val t = tv(label, 13f, if (sel) Color.WHITE else cText(), true)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(8), dp(10), dp(8), dp(10))
            val g = GradientDrawable()
            g.cornerRadius = dpf(40f)
            if (sel) g.setColor(cAcc()) else {
                g.setColor(ColorUtils.setAlphaComponent(cAcc(), 24))
            }
            t.background = g
            t.setOnClickListener { onPick(i) }
            val p = LinearLayout.LayoutParams(0, wrapP, 1f)
            p.marginEnd = dp(6)
            row.addView(t, p)
        }
        return row
    }

    protected fun startOfDay(millis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** idx: 0 today, 1 last 7 days, 2 last 30 days, 3 custom. */
    protected fun rangeBounds(idx: Int, customFrom: Long, customTo: Long): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val today = startOfDay(now)
        return when (idx) {
            0 -> today to now
            1 -> (today - 6 * 86_400_000L) to now
            2 -> (today - 29 * 86_400_000L) to now
            else -> customFrom to customTo
        }
    }

    protected fun pickRange(onDone: (Long, Long) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y1, m1, d1 ->
            val from = Calendar.getInstance()
            from.set(y1, m1, d1, 0, 0, 0)
            from.set(Calendar.MILLISECOND, 0)
            toast("Now pick the end date")
            DatePickerDialog(this, { _, y2, m2, d2 ->
                val to = Calendar.getInstance()
                to.set(y2, m2, d2, 23, 59, 59)
                to.set(Calendar.MILLISECOND, 999)
                if (to.timeInMillis < from.timeInMillis) {
                    toast("End date must be after the start date")
                } else {
                    onDone(from.timeInMillis, to.timeInMillis)
                }
            }, y1, m1, d1).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    protected fun formatDuration(ms: Long): String {
        val totalMin = ms / 60_000L
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h >= 24 -> "${h / 24}d ${h % 24}h ${m}m"
            h > 0 -> "${h}h ${m}m"
            totalMin > 0 -> "${m}m"
            ms > 0 -> "<1m"
            else -> "0m"
        }
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
        back.setPadding(dp(8), dp(8), dp(8), dp(8))
        val backBg = GradientDrawable()
        backBg.shape = GradientDrawable.OVAL
        backBg.setColor(ColorUtils.setAlphaComponent(cAcc(), 40))
        val backInner = FrameLayout(this)
        backInner.background = backBg
        val bi = ic(NgIcon.BACK, cAcc(), 22)
        val bil = FrameLayout.LayoutParams(dp(22), dp(22))
        bil.gravity = Gravity.CENTER
        backInner.addView(bi, bil)
        back.addView(backInner, FrameLayout.LayoutParams(dp(40), dp(40)))
        back.setOnClickListener { finish() }
        head.addView(back)
        val spacer = View(this)
        head.addView(spacer, LinearLayout.LayoutParams(dp(6), 1))
        head.addView(tv(title, 20f, cText(), true), LinearLayout.LayoutParams(0, wrapP, 1f))
        if (onRefresh != null) {
            val rf = FrameLayout(this)
            rf.setPadding(dp(10), dp(10), dp(10), dp(10))
            rf.addView(ic(NgIcon.REFRESH, cText(), 24))
            rf.setOnClickListener { onRefresh() }
            head.addView(rf)
        }
        rootView.addView(head, LinearLayout.LayoutParams(matchP, wrapP))

        pull = PullRefreshLayout(this)
        pull.onRefresh = onRefresh
        col = vcol()
        col.setPadding(dp(16), dp(8), dp(16), dp(28))
        pull.scroll.addView(col, FrameLayout.LayoutParams(matchP, wrapP))
        rootView.addView(pull, LinearLayout.LayoutParams(matchP, 0, 1f))
        setContentView(rootView)
    }
}
