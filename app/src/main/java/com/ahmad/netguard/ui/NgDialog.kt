package com.ahmad.netguard.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils

/** App ke theme wale dialogs (rounded card, accent buttons). */
object NgDialog {

    private fun dp(a: Activity, v: Int) = NgKit.dp(a, v)

    private fun label(a: Activity, text: String, sp: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(a)
        t.text = text
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun button(a: Activity, text: String, color: Int, filled: Boolean): TextView {
        val t = label(a, text, 14f, if (filled) Color.WHITE else color, true)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(a, 20), dp(a, 11), dp(a, 20), dp(a, 11))
        val g = GradientDrawable()
        g.cornerRadius = NgKit.dpf(a, 40f)
        g.setColor(if (filled) color else ColorUtils.setAlphaComponent(color, 36))
        t.background = g
        return t
    }

    private fun shell(a: Activity, title: String, build: (LinearLayout, Dialog) -> Unit): Dialog {
        val dlg = Dialog(a)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val card = LinearLayout(a)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(a, 22), dp(a, 22), dp(a, 22), dp(a, 18))
        val bg = GradientDrawable()
        bg.cornerRadius = NgKit.dpf(a, 28f)
        bg.setColor(ThemeManager.surface())
        bg.setStroke(dp(a, 1), ColorUtils.setAlphaComponent(ThemeManager.accent(), 90))
        card.background = bg
        card.addView(label(a, title, 20f, ThemeManager.text(), true))
        build(card, dlg)
        dlg.setContentView(card)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val w = (a.resources.displayMetrics.widthPixels * 0.90f).toInt()
        dlg.window?.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dlg
    }

    private fun buttons(a: Activity, card: LinearLayout, left: TextView, right: TextView) {
        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        row.addView(left)
        val sp = android.view.View(a)
        row.addView(sp, LinearLayout.LayoutParams(dp(a, 10), 1))
        row.addView(right)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(a, 20)
        card.addView(row, lp)
    }

    fun input(a: Activity, title: String, subtitle: String?, initial: String, hint: String, onSave: (String) -> Unit) {
        val dlg = shell(a, title) { card, d ->
            if (subtitle != null) {
                val st = label(a, subtitle, 13f, ThemeManager.sub(), false)
                st.setPadding(0, dp(a, 4), 0, 0)
                card.addView(st)
            }
            val et = EditText(a)
            et.setText(initial)
            et.hint = hint
            et.setSingleLine(true)
            et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            et.textSize = 16f
            et.setTextColor(ThemeManager.text())
            et.setHintTextColor(ThemeManager.sub())
            et.setPadding(dp(a, 16), dp(a, 13), dp(a, 16), dp(a, 13))
            val fb = GradientDrawable()
            fb.cornerRadius = NgKit.dpf(a, 16f)
            fb.setColor(ColorUtils.setAlphaComponent(ThemeManager.accent(), 22))
            fb.setStroke(dp(a, 2), ThemeManager.accent())
            et.background = fb
            et.setSelection(et.text.length)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(a, 16)
            card.addView(et, lp)

            val cancel = button(a, "Cancel", ThemeManager.sub(), false)
            val save = button(a, "Save", ThemeManager.accent(), true)
            cancel.setOnClickListener { d.dismiss() }
            save.setOnClickListener {
                val v = et.text.toString().trim()
                if (v.isNotEmpty()) {
                    onSave(v)
                    d.dismiss()
                }
            }
            buttons(a, card, cancel, save)
            d.setOnShowListener {
                et.requestFocus()
                d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            }
        }
        dlg.show()
    }

    fun confirm(a: Activity, title: String, message: String, positive: String, danger: Boolean, onOk: () -> Unit) {
        val dlg = shell(a, title) { card, d ->
            val m = label(a, message, 14f, ThemeManager.sub(), false)
            m.setPadding(0, dp(a, 10), 0, 0)
            card.addView(m)
            val cancel = button(a, "Cancel", ThemeManager.sub(), false)
            val ok = button(a, positive, if (danger) ThemeManager.danger() else ThemeManager.accent(), true)
            cancel.setOnClickListener { d.dismiss() }
            ok.setOnClickListener {
                d.dismiss()
                onOk()
            }
            buttons(a, card, cancel, ok)
        }
        dlg.show()
    }
}
