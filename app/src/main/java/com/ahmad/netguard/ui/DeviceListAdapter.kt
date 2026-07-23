package com.ahmad.netguard.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ahmad.netguard.R
import com.ahmad.netguard.model.Device
import com.google.android.material.button.MaterialButton

class DeviceListAdapter(
    private val devices: List<Device>,
    private val onBlockClick: (Device) -> Unit,
    private val onItemClick: (Device) -> Unit
) : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDeviceName)
        val tvIp: TextView = view.findViewById(R.id.tvIpAddress)
        val tvMac: TextView = view.findViewById(R.id.tvMacAddress)
        val tvHotspotBadge: TextView = view.findViewById(R.id.tvHotspotBadge)
        val btnBlock: MaterialButton = view.findViewById(R.id.btnBlock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]

        holder.tvName.text = device.name
        holder.tvIp.text = device.ip
        holder.tvMac.text = device.mac

        if (device.isHotspotActive) {
            holder.tvHotspotBadge.visibility = View.VISIBLE
        } else {
            holder.tvHotspotBadge.visibility = View.GONE
        }

        if (device.isBlocked) {
            holder.btnBlock.text = "Unblock"
            holder.btnBlock.setBackgroundColor(Color.parseColor("#10B981"))
        } else {
            holder.btnBlock.text = "Block"
            holder.btnBlock.setBackgroundColor(Color.parseColor("#EF4444"))
        }

        holder.btnBlock.setOnClickListener {
            onBlockClick(device)
        }

        holder.itemView.setOnClickListener {
            onItemClick(device)
        }
    }

    override fun getItemCount(): Int = devices.size
}
