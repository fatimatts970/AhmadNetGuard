package com.ahmad.netguard.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmad.netguard.databinding.ActivityMainBinding
import com.ahmad.netguard.history.ConnectionMonitorService
import com.ahmad.netguard.network.DeviceNameStore
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var deviceAdapter: DeviceListAdapter
    private lateinit var nameStore: DeviceNameStore
    private lateinit var credentialStore: RouterCredentialStore

    private val router = RouterAdapterFactory.create(RouterAdapterFactory.Brand.HUAWEI)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        nameStore = DeviceNameStore(this)
        credentialStore = RouterCredentialStore(this)

        if (!credentialStore.hasSavedCredentials()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        startMonitorService()
        setupDeviceList()
        setupSwipeRefresh()
        connectAndLoad()
    }

    override fun onResume() {
        super.onResume()
        loadDevices()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadDevices()
        }
    }

    private fun setupDeviceList() {
        deviceAdapter = DeviceListAdapter(
            onBlockUnblockClick = { device ->
                lifecycleScope.launch {
                    val success = if (device.isBlocked) router.unblockDevice(device.macAddress)
                    else router.blockDevice(device.macAddress)
                    if (!success) {
                        Toast.makeText(this@MainActivity, "Action failed — router didn't confirm it", Toast.LENGTH_SHORT).show()
                    }
                    loadDevices()
                }
            },
            onDeviceClick = { device ->
                HistoryActivity.start(this, device.macAddress, device.displayName())
            },
            onDeviceLongClick = { device ->
                showRenameDialog(device.macAddress, device.displayName())
            },
            onRenameClick = { device ->
                showRenameDialog(device.macAddress, device.displayName())
            }
        )
        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = deviceAdapter
    }

    private fun connectAndLoad() {
        lifecycleScope.launch {
            val success = router.login(
                credentialStore.getRouterIp(),
                credentialStore.getUsername(),
                credentialStore.getPassword()
            )
            if (!success) {
                Toast.makeText(
                    this@MainActivity,
                    "Couldn't log in to the router — check your saved details",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
                return@launch
            }
            loadDevices()
        }
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
            binding.swipeRefresh.isRefreshing = false
            if (devices.isEmpty()) {
                Toast.makeText(this@MainActivity, "No devices found — pull to refresh or check connection", Toast.LENGTH_SHORT).show()
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
