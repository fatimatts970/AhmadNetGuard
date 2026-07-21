package com.ahmad.netguard.history

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ahmad.netguard.network.RouterAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConnectionMonitorService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val router = RouterAdapterFactory.create(RouterAdapterFactory.Brand.HUAWEI)

    companion object {
        const val CHANNEL_ID = "netguard_monitor"
        const val NOTIFICATION_ID = 1
        const val POLL_INTERVAL_MS = 30_000L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startPollingLoop()
    }

    private fun startPollingLoop() {
        scope.launch {
            val db = AppDatabase.getInstance(applicationContext).connectionEventDao()
            router.login("192.168.100.1", "admin", "admin")
            while (true) {
                try {
                    val devices = router.getDevices()
                    for (device in devices) {
                        val last = db.getLastEventForDevice(device.macAddress)
                        val lastWasOnline = last?.eventType == "ONLINE"
                        val stateChanged = last == null || lastWasOnline != device.isOnline
                        if (stateChanged) {
                            db.insert(
                                ConnectionEvent(
                                    mac = device.macAddress,
                                    deviceNameAtTime = device.displayName(),
                                    eventType = if (device.isOnline) "ONLINE" else "OFFLINE",
                                    timestampMillis = System.currentTimeMillis(),
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "WiFi Monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AHMAD NetGuard")
            .setContentText("Watching your WiFi devices in the background")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }
}
