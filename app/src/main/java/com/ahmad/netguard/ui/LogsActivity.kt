package com.ahmad.netguard.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.history.AppDatabase
import com.ahmad.netguard.history.AppLog
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Activity logs with filter chips, clear and share. */
class LogsActivity : NgScreen() {

    private val filters = listOf(
        "All" to null, "Connection" to "CONNECTION", "Login" to "LOGIN",
        "Block" to "BLOCK", "Unblock" to "UNBLOCK", "Security" to "SECURITY"
    )
    private var filterIdx = 0
    private var shown: List<AppLog> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("Logs") { load() }
        load()
    }

    private fun colorFor(type: String): Int = when (type) {
        "CONNECTION" -> cAcc()
        "LOGIN" -> Color.parseColor("#6D4FC2")
        "BLOCK" -> ThemeManager.danger()
        "UNBLOCK" -> Color.parseColor("#16A34A")
        "SECURITY" -> Color.parseColor("#D97706")
        else -> cSub()
    }

    private fun load() {
        showLoading()
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@LogsActivity).appLogDao()
            val type = filters[filterIdx].second
            val logs: List<AppLog> = try {
                if (type == null) dao.getAllLogs() else dao.getLogsByType(type)
            } catch (e: Exception) {
                emptyList()
            }
            shown = logs
            col.removeAllViews()

            // chips
            val hs = HorizontalScrollView(this@LogsActivity)
            hs.isHorizontalScrollBarEnabled = false
            val chips = hrow()
            filters.forEachIndexed { i, (label, _) ->
                val sel = i == filterIdx
                val t = tv(label, 13f, if (sel) Color.WHITE else cText(), true)
                t.setPadding(dp(16), dp(9), dp(16), dp(9))
                val g = GradientDrawable()
                g.cornerRadius = dpf(40f)
                g.setColor(if (sel) cAcc() else ColorUtils.setAlphaComponent(cAcc(), 24))
                t.background = g
                t.setOnClickListener { filterIdx = i; load() }
                val p = LinearLayout.LayoutParams(wrapP, wrapP)
                p.marginEnd = dp(8)
                chips.addView(t, p)
            }
            hs.addView(chips)
            add(col, hs, bottom = 12)

            val head = hrow()
            head.addView(tv("${logs.size} entries", 13f, cSub(), true), LinearLayout.LayoutParams(0, wrapP, 1f))
            val share = pill("Share", cAcc())
            share.setOnClickListener { shareLogs() }
            val clear = pill("Clear", ThemeManager.danger())
            clear.setOnClickListener {
                NgDialog.confirm(this@LogsActivity, "Clear logs", "Delete all saved logs?", "Clear", true) {
                    lifecycleScope.launch {
                        try { dao.clearAllLogs() } catch (e: Exception) { }
                        load()
                    }
                }
            }
            val sp = LinearLayout.LayoutParams(wrapP, wrapP)
            sp.marginEnd = dp(8)
            head.addView(share, sp)
            head.addView(clear)
            add(col, head, bottom = 10)

            if (logs.isEmpty()) {
                val c = card(18)
                c.gravity = Gravity.CENTER_HORIZONTAL
                c.addView(tv("No logs here yet.", 14f, cSub()))
                add(col, c, bottom = 0)
            }
            val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            for (l in logs) {
                val c = card(12)
                val row = hrow()
                val dot = android.view.View(this@LogsActivity)
                val dg = GradientDrawable()
                dg.shape = GradientDrawable.OVAL
                dg.setColor(if (l.success) colorFor(l.type) else ThemeManager.danger())
                dot.background = dg
                row.addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)))
                val tb = vcol()
                tb.setPadding(dp(12), 0, 0, 0)
                tb.addView(tv(l.message, 14f, cText(), true))
                tb.addView(tv(l.type + " · " + df.format(Date(l.timestampMillis)) + (if (l.success) "" else " · failed"), 11f, cSub()))
                row.addView(tb, LinearLayout.LayoutParams(0, wrapP, 1f))
                c.addView(row)
                add(col, c, bottom = 8)
            }
            pull.done()
        }
    }

    private fun shareLogs() {
        val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val text = shown.joinToString("\n") {
            df.format(Date(it.timestampMillis)) + " | " + it.type + " | " + (if (it.success) "OK" else "FAIL") + " | " + it.message
        }
        if (text.isBlank()) {
            toast("Nothing to share")
            return
        }
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, text.take(60000))
        startActivity(Intent.createChooser(i, "Share logs"))
    }
}
