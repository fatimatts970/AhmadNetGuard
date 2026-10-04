package com.ahmad.netguard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.model.OpticalInfo
import com.ahmad.netguard.network.RouterAdapterFactory
import kotlinx.coroutines.launch

/** RX/TX power ki history ka simple line chart. */
class PowerChartView(context: Context) : View(context) {

    var rx: List<Float> = emptyList()
    var tx: List<Float> = emptyList()
    var rxColor: Int = Color.GREEN
    var txColor: Int = Color.MAGENTA
    var textColor: Int = Color.GRAY
    var gridColor: Int = Color.LTGRAY

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val all = rx + tx
        val d = resources.displayMetrics.density
        val padL = 36f * d
        val padB = 6f * d
        val padT = 6f * d
        val padR = 8f * d

        if (all.isEmpty()) {
            p.style = Paint.Style.FILL
            p.color = textColor
            p.textSize = 12f * d
            p.textAlign = Paint.Align.CENTER
            canvas.drawText("History jama ho rahi hai…", w / 2f, h / 2f, p)
            return
        }

        var lo = all.minOrNull() ?: -30f
        var hi = all.maxOrNull() ?: 0f
        if (hi - lo < 4f) {
            lo -= 2f
            hi += 2f
        } else {
            lo -= 1f
            hi += 1f
        }
        val cw = w - padL - padR
        val ch = h - padT - padB

        // grid + labels
        p.textSize = 10f * d
        p.textAlign = Paint.Align.RIGHT
        for (i in 0..3) {
            val y = padT + ch * i / 3f
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1f
            p.color = gridColor
            canvas.drawLine(padL, y, w - padR, y, p)
            val v = hi - (hi - lo) * i / 3f
            p.style = Paint.Style.FILL
            p.color = textColor
            canvas.drawText(String.format("%.0f", v), padL - 4f * d, y + 3f * d, p)
        }

        fun drawSeries(series: List<Float>, color: Int) {
            if (series.isEmpty()) return
            val path = Path()
            series.forEachIndexed { i, v ->
                val x = if (series.size == 1) padL + cw / 2f else padL + cw * i / (series.size - 1f)
                val y = padT + ch * (hi - v) / (hi - lo)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.5f * d
            p.strokeCap = Paint.Cap.ROUND
            p.strokeJoin = Paint.Join.ROUND
            p.color = color
            canvas.drawPath(path, p)
            if (series.size == 1) {
                p.style = Paint.Style.FILL
                canvas.drawCircle(padL + cw / 2f, padT + ch * (hi - series[0]) / (hi - lo), 4f * d, p)
            }
        }
        drawSeries(rx, rxColor)
        drawSeries(tx, txColor)
    }
}

class OpticalActivity : NgScreen() {

    private val prefs by lazy { getSharedPreferences("optical_hist", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("Optical Diagnostics") { load() }
        load()
    }

    private fun num(s: String): Float? =
        Regex("-?\\d+(\\.\\d+)?").find(s)?.value?.toFloatOrNull()

    private fun loadHistory(): MutableList<Pair<Float, Float>> {
        val raw = prefs.getString("h", "") ?: ""
        val out = ArrayList<Pair<Float, Float>>()
        for (line in raw.split(";")) {
            val p = line.split(",")
            if (p.size == 2) {
                val a = p[0].toFloatOrNull()
                val b = p[1].toFloatOrNull()
                if (a != null && b != null) out.add(a to b)
            }
        }
        return out
    }

    private fun saveHistory(list: List<Pair<Float, Float>>) {
        val tail = list.takeLast(80)
        prefs.edit().putString("h", tail.joinToString(";") { "${it.first},${it.second}" }).apply()
    }

    private fun load() {
        col.removeAllViews()
        val loading = card(18)
        loading.addView(tv("Optical info load ho rahi hai…", 14f, cSub()))
        add(col, loading)

        lifecycleScope.launch {
            val info: OpticalInfo? = try {
                RouterAdapterFactory.getAdapter().getOpticalInfo()
            } catch (e: Exception) {
                null
            }
            col.removeAllViews()

            if (info == null) {
                val c = card(18)
                c.addView(tv("Optical information nahi mili", 15f, cText(), true))
                c.addView(tv("Router se signal data fetch nahi hua. Refresh karke dekho.", 12f, cSub()))
                add(col, c)
                return@launch
            }

            val rxV = num(info.rxPowerDbm)
            val txV = num(info.txPowerDbm)
            if (rxV != null && txV != null) {
                val h = loadHistory()
                h.add(rxV to txV)
                saveHistory(h)
            }

            // status pill
            val (label, good) = when {
                rxV == null -> "No optical signal detected" to false
                rxV < -27f -> "Weak Optical Signal" to false
                rxV > -8f -> "Signal too strong" to false
                else -> "Connection Stable" to true
            }
            val sc = card(14)
            sc.gravity = Gravity.CENTER
            val col1 = if (good) cAcc() else ThemeManager.danger()
            val row = hrow()
            row.gravity = Gravity.CENTER
            row.addView(ic(if (good) NgIcon.CHECK else NgIcon.BLOCK, col1, 22))
            val lt = tv(label, 15f, col1, true)
            lt.setPadding(dp(10), 0, 0, 0)
            row.addView(lt)
            sc.addView(row)
            add(col, sc)

            // chart
            val hist = loadHistory()
            val chartCard = card(14)
            val head = hrow()
            head.addView(tv("POWER HISTORY (dBm)", 12f, cSub(), true), LinearLayout.LayoutParams(0, wrapP, 1f))
            head.addView(tv("● RX ", 11f, cAcc(), true))
            head.addView(tv("● TX", 11f, Color.parseColor("#7C3AED"), true))
            chartCard.addView(head)
            val chart = PowerChartView(this@OpticalActivity)
            chart.rx = hist.map { it.first }
            chart.tx = hist.map { it.second }
            chart.rxColor = cAcc()
            chart.txColor = Color.parseColor("#7C3AED")
            chart.textColor = cSub()
            chart.gridColor = ThemeManager.border()
            chartCard.addView(chart, LinearLayout.LayoutParams(matchP, dp(170)))
            add(col, chartCard)

            // metrics
            add(col, section("Live values"), bottom = 0)
            val m = card(16)
            m.addView(kvRow("RX Power", info.rxPowerDbm))
            m.addView(divider())
            m.addView(kvRow("TX Power", info.txPowerDbm))
            m.addView(divider())
            m.addView(kvRow("Voltage", info.voltageMv))
            m.addView(divider())
            m.addView(kvRow("Bias Current", info.biasMa))
            m.addView(divider())
            m.addView(kvRow("Temperature", info.temperatureC))
            add(col, m)

            add(col, section("Module"), bottom = 0)
            val mod = card(16)
            mod.addView(kvRow("Vendor", info.vendor))
            mod.addView(divider())
            mod.addView(kvRow("Serial", info.serialNumber))
            mod.addView(divider())
            mod.addView(kvRow("TX wavelength", info.txWaveLengthNm))
            mod.addView(divider())
            mod.addView(kvRow("RX wavelength", info.rxWaveLengthNm))
            add(col, mod, bottom = 0)
        }
    }
}
