package com.ahmad.netguard.ui

import com.ahmad.netguard.history.ConnectionEvent

/** Connect/disconnect events se online sessions banata hai (sab real, tracked data). */
object SessionCalc {

    class Session(val start: Long, val end: Long, val ongoing: Boolean) {
        val duration: Long get() = (end - start).coerceAtLeast(0L)
    }

    /** eventsAsc: purani se nayi. */
    fun build(eventsAsc: List<ConnectionEvent>, isOnlineNow: Boolean, now: Long): List<Session> {
        val out = ArrayList<Session>()
        var start: Long? = null
        for (e in eventsAsc) {
            if (e.eventType == "connected") {
                if (start == null) start = e.timestampMillis
            } else if (e.eventType == "disconnected") {
                val s = start
                if (s != null) {
                    out.add(Session(s, e.timestampMillis, false))
                    start = null
                }
            }
        }
        val open = start
        if (open != null && isOnlineNow) out.add(Session(open, now, true))
        return out
    }

    fun overlap(sessions: List<Session>, from: Long, to: Long): Long {
        var total = 0L
        for (s in sessions) {
            val a = maxOf(s.start, from)
            val b = minOf(s.end, to)
            if (b > a) total += b - a
        }
        return total
    }

    fun countIn(sessions: List<Session>, from: Long, to: Long): Int =
        sessions.count { it.end > from && it.start < to }

    fun longestIn(sessions: List<Session>, from: Long, to: Long): Long {
        var best = 0L
        for (s in sessions) {
            val a = maxOf(s.start, from)
            val b = minOf(s.end, to)
            if (b - a > best) best = b - a
        }
        return best
    }
}
