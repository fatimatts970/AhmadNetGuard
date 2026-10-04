package com.ahmad.netguard.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.min

/**
 * Pull-down-to-refresh bina kisi extra library ke.
 * Neeche ek ScrollView hai (`scroll`), jisme content add karo.
 * Refresh khatam hone par `done()` call karo.
 */
class PullRefreshLayout(context: Context) : FrameLayout(context) {

    val scroll = ScrollView(context)
    var onRefresh: (() -> Unit)? = null

    private val spinner = IconView(context)
    private val d = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val trigger = 72f * d
    private val maxPull = 130f * d
    private var startY = 0f
    private var startX = 0f
    private var pulling = false
    private var refreshing = false
    private var spin: ObjectAnimator? = null

    init {
        spinner.icon = NgIcon.REFRESH
        spinner.tint = ThemeManager.accent()
        spinner.alpha = 0f
        val size = (30f * d).toInt()
        val lp = LayoutParams(size, size, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.topMargin = (14f * d).toInt()
        addView(spinner, lp)
        scroll.clipToPadding = false
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (refreshing || onRefresh == null) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.y
                startX = ev.x
                pulling = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - startY
                val dx = abs(ev.x - startX)
                if (!pulling && dy > slop && dy > dx && !scroll.canScrollVertically(-1)) {
                    pulling = true
                    startY = ev.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pulling = false
        }
        return pulling
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!pulling) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dy = (ev.y - startY).coerceAtLeast(0f)
                setOffset(min(dy * 0.5f, maxPull))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pulling = false
                if (scroll.translationY >= trigger) startRefresh() else animateTo(0f)
            }
        }
        return true
    }

    private fun setOffset(off: Float) {
        scroll.translationY = off
        spinner.alpha = min(1f, off / trigger)
        spinner.rotation = off * 3f
    }

    private fun animateTo(y: Float) {
        scroll.animate().translationY(y).setDuration(200).start()
    }

    private fun startRefresh() {
        refreshing = true
        animateTo(56f * d)
        spinner.alpha = 1f
        spin?.cancel()
        spin = ObjectAnimator.ofFloat(spinner, View.ROTATION, 0f, 360f).apply {
            duration = 800
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        onRefresh?.invoke()
        postDelayed({ done() }, 8000)
    }

    /** Refresh mukammal — spinner hata do. */
    fun done() {
        if (!refreshing) return
        refreshing = false
        spin?.cancel()
        spinner.animate().alpha(0f).setDuration(150).start()
        animateTo(0f)
    }

    fun retint() {
        spinner.tint = ThemeManager.accent()
    }
}
