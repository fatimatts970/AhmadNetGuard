package com.ahmad.netguard.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ahmad.netguard.R
import com.ahmad.netguard.model.Device
import com.ahmad.netguard.network.HuaweiRouterAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var rvDevices: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var tvDeviceCountHeader: TextView

    private val routerAdapter = HuaweiRouterAdapter()
    private val deviceList = mutableListOf<Device>()
    private lateinit var deviceListAdapter: DeviceListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        rvDevices = findViewById(R.id.rvDevices)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        tvDeviceCountHeader = findViewById(R.id.tvDeviceCountHeader)

        setupRecyclerView()

        swipeRefreshLayout.setOnRefreshListener {
            loadConnectedDevices()
        }

        loadConnectedDevices()
    }

    private fun setupRecyclerView() {
        rvDevices.layoutManager = LinearLayoutManager(this)

        deviceListAdapter = DeviceListAdapter(
            devices = deviceList,
            onBlockClick = { device -> handleBlockToggle(device) },
            onItemClick = { device ->
                HistoryActivity.start(this, device.macAddress, device.displayName)
            }
        )
        rvDevices.adapter = deviceListAdapter
    }

    private fun loadConnectedDevices() {
        swipeRefreshLayout.isRefreshing = true

        lifecycleScope.launch {
            val freshDevices = routerAdapter.getConnectedDevices()
            swipeRefreshLayout.isRefreshing = false

            deviceList.clear()
            deviceList.addAll(freshDevices)
            deviceListAdapter.notifyDataSetChanged()

            if (deviceList.isEmpty()) {
                emptyStateLayout.visibility = View.VISIBLE
                rvDevices.visibility = View.GONE
                tvDeviceCountHeader.text = "Connected Devices: 0"
            } else {
                emptyStateLayout.visibility = View.GONE
                rvDevices.visibility = View.VISIBLE
                tvDeviceCountHeader.text = "Connected Devices: ${deviceList.size}"
            }
        }
    }

    private fun handleBlockToggle(device: Device) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Processing request...", Toast.LENGTH_SHORT).show()

            val success = if (device.isBlocked) {
                routerAdapter.unblockDevice(device.macAddress)
            } else {
                routerAdapter.blockDevice(device.macAddress)
            }

            if (success) {
                device.isBlocked = !device.isBlocked
                deviceListAdapter.notifyDataSetChanged()
                val statusMsg = if (device.isBlocked) "Blocked" else "Unblocked"
                Toast.makeText(this@MainActivity, "${device.displayName} $statusMsg successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Action failed! Check router connection.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
