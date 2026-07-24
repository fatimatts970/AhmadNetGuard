package com.ahmad.netguard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.ahmad.netguard.R
import com.ahmad.netguard.model.Device
import com.google.android.material.button.MaterialButton

class DeviceListAdapter(
    private val devices: List<Device>,
    private val onBlockClick: (Device) -> Unit,
    private val onItemClick: (Device) -> Unit,
    private val onRenameClick: (Device) -> Unit = {}
) : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    private val ouiVendors = mapOf(
        "3C:5A:B4" to "Google", "F4:F5:D8" to "Google",
        "A4:83:E7" to "Apple", "DC:A6:32" to "Apple", "F0:18:98" to "Apple",
        "5C:F9:38" to "Samsung", "8C:79:F5" to "Samsung", "E8:50:8B" to "Samsung",
        "64:B4:73" to "Xiaomi", "F8:A4:5F" to "Xiaomi", "50:8F:4C" to "Xiaomi",
        "00:1E:10" to "Huawei", "00:66:4B" to "Huawei", "F8:01:13" to "Huawei",
        "3C:97:0E" to "Vivo", "D8:5D:E2" to "Vivo",
        "AC:37:43" to "Oppo", "40:4E:36" to "Oppo",
        "B0:E5:ED" to "Infinix", "AE:A6:AB" to "Infinix"
    )

    private fun vendorFor(mac: String): String {
        val prefix = mac.uppercase().take(8)
        return ouiVendors[prefix] ?: "Unknown Vendor"
    }

    private fun lastSeenText(isOnline: Boolean, lastSeenMillis: Long): String {
        if (isOnline) return "Online now"
        if (lastSeenMillis <= 0L) return "Last seen: unknown"

        val diffMs = System.currentTimeMillis() - lastSeenMillis
        val minutes = diffMs / 60000
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "Last seen: just now"
            minutes < 60 -> "Last seen: ${minutes}m ago"
            hours < 24 -> "Last seen: ${hours}h ago"
            else -> "Last seen: ${days}d ago"
        }
    }

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDeviceName)
        val tvVendor: TextView = view.findViewById(R.id.tvVendorName)
        val tvIp: TextView = view.findViewById(R.id.tvIpAddress)
        val tvMac: TextView = view.findViewById(R.id.tvMacAddress)
        val tvHotspotBadge: TextView = view.findViewById(R.id.tvHotspotBadge)
        val tvLastSeen: TextView = view.findViewById(R.id.tvLastSeen)
        val dotOnlineStatus: View = view.findViewById(R.id.dotOnlineStatus)
        val ivCopyIp: ImageView = view.findViewById(R.id.ivCopyIp)
        val ivCopyMac: ImageView = view.findViewById(R.id.ivCopyMac)
        val ivRename: ImageView = view.findViewById(R.id.ivRename)
        val btnBlock: MaterialButton = view.findViewById(R.id.btnBlock)
        val progressBlock: View = view.findViewById(R.id.progressBlock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        val context = holder.itemView.context

        holder.tvName.text = device.displayName
        holder.tvVendor.text = vendorFor(device.macAddress)
        holder.tvIp.text = device.ipAddress
        holder.tvMac.text = device.macAddress

        holder.dotOnlineStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (device.isOnline) Color.parseColor("#22A559") else Color.parseColor("#B0BEC5")
        )

        holder.tvHotspotBadge.visibility = if (device.isHotspotActive) View.VISIBLE else View.GONE
        holder.tvLastSeen.text = lastSeenText(device.isOnline, device.lastSeenMillis)

        holder.progressBlock.visibility = View.GONE
        holder.btnBlock.visibility = View.VISIBLE

        if (device.isBlocked) {
            holder.btnBlock.text = "Unblock"
            holder.btnBlock.setBackgroundColor(Color.parseColor("#10B981"))
        } else {
            holder.btnBlock.text = "Block"
            holder.btnBlock.setBackgroundColor(Color.parseColor("#EF4444"))
        }

        holder.btnBlock.setOnClickListener {
            holder.btnBlock.visibility = View.INVISIBLE
            holder.progressBlock.visibility = View.VISIBLE
            onBlockClick(device)
        }

        holder.ivCopyIp.setOnClickListener {
            copyToClipboard(context, "IP Address", device.ipAddress)
        }

        holder.ivCopyMac.setOnClickListener {
            copyToClipboard(context, "MAC Address", device.macAddress)
        }

        holder.ivRename.setOnClickListener {
            onRenameClick(device)
        }

        holder.itemView.setOnClickListener {
            onItemClick(device)
        }
    }

    private fun copyToClipboard(context: Context, label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    fun clearProcessingState() {
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = devices.size
}
