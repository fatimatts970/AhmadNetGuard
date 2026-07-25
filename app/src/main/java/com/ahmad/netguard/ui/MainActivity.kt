package com.ahmad.netguard.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ahmad.netguard.R
import com.ahmad.netguard.model.Device
import com.ahmad.netguard.history.AppDatabase
import com.ahmad.netguard.history.AppLog
import com.ahmad.netguard.hostport.HostportRisk
import com.ahmad.netguard.hostport.HostportSuspicion
import com.ahmad.netguard.network.DeviceNameStore
import com.ahmad.netguard.network.RouterSession
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var rvDevices: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var tvDeviceCountHeader: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnSort: ImageButton
    private lateinit var btnBackup: ImageButton

    private val routerAdapter = RouterSession.adapter
    private lateinit var nameStore: DeviceNameStore

    private val allDevices = mutableListOf<Device>()
    private val deviceList = mutableListOf<Device>()
    private lateinit var deviceListAdapter: DeviceListAdapter

    private var currentSort = SortOption.NAME
    private var searchQuery = ""

    private enum class SortOption { NAME, IP, STATUS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        rvDevices = findViewById(R.id.rvDevices)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        tvDeviceCountHeader = findViewById(R.id.tvDeviceCountHeader)
        etSearch = findViewById(R.id.etSearchDevices)
        btnSort = findViewById(R.id.btnSort)
        btnBackup = findViewById(R.id.btnBackup)

        nameStore = DeviceNameStore(this)

        setupRecyclerView()
        setupSearch()
        setupSort()
        setupBackup()

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
            },
            onRenameClick = { device -> showRenameDialog(device) }
        )
        rvDevices.adapter = deviceListAdapter
    }

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) importBackup(uri)
    }

    private fun setupBackup() {
        btnBackup.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 0, 0, "Export Device Names")
            popup.menu.add(0, 1, 1, "Import Device Names")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> exportBackup()
                    1 -> importFileLauncher.launch("application/json")
                }
                true
            }
            popup.show()
        }
    }

    private fun exportBackup() {
        val json = nameStore.exportAllAsJson()
        val exportDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports")
        exportDir.mkdirs()
        val file = File(exportDir, "netguard_device_names_${System.currentTimeMillis()}.json")
        file.writeText(json)

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Export Device Names"))
    }

    private fun importBackup(uri: Uri) {
        try {
            val jsonText = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (jsonText == null) {
                Snackbar.make(rvDevices, "Couldn't read that file", Snackbar.LENGTH_SHORT).show()
                return
            }
            val count = nameStore.importFromJson(jsonText)
            Snackbar.make(rvDevices, "Imported $count device name(s)", Snackbar.LENGTH_SHORT).show()
            loadConnectedDevices()
        } catch (e: Exception) {
            Snackbar.make(rvDevices, "Import failed — not a valid backup file", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilterAndSort()
            }
        })
    }

    private fun setupSort() {
        btnSort.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 0, 0, "Sort by Name")
            popup.menu.add(0, 1, 1, "Sort by IP Address")
            popup.menu.add(0, 2, 2, "Sort by Online Status")
            popup.setOnMenuItemClickListener { item ->
                currentSort = when (item.itemId) {
                    1 -> SortOption.IP
                    2 -> SortOption.STATUS
                    else -> SortOption.NAME
                }
                applyFilterAndSort()
                true
            }
            popup.show()
        }
    }

    private fun applyFilterAndSort() {
        var filtered = if (searchQuery.isBlank()) {
            allDevices.toList()
        } else {
            allDevices.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.ipAddress.contains(searchQuery, ignoreCase = true) ||
                    it.macAddress.contains(searchQuery, ignoreCase = true)
            }
        }

        filtered = when (currentSort) {
            SortOption.NAME -> filtered.sortedBy { it.displayName.lowercase() }
            SortOption.IP -> filtered.sortedBy { ipSortKey(it.ipAddress) }
            SortOption.STATUS -> filtered.sortedByDescending { it.isOnline }
        }

        deviceList.clear()
        deviceList.addAll(filtered)
        deviceListAdapter.notifyDataSetChanged()

        if (deviceList.isEmpty()) {
            emptyStateLayout.visibility = View.VISIBLE
            rvDevices.visibility = View.GONE
        } else {
            emptyStateLayout.visibility = View.GONE
            rvDevices.visibility = View.VISIBLE
        }

        tvDeviceCountHeader.text = "Connected Devices: ${allDevices.size}" +
            if (searchQuery.isNotBlank()) " (${deviceList.size} shown)" else ""
    }

    private fun ipSortKey(ip: String): List<Int> =
        ip.split(".").map { it.toIntOrNull() ?: 0 }

    private fun loadConnectedDevices() {
        swipeRefreshLayout.isRefreshing = true

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val freshDevices = routerAdapter.getDevices().map { device ->
                var d = device
                val savedName = nameStore.getCustomName(device.macAddress)
                if (savedName != null) d = d.copy(displayName = savedName)

                val firstEvent = db.connectionEventDao().getFirstEventForDevice(device.macAddress)
                val lastEvent = db.connectionEventDao().getLastEventForDevice(device.macAddress)
                d = d.copy(
                    firstSeenMillis = firstEvent?.timestampMillis ?: 0L,
                    lastSeenMillis = lastEvent?.timestampMillis ?: 0L
                )

                val history = db.connectionEventDao().getHistoryForDevice(device.macAddress)
                val assessment = HostportSuspicion.assess(d, history)
                d.isHotspotActive = assessment.risk == HostportRisk.MEDIUM || assessment.risk == HostportRisk.HIGH

                d
            }
            swipeRefreshLayout.isRefreshing = false

            allDevices.clear()
            allDevices.addAll(freshDevices)
            applyFilterAndSort()
        }
    }

    private fun handleBlockToggle(device: Device) {
        lifecycleScope.launch {
            val success = if (device.isBlocked) {
                routerAdapter.unblockDevice(device.macAddress)
            } else {
                routerAdapter.blockDevice(device.macAddress)
            }

            val actionType = if (device.isBlocked) "UNBLOCK" else "BLOCK"
            val db = AppDatabase.getInstance(applicationContext)
            db.appLogDao().insert(
                AppLog(
                    type = actionType,
                    message = "${if (success) "" else "Failed to "}${actionType.lowercase()} ${device.displayName} (${device.macAddress})",
                    success = success,
                    timestampMillis = System.currentTimeMillis()
                )
            )

            if (success) {
                device.isBlocked = !device.isBlocked
                val statusMsg = if (device.isBlocked) "Blocked" else "Unblocked"
                Snackbar.make(rvDevices, "${device.displayName} $statusMsg successfully!", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(rvDevices, "Action failed! Check router connection.", Snackbar.LENGTH_LONG).show()
            }
            deviceListAdapter.clearProcessingState()
        }
    }

    private fun showRenameDialog(device: Device) {
        val input = EditText(this)
        input.setText(device.displayName)
        input.setSelection(input.text.length)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Rename Device")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    nameStore.setCustomName(device.macAddress, newName)
                    val index = allDevices.indexOfFirst { it.macAddress == device.macAddress }
                    if (index != -1) {
                        allDevices[index] = allDevices[index].copy(displayName = newName)
                    }
                    applyFilterAndSort()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
