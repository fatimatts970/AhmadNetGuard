package com.ahmad.netguard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.ahmad.netguard.databinding.ItemDeviceBinding
import com.ahmad.netguard.model.Device
import com.ahmad.netguard.network.RouterAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceListAdapter : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    private var items = listOf<Device>()

    fun submitList(list: List<Device>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class DeviceViewHolder(private val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: Device) {
            binding.tvDeviceName.text = device.hostName.ifEmpty { "Unknown Device" }
            binding.tvDeviceIp.text = "${device.ip} • ${device.mac}"

            // Block/Unblock button text
            if (device.isBlocked) {
                binding.btnBlock.text = "Unblock"
            } else {
                binding.btnBlock.text = "Block"
            }

            binding.btnBlock.setOnClickListener {
                val mac = device.mac
                val currentBlocked = device.isBlocked
                val action = if (currentBlocked) "unblock" else "block"

                // Disable button to prevent double click
                binding.btnBlock.isEnabled = false

                CoroutineScope(Dispatchers.IO).launch {
                    val router = RouterAdapterFactory.getAdapter()
                    val success = if (currentBlocked) {
                        router.unblockDevice(mac)
                    } else {
                        router.blockDevice(mac)
                    }

                    withContext(Dispatchers.Main) {
                        binding.btnBlock.isEnabled = true
                        if (success) {
                            Toast.makeText(
                                binding.root.context,
                                "Device $action successful!",
                                Toast.LENGTH_SHORT
                            ).show()
                            // Update device state locally (optional)
                            // We'll just refresh list from Dashboard
                        } else {
                            Toast.makeText(
                                binding.root.context,
                                "$action failed!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }
}
