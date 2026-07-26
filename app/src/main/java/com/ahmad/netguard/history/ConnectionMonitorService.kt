package com.ahmad.netguard.history

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ahmad.netguard.model.Device
import com.ahmad.netguard.network.RouterSession
import com.ahmad.netguard.ui.MainActivity
import kotlinx.coroutines.*

class ConnectionMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val routerAdapter = RouterSession.adapter
    private val knownDevices = mutableMapOf<String, Device>()
    private var isFirstPoll = true

    companion object {
        private const val POLL_INTERVAL_MS = 30_000L
        private const val ASSUMED_BYTES_PER_SECOND = 60_000L
        private const val NOTIFICATION_CHANNEL_ID = "netguard_new_device"
        private const val SERVICE_NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val persistentNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("AHMAD NetGuard")
            .setContentText("Monitoring your WiFi network")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        startForeground(SERVICE_NOTIFICATION_ID, persistentNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "New Device Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a new device joins your WiFi"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun notifyNewDevice(device: Device) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("New device connected")
            .setContentText("${device.displayName} (${device.ipAddress}) just joined your network")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(device.macAddress.hashCode(), notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val currentDevices = routerAdapter.getDevices()
                    val db = AppDatabase.getInstance(applicationContext)
                    val todayEpoch = System.currentTimeMillis() / 86_400_000L
                    val bytesThisPoll = ASSUMED_BYTES_PER_SECOND * (POLL_INTERVAL_MS / 1000L)

                    for (device in currentDevices) {
                        val oldDevice = knownDevices[device.macAddress]
                        if (oldDevice == null || oldDevice.isOnline != device.isOnline) {
                            val event = ConnectionEvent(
                                mac = device.macAddress,
                                deviceNameAtTime = device.displayName,
                                eventType = if (device.isOnline) "connected" else "disconnected",
                                timestampMillis = System.currentTimeMillis()
                            )
                            db.connectionEventDao().insert(event)

                            if (oldDevice == null && device.isOnline && !isFirstPoll) {
                                notifyNewDevice(device)
                            }
                        }
                        knownDevices[device.macAddress] = device

                        if (device.isOnline) {
                            db.usageDao().addBytes(device.macAddress, todayEpoch, bytesThisPoll)
                        }
                    }
                    isFirstPoll = false
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "New Device Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a new device joins your WiFi"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun notifyNewDevice(device: Device) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("New device connected")
            .setContentText("${device.displayName} (${device.ipAddress}) just joined your network")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(device.macAddress.hashCode(), notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val currentDevices = routerAdapter.getDevices()
                    val db = AppDatabase.getInstance(applicationContext)
                    val todayEpoch = System.currentTimeMillis() / 86_400_000L
                    val bytesThisPoll = ASSUMED_BYTES_PER_SECOND * (POLL_INTERVAL_MS / 1000L)

                    for (device in currentDevices) {
                        val oldDevice = knownDevices[device.macAddress]
                        if (oldDevice == null || oldDevice.isOnline != device.isOnline) {
                            val event = ConnectionEvent(
                                mac = device.macAddress,
                                deviceNameAtTime = device.displayName,
                                eventType = if (device.isOnline) "connected" else "disconnected",
                                timestampMillis = System.currentTimeMillis()
                            )
                            db.connectionEventDao().insert(event)

                            if (oldDevice == null && device.isOnline && !isFirstPoll) {
                                notifyNewDevice(device)
                            }
                        }
                        knownDevices[device.macAddress] = device

                        if (device.isOnline) {
                            db.usageDao().addBytes(device.macAddress, todayEpoch, bytesThisPoll)
                        }
                    }
                    isFirstPoll = false
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
