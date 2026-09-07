package com.ahmad.netguard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ahmad.netguard.databinding.ItemDeviceBinding
import com.ahmad.netguard.model.Device

class DeviceListAdapter(
    private val devices: List<Device>,
    private val onBlockClick: (Device) -> Unit,
    private val onItemClick: (Device) -> Unit,
    private val onRenameClick: (Device) -> Unit
) : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    private val processingMacs = mutableSetOf<String>()

    // Block/Unblock request poori hone ke baad MainActivity ye call karta hai
    // taake button dobara enable ho jaye
    fun clearProcessingState() {
        processingMacs.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    inner class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device) {
            binding.tvDeviceName.text = device.displayName.ifEmpty { "Unknown Device" }
            binding.tvDeviceIp.text = "${device.ipAddress} • ${device.macAddress}"

            binding.btnBlock.isEnabled = !processingMacs.contains(device.macAddress)
            binding.btnBlock.text = if (device.isBlocked) "Unblock" else "Block"

            binding.root.setOnClickListener { onItemClick(device) }
            binding.root.setOnLongClickListener {
                onRenameClick(device)
                true
            }

            binding.btnBlock.setOnClickListener {
                processingMacs.add(device.macAddress)
                notifyItemChanged(bindingAdapterPosition)
                onBlockClick(device)
            }
        }
    }
}
