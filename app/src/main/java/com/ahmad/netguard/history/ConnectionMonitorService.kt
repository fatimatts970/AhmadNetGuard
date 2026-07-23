package com.ahmad.netguard.history

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.ahmad.netguard.model.Device
import com.ahmad.netguard.network.HuaweiRouterAdapter
import kotlinx.coroutines.*

class ConnectionMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val routerAdapter = HuaweiRouterAdapter()
    private val knownDevices = mutableMapOf<String, Device>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val currentDevices = routerAdapter.getConnectedDevices()
                    val db = AppDatabase.getInstance(applicationContext)

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
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(30000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
