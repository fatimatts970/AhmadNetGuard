package com.ahmad.netguard.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
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

    private fun setupDeviceList() {
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
            },
            onDeviceLongClick = { device ->
                showRenameDialog(device.macAddress, device.displayName())
            }
        )
        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = deviceAdapter
    }

    private fun unlockAndConnect() {
        if (!credentialStore.hasSavedCredentials()) {
            showFirstTimeSetupDialog()
            return
        }

        if (BiometricHelper.canUseBiometrics(this)) {
            BiometricHelper.prompt(
                activity = this,
                onSuccess = { connectWithSavedCredentials() },
                onFailure = { }
            )
        } else {
            connectWithSavedCredentials()
        }
    }

    private fun connectWithSavedCredentials() {
        lifecycleScope.launch {
            router.login(
                credentialStore.getRouterIp(),
                credentialStore.getUsername(),
                credentialStore.getPassword()
            )
            loadDevices()
        }
    }

    private fun showFirstTimeSetupDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val ipInput = EditText(this).apply { hint = "Router IP (e.g. 192.168.100.1)" }
        val userInput = EditText(this).apply { hint = "Username" }
        val passInput = EditText(this).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(ipInput)
        layout.addView(userInput)
        layout.addView(passInput)

        AlertDialog.Builder(this)
            .setTitle("Connect to your router")
            .setMessage("Enter these once — after this you'll unlock with your fingerprint.")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Save & Connect") { _, _ ->
                val ip = ipInput.text.toString().ifBlank { "192.168.100.1" }
                val user = userInput.text.toString()
                val pass = passInput.text.toString()
                credentialStore.save(ip, user, pass)
                connectWithSavedCredentials()
            }
            .show()
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
