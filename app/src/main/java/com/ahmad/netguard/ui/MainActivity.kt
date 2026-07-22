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
            goToLogin()
            return
        }

        startMonitorService()
        setupDeviceList()
        unlockAndConnect()
    }

    override fun onResume() {
        super.onResume()
        if (credentialStore.hasSavedCredentials()) {
            loadDevices()
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
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

    private fun unlockAndConnect() {
        if (BiometricHelper.canUseBiometrics(this)) {
            BiometricHelper.prompt(
                activity = this,
                onSuccess = { connectWithSavedCredentials() },
                onFailure = {
                    Toast.makeText(this, "Fingerprint not verified", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            connectWithSavedCredentials()
        }
    }

    private fun connectWithSavedCredentials() {
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
                goToLogin()
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
