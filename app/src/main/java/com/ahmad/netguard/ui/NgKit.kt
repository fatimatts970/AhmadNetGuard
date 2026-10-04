package com.ahmad.netguard.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat

/** Apne banaye hue vector icons (emoji nahi). Sab 24x24 grid par draw hote hain. */
enum class NgIcon {
    ROUTER, GLOBE, DEVICES, SUN, NETWORK, USAGE, HEADSET, WIFI, PEOPLE, FAMILY, SHIELD, BLOCK,
    GRID, GEAR, PALETTE, BELL, MOON, LOGOUT, REFRESH, USER, LOCK, EYE, EYE_OFF, FINGERPRINT,
    SEARCH, RADAR, CPU, ARROW_IN, CHEVRON, CHECK, EDIT, POWER, BACK, TRASH
}

class IconView(context: Context) : View(context) {

    var icon: NgIcon = NgIcon.ROUTER
        set(v) { field = v; invalidate() }
    var tint: Int = Color.BLACK
        set(v) { field = v; invalidate() }
    var filled: Boolean = false
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = minOf(width, height) / 24f
        if (s <= 0f) return
        canvas.save()
        canvas.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
        canvas.scale(s, s)
        p.color = tint
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.9f
        drawIcon(canvas)
        canvas.restore()
    }

    private fun stroke() { p.style = Paint.Style.STROKE }
    private fun fillOrStroke() { p.style = if (filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE }

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float) {
        c.drawRoundRect(RectF(l, t, r, b), rad, rad, p)
    }

    private fun arc(c: Canvas, cx: Float, cy: Float, r: Float, start: Float, sweep: Float) {
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), start, sweep, false, p)
    }

    private fun poly(c: Canvas, vararg pts: Float) {
        val path = Path()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        c.drawPath(path, p)
    }

    private fun drawIcon(c: Canvas) {
        when (icon) {
            NgIcon.ROUTER -> {
                rr(c, 3f, 14f, 21f, 21f, 2.5f)
                c.drawLine(8f, 14f, 6f, 6f, p)
                c.drawLine(16f, 14f, 18f, 6f, p)
                arc(c, 12f, 11f, 3f, 210f, 120f)
                p.style = Paint.Style.FILL
                c.drawCircle(7f, 17.5f, 1.1f, p)
                c.drawCircle(11f, 17.5f, 1.1f, p)
            }
            NgIcon.GLOBE -> {
                c.drawCircle(12f, 12f, 9f, p)
                c.drawOval(RectF(8f, 3f, 16f, 21f), p)
                c.drawLine(3f, 12f, 21f, 12f, p)
            }
            NgIcon.DEVICES -> {
                rr(c, 2.5f, 5f, 16.5f, 16f, 2f)
                c.drawLine(7f, 19.5f, 12f, 19.5f, p)
                rr(c, 15f, 10f, 21.5f, 19.5f, 1.8f)
            }
            NgIcon.SUN -> {
                c.drawCircle(12f, 12f, 4.2f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val x1 = (12 + 7.2 * Math.cos(a)).toFloat()
                    val y1 = (12 + 7.2 * Math.sin(a)).toFloat()
                    val x2 = (12 + 10 * Math.cos(a)).toFloat()
                    val y2 = (12 + 10 * Math.sin(a)).toFloat()
                    c.drawLine(x1, y1, x2, y2, p)
                }
            }
            NgIcon.NETWORK -> {
                rr(c, 9f, 3f, 15f, 9f, 1.4f)
                rr(c, 2.5f, 15f, 8.5f, 21f, 1.4f)
                rr(c, 15.5f, 15f, 21.5f, 21f, 1.4f)
                c.drawLine(12f, 9f, 12f, 12.5f, p)
                c.drawLine(5.5f, 15f, 5.5f, 12.5f, p)
                c.drawLine(18.5f, 15f, 18.5f, 12.5f, p)
                c.drawLine(5.5f, 12.5f, 18.5f, 12.5f, p)
            }
            NgIcon.USAGE -> {
                p.strokeWidth = 2.6f
                arc(c, 12f, 12f, 8.5f, -80f, 290f)
            }
            NgIcon.HEADSET -> {
                arc(c, 12f, 12f, 8f, 180f, 180f)
                rr(c, 3f, 12f, 7f, 18f, 1.6f)
                rr(c, 17f, 12f, 21f, 18f, 1.6f)
                poly(c, 19f, 18f, 19f, 20.5f, 14f, 21f)
            }
            NgIcon.WIFI -> {
                arc(c, 12f, 19f, 5f, -135f, 90f)
                arc(c, 12f, 19f, 9.5f, -135f, 90f)
                arc(c, 12f, 19f, 14f, -135f, 90f)
                p.style = Paint.Style.FILL
                c.drawCircle(12f, 19f, 1.5f, p)
            }
            NgIcon.PEOPLE -> {
                c.drawCircle(9f, 8f, 3.2f, p)
                arc(c, 9f, 20f, 6f, 180f, 180f)
                c.drawCircle(17f, 9f, 2.5f, p)
                arc(c, 17.5f, 20f, 4.5f, 200f, 140f)
            }
            NgIcon.FAMILY -> {
                c.drawCircle(7f, 7f, 2.5f, p)
                c.drawCircle(17f, 7f, 2.5f, p)
                c.drawCircle(12f, 13.5f, 2f, p)
                arc(c, 7f, 19f, 5f, 180f, 180f)
                arc(c, 17f, 19f, 5f, 180f, 180f)
                c.drawLine(9.5f, 21f, 14.5f, 21f, p)
            }
            NgIcon.SHIELD -> {
                fillOrStroke()
                val path = Path()
                path.moveTo(12f, 2.5f)
                path.lineTo(20f, 6f)
                path.lineTo(20f, 12f)
                path.quadTo(20f, 18f, 12f, 21.5f)
                path.quadTo(4f, 18f, 4f, 12f)
                path.lineTo(4f, 6f)
                path.close()
                c.drawPath(path, p)
                stroke()
                p.color = if (filled) Color.WHITE else tint
                poly(c, 8.5f, 12f, 11f, 14.5f, 15.5f, 9.5f)
            }
            NgIcon.BLOCK -> {
                c.drawCircle(12f, 12f, 9f, p)
                c.drawLine(5.6f, 5.6f, 18.4f, 18.4f, p)
            }
            NgIcon.GRID -> {
                fillOrStroke()
                rr(c, 3f, 3f, 10.5f, 10.5f, 2f)
                rr(c, 13.5f, 3f, 21f, 10.5f, 2f)
                rr(c, 3f, 13.5f, 10.5f, 21f, 2f)
                rr(c, 13.5f, 13.5f, 21f, 21f, 2f)
            }
            NgIcon.GEAR -> {
                c.drawCircle(12f, 12f, 3f, p)
                c.drawCircle(12f, 12f, 7.2f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    c.drawLine(
                        (12 + 7.2 * Math.cos(a)).toFloat(), (12 + 7.2 * Math.sin(a)).toFloat(),
                        (12 + 9.8 * Math.cos(a)).toFloat(), (12 + 9.8 * Math.sin(a)).toFloat(), p
                    )
                }
            }
            NgIcon.PALETTE -> {
                c.drawCircle(12f, 12f, 9f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(8f, 10f, 1.4f, p)
                c.drawCircle(12f, 7.5f, 1.4f, p)
                c.drawCircle(16f, 10f, 1.4f, p)
                c.drawCircle(15.5f, 15f, 1.4f, p)
            }
            NgIcon.BELL -> {
                fillOrStroke()
                val path = Path()
                path.moveTo(6.5f, 16f)
                path.lineTo(5f, 17.5f)
                path.lineTo(19f, 17.5f)
                path.lineTo(17.5f, 16f)
                path.lineTo(17.5f, 10f)
                path.arcTo(RectF(6.5f, 4.5f, 17.5f, 15.5f), 0f, -180f)
                path.close()
                c.drawPath(path, p)
                stroke()
                arc(c, 12f, 19.5f, 2f, 0f, 180f)
            }
            NgIcon.MOON -> {
                fillOrStroke()
                val a = Path()
                a.addCircle(12f, 12f, 8.5f, Path.Direction.CW)
                val b = Path()
                b.addCircle(16.5f, 8.5f, 7f, Path.Direction.CW)
                a.op(b, Path.Op.DIFFERENCE)
                c.drawPath(a, p)
            }
            NgIcon.LOGOUT -> {
                poly(c, 10f, 4f, 5f, 4f, 5f, 20f, 10f, 20f)
                c.drawLine(10f, 12f, 20f, 12f, p)
                poly(c, 16f, 8f, 20f, 12f, 16f, 16f)
            }
            NgIcon.REFRESH -> {
                arc(c, 12f, 12f, 8f, 30f, 290f)
                poly(c, 19f, 3.5f, 19f, 8.5f, 14f, 8.5f)
            }
            NgIcon.USER -> {
                c.drawCircle(12f, 8f, 4f, p)
                arc(c, 12f, 22f, 8f, 180f, 180f)
            }
            NgIcon.LOCK -> {
                rr(c, 5f, 11f, 19f, 21f, 2.5f)
                val path = Path()
                path.moveTo(8f, 11f)
                path.lineTo(8f, 8f)
                path.arcTo(RectF(8f, 4f, 16f, 12f), 180f, 180f)
                path.lineTo(16f, 11f)
                c.drawPath(path, p)
                p.style = Paint.Style.FILL
                c.drawCircle(12f, 16f, 1.3f, p)
            }
            NgIcon.EYE, NgIcon.EYE_OFF -> {
                val path = Path()
                path.moveTo(2f, 12f)
                path.quadTo(12f, 3f, 22f, 12f)
                path.quadTo(12f, 21f, 2f, 12f)
                c.drawPath(path, p)
                c.drawCircle(12f, 12f, 3f, p)
                if (icon == NgIcon.EYE_OFF) c.drawLine(4f, 4f, 20f, 20f, p)
            }
            NgIcon.FINGERPRINT -> {
                arc(c, 12f, 12f, 3f, 200f, 300f)
                arc(c, 12f, 12f, 6.5f, 150f, 280f)
                arc(c, 12f, 12f, 10f, 160f, 260f)
                c.drawLine(12f, 12f, 12f, 17f, p)
            }
            NgIcon.SEARCH -> {
                c.drawCircle(10.5f, 10.5f, 6.5f, p)
                c.drawLine(15.5f, 15.5f, 21f, 21f, p)
            }
            NgIcon.RADAR -> {
                c.drawCircle(12f, 12f, 9f, p)
                c.drawCircle(12f, 12f, 4.5f, p)
                c.drawLine(12f, 12f, 19f, 5f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(12f, 12f, 1.3f, p)
            }
            NgIcon.CPU -> {
                rr(c, 6f, 6f, 18f, 18f, 2f)
                rr(c, 9.5f, 9.5f, 14.5f, 14.5f, 1f)
                for (v in floatArrayOf(9.5f, 14.5f)) {
                    c.drawLine(v, 3f, v, 6f, p)
                    c.drawLine(v, 18f, v, 21f, p)
                    c.drawLine(3f, v, 6f, v, p)
                    c.drawLine(18f, v, 21f, v, p)
                }
            }
            NgIcon.ARROW_IN -> {
                c.drawLine(3.5f, 12f, 14f, 12f, p)
                poly(c, 10f, 8f, 14f, 12f, 10f, 16f)
                poly(c, 14f, 4f, 20f, 4f, 20f, 20f, 14f, 20f)
            }
            NgIcon.CHEVRON -> poly(c, 9f, 6f, 15f, 12f, 9f, 18f)
            NgIcon.CHECK -> poly(c, 5f, 12.5f, 10f, 17.5f, 19f, 7f)
            NgIcon.EDIT -> poly(c, 4f, 20f, 8f, 19f, 19f, 8f, 16f, 5f, 5f, 16f, 4f, 20f)
            NgIcon.BACK -> {
                c.drawLine(19f, 12f, 5f, 12f, p)
                poly(c, 11f, 6f, 5f, 12f, 11f, 18f)
            }
            NgIcon.TRASH -> {
                c.drawLine(4f, 7f, 20f, 7f, p)
                rr(c, 6f, 7f, 18f, 21f, 2f)
                poly(c, 9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f)
                c.drawLine(10f, 11f, 10f, 17f, p)
                c.drawLine(14f, 11f, 14f, 17f, p)
            }
            NgIcon.POWER -> {
                arc(c, 12f, 12.5f, 8f, -60f, 300f)
                c.drawLine(12f, 3f, 12f, 12f, p)
            }
        }
    }
}

/** Hero card ke liye router ki apni drawing (white body, antennas, LED dots). */
class RouterArt(context: Context) : View(context) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val bw = minOf(w * 0.52f, h * 1.9f)
        val bh = bw * 0.36f
        val top = h * 0.5f

        // shadow
        p.style = Paint.Style.FILL
        p.color = Color.argb(45, 0, 0, 0)
        canvas.drawOval(RectF(cx - bw * 0.5f, top + bh * 0.9f, cx + bw * 0.5f, top + bh * 1.25f), p)

        // antennas
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = bw * 0.055f
        p.color = Color.WHITE
        canvas.drawLine(cx - bw * 0.3f, top + 2f, cx - bw * 0.38f, top - bh * 0.95f, p)
        canvas.drawLine(cx + bw * 0.3f, top + 2f, cx + bw * 0.38f, top - bh * 0.95f, p)

        // wifi arcs
        p.strokeWidth = bw * 0.028f
        p.color = Color.argb(210, 255, 255, 255)
        for (i in 1..3) {
            val r = bw * 0.075f * i
            canvas.drawArc(RectF(cx - r, top - bh * 0.15f - r, cx + r, top - bh * 0.15f + r), -145f, 110f, false, p)
        }

        // body
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        canvas.drawRoundRect(RectF(cx - bw / 2f, top, cx + bw / 2f, top + bh), bh / 2.2f, bh / 2.2f, p)

        // LEDs
        val ly = top + bh / 2f
        val lr = bh * 0.075f
        p.color = Color.parseColor("#2DD4A0")
        canvas.drawCircle(cx - bw * 0.33f, ly, lr, p)
        p.color = Color.parseColor("#3B82F6")
        canvas.drawCircle(cx - bw * 0.25f, ly, lr, p)
        p.color = Color.parseColor("#FBBF24")
        canvas.drawCircle(cx - bw * 0.17f, ly, lr, p)

        // slot
        p.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(RectF(cx + bw * 0.22f, ly - bh * 0.1f, cx + bw * 0.38f, ly + bh * 0.1f), bh * 0.08f, bh * 0.08f, p)
    }
}

object NgKit {

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
    fun dpf(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    fun icon(ctx: Context, icon: NgIcon, color: Int, filled: Boolean = false): IconView {
        val v = IconView(ctx)
        v.icon = icon
        v.tint = color
        v.filled = filled
        return v
    }

    fun topColor(): Int {
        val acc = ThemeManager.accent()
        return if (ThemeManager.dark) Color.parseColor("#0B1210")
        else ColorUtils.blendARGB(Color.parseColor("#F6FBF9"), acc, 0.03f)
    }

    fun bottomColor(): Int {
        val acc = ThemeManager.accent()
        return if (ThemeManager.dark) ColorUtils.blendARGB(Color.parseColor("#131F1A"), acc, 0.10f)
        else ColorUtils.blendARGB(Color.parseColor("#D9EFE7"), acc, 0.14f)
    }

    /** Splash, Login aur Home — teeno ka background yehi, taake theme sab jagah same lage. */
    fun screenBg(): Drawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(topColor(), bottomColor()))

    fun cardBg(ctx: Context): Drawable {
        val surface = ThemeManager.surface()
        val acc = ThemeManager.accent()
        val g = GradientDrawable()
        g.cornerRadius = dpf(ctx, ThemeManager.radiusDp())
        when (ThemeManager.style) {
            1 -> {
                g.orientation = GradientDrawable.Orientation.TL_BR
                g.colors = intArrayOf(ColorUtils.blendARGB(surface, acc, 0.22f), surface)
            }
            2 -> {
                g.setColor(ColorUtils.setAlphaComponent(surface, 200))
                g.setStroke(dp(ctx, 1), ColorUtils.setAlphaComponent(Color.WHITE, 140))
            }
            3 -> {
                g.setColor(surface)
                g.setStroke(dp(ctx, 2), ColorUtils.setAlphaComponent(acc, 150))
            }
            else -> {
                g.setColor(surface)
                g.setStroke(dp(ctx, 1), ThemeManager.border())
            }
        }
        return g
    }

    /** Hero / login card: accent ka glossy gradient. */
    fun heroBg(ctx: Context): Drawable {
        val acc = ThemeManager.accent()
        val g = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(ColorUtils.blendARGB(acc, Color.BLACK, 0.08f), ColorUtils.blendARGB(acc, Color.WHITE, 0.30f))
        )
        g.cornerRadius = dpf(ctx, maxOf(ThemeManager.radiusDp(), 8f) + 4f)
        return g
    }

    fun chrome(activity: Activity) {
        activity.window.statusBarColor = topColor()
        activity.window.navigationBarColor = bottomColor()
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = !ThemeManager.dark
    }

    fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(
            if (ThemeManager.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
