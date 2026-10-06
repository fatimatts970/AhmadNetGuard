package com.ahmad.netguard.ui

import android.content.Context
import com.ahmad.netguard.history.AppDatabase
import com.ahmad.netguard.history.AppLog
import com.ahmad.netguard.history.ConnectionEvent
import com.ahmad.netguard.model.Device
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App khula ho tab hi devices ka online/offline record karta hai (koi background service nahi).
 * App band hone ke dauran ka time kabhi online nahi gina jata.
 */
object ConnectionTracker {

    private val mutex = Mutex()
    private val known = HashMap<String, Boolean>()
    private var firstPollDone = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("ng_tracker", Context.MODE_PRIVATE)

    suspend fun record(ctx: Context, devices: List<Device>, onNew: (Device) -> Unit = {}) {
        if (devices.isEmpty()) return
        mutex.withLock {
            val db = AppDatabase.getInstance(ctx.applicationContext)
            val events = db.connectionEventDao()
            val logs = db.appLogDao()
            val now = System.currentTimeMillis()
            val lastSeen = prefs(ctx).getLong("last_seen", 0L)

            for (d in devices) {
                val mac = d.macAddress
                val old = known[mac]
                if (old == null) {
                    // is app-run mein pehli baar dekha: DB ki last state se milao
                    val last = try { events.getLastEventForDevice(mac) } catch (e: Exception) { null }
                    if (last != null && last.eventType == "connected") {
                        // app band tha: purana session last_seen par band karo, phir naya (agar online) shuru karo
                        val closeAt = maxOf(lastSeen, last.timestampMillis)
                        events.insert(ConnectionEvent(mac = mac, deviceNameAtTime = d.displayName, eventType = "disconnected", timestampMillis = closeAt))
                    }
                    if (d.isOnline) {
                        events.insert(ConnectionEvent(mac = mac, deviceNameAtTime = d.displayName, eventType = "connected", timestampMillis = now))
                        if (last == null && firstPollDone) {
                            logs.insert(AppLog(type = "CONNECTION", message = "New device joined: ${DeviceNamer.pretty(d.displayName)} (${d.ipAddress})", success = true, timestampMillis = now))
                            onNew(d)
                        } else {
                            logs.insert(AppLog(type = "CONNECTION", message = "${DeviceNamer.pretty(d.displayName)} connected ($mac)", success = true, timestampMillis = now))
                        }
                    }
                } else if (old != d.isOnline) {
                    events.insert(
                        ConnectionEvent(
                            mac = mac, deviceNameAtTime = d.displayName,
                            eventType = if (d.isOnline) "connected" else "disconnected", timestampMillis = now
                        )
                    )
                    logs.insert(
                        AppLog(
                            type = "CONNECTION",
                            message = "${DeviceNamer.pretty(d.displayName)} ${if (d.isOnline) "connected" else "disconnected"} ($mac)",
                            success = true,
                            timestampMillis = now
                        )
                    )
                }
                known[mac] = d.isOnline
            }
            firstPollDone = true
            prefs(ctx).edit().putLong("last_seen", now).apply()
        }
    }
}
