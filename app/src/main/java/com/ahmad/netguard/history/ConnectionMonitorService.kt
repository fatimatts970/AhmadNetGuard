package com.ahmad.netguard.history

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.ahmad.netguard.model.Device
import com.ahmad.netguard.network.RouterSession
import kotlinx.coroutines.*

class ConnectionMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val routerAdapter = RouterSession.adapter
    private val knownDevices = mutableMapOf<String, Device>()

    companion object {
        private const val POLL_INTERVAL_MS = 30_000L
        private const val ASSUMED_BYTES_PER_SECOND = 60_000L // ~60 KB/s average
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
                        }
                        knownDevices[device.macAddress] = device

                        if (device.isOnline) {
                            db.usageDao().addBytes(device.macAddress, todayEpoch, bytesThisPoll)
                        }
                    }
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
