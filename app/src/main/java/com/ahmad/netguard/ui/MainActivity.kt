package com.ahmad.netguard.ui

import android.content.Intent
import android.os.Build
import android.app.AlertDialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.EditText
import com.ahmad.netguard.databinding.ActivityMainBinding
import com.ahmad.netguard.history.ConnectionMonitorService
import com.ahmad.netguard.network.DeviceNameStore
import com.ahmad.netguard.network.RouterAdapterFactory
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var deviceAdapter: DeviceListAdapter
    private lateinit var nameStore: DeviceNameStore

    private val router = RouterAdapterFactory.create(RouterAdapterFactory.Brand.HUAWEI)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        nameStore = DeviceNameStore(this)

        startMonitorService()

        deviceAdapter = DeviceListAdapter(
            onBlockUnblockClick = { device ->
                lifecycleScope.launch {
                    if (device.isBlocked) router.unblockDevice(device.macAddress)
                    else router.blockDevice(device.macAddress)
                    loadDevices()
                }
            },
            onDeviceClick = { device ->
                HistoryActivity.start(this, device.macAddress, device.displayName())
            }
        )
        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = deviceAdapter

        lifecycleScope.launch {
            router.login("192.168.100.1", "admin", "admin")
            loadDevices()
        }
    }

    override fun onResume() {
        super.onResume()
        loadDevices()
    }

    private fun startMonitorService() {
        val intent = Intent(this, ConnectionMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun loadDevices() {
        lifecycleScope.launch {
            val devices = router.getDevices().onEach { device ->
                nameStore.getCustomName(device.macAddress)?.let { device.customName = it }
            }
            deviceAdapter.submitList(devices)
            binding.textOnlineCount.text = "${devices.count { it.isOnline }} Online"
            binding.textBlockedCount.text = "${devices.count { it.isBlocked }} Blocked"
        }
    }

    private fun showRenameDialog(mac: String, currentName: String) {
        val input = EditText(this).apply { setText(currentName) }
        AlertDialog.Builder(this)
            .setTitle("Rename device")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                nameStore.setCustomName(mac, input.text.toString())
                loadDevices()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
