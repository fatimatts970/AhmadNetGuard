package com.ahmad.netguard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ahmad.netguard.R
import com.ahmad.netguard.databinding.ItemDeviceBinding
import com.ahmad.netguard.model.Device

class DeviceListAdapter(
    private val onBlockUnblockClick: (Device) -> Unit,
    private val onDeviceClick: (Device) -> Unit,
) : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    private var devices: List<Device> = emptyList()

    fun submitList(newDevices: List<Device>) {
        devices = newDevices
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position], onBlockUnblockClick, onDeviceClick)
    }

    override fun getItemCount() = devices.size

    class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device, onBlockUnblockClick: (Device) -> Unit, onDeviceClick: (Device) -> Unit) {
            binding.root.setOnClickListener { onDeviceClick(device) }
            binding.textDeviceName.text = device.displayName()
            binding.textIp.text = device.ipAddress
            binding.textMac.text = device.macAddress

            val metaParts = mutableListOf(device.connectionType)
            device.connectedSinceMinutes?.let { metaParts.add("${it}m") }
            device.signalDbm?.let { metaParts.add("$it dBm") }
            if (device.possibleHotspotShare) metaParts.add("⚠ possibly sharing hotspot")
            binding.textMeta.text = metaParts.joinToString(" · ")

            binding.textIp.setTextColor(
                binding.root.context.getColor(
                    if (device.isOnline) R.color.green_online else R.color.text_secondary
                )
            )

            binding.btnBlockUnblock.setImageResource(
                if (device.isBlocked) android.R.drawable.ic_lock_lock
                else android.R.drawable.ic_menu_close_clear_cancel
            )
            binding.btnBlockUnblock.setOnClickListener { onBlockUnblockClick(device) }
        }
    }
}
