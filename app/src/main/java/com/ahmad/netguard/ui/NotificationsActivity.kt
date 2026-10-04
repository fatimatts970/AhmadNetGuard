package com.ahmad.netguard.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.history.AppDatabase
import com.ahmad.netguard.history.AppLog
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Recent alerts: naye devices, logins, block/unblock. */
class NotificationsActivity : NgScreen() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("Notifications") { load() }
        load()
    }

    private fun iconFor(type: String): Pair<NgIcon, Int> = when (type) {
        "CONNECTION" -> NgIcon.DEVICES to cAcc()
        "LOGIN" -> NgIcon.LOCK to Color.parseColor("#6D4FC2")
        "BLOCK" -> NgIcon.BLOCK to ThemeManager.danger()
        "UNBLOCK" -> NgIcon.CHECK to Color.parseColor("#16A34A")
        "SECURITY" -> NgIcon.SHIELD to Color.parseColor("#D97706")
        else -> NgIcon.BELL to cSub()
    }

    private fun titleFor(type: String): String = when (type) {
        "CONNECTION" -> "Device activity"
        "LOGIN" -> "Login"
        "BLOCK" -> "Device blocked"
        "UNBLOCK" -> "Device unblocked"
        "SECURITY" -> "Security"
        else -> "Notice"
    }

    private fun load() {
        col.removeAllViews()
        lifecycleScope.launch {
            val logs: List<AppLog> = try {
                AppDatabase.getInstance(this@NotificationsActivity).appLogDao().getAllLogs()
            } catch (e: Exception) {
                emptyList()
            }
            col.removeAllViews()

            if (logs.isEmpty()) {
                val c = card(22)
                c.gravity = android.view.Gravity.CENTER_HORIZONTAL
                c.addView(ic(NgIcon.BELL, cSub(), 40))
                val t = tv("No notifications yet", 16f, cText(), true)
                t.setPadding(0, dp(10), 0, 0)
                c.addView(t)
                c.addView(tv("New device alerts and activity will show up here.", 13f, cSub()))
                add(col, c, bottom = 0)
                pull.done()
                return@launch
            }

            val head = hrow()
            head.addView(tv("${logs.size} recent", 13f, cSub(), true), LinearLayout.LayoutParams(0, wrapP, 1f))
            val clear = pill("Clear all", ThemeManager.danger())
            clear.setOnClickListener {
                NgDialog.confirm(this@NotificationsActivity, "Clear notifications", "Remove all saved activity?", "Clear", true) {
                    lifecycleScope.launch {
                        try {
                            AppDatabase.getInstance(this@NotificationsActivity).appLogDao().clearAllLogs()
                        } catch (e: Exception) {
                        }
                        load()
                    }
                }
            }
            head.addView(clear)
            add(col, head, bottom = 10)

            val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            for (l in logs.take(120)) {
                val (icon, color) = iconFor(l.type)
                val c = card(14)
                val row = hrow()
                val badge = FrameLayout(this@NotificationsActivity)
                val bg = android.graphics.drawable.GradientDrawable()
                bg.shape = android.graphics.drawable.GradientDrawable.OVAL
                bg.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 40))
                badge.background = bg
                val iv = ic(icon, color, 20)
                val ivp = FrameLayout.LayoutParams(dp(20), dp(20))
                ivp.gravity = android.view.Gravity.CENTER
                badge.addView(iv, ivp)
                row.addView(badge, LinearLayout.LayoutParams(dp(40), dp(40)))
                val tb = vcol()
                tb.setPadding(dp(12), 0, 0, 0)
                tb.addView(tv(titleFor(l.type), 14f, cText(), true))
                tb.addView(tv(l.message, 12f, cSub()))
                val tm = tv(df.format(Date(l.timestampMillis)), 11f, cSub())
                tm.setPadding(0, dp(3), 0, 0)
                tb.addView(tm)
                row.addView(tb, LinearLayout.LayoutParams(0, wrapP, 1f))
                c.addView(row)
                add(col, c, bottom = 10)
            }
            pull.done()
        }
    }
}
