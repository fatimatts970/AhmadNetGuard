package com.ahmad.netguard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ahmad.netguard.R
import com.ahmad.netguard.databinding.ItemDeviceBinding
import com.ahmad.netguard.model.Device

private const val HEADER_MAC_PREFIX = "__HEADER__"
private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_DEVICE = 1

fun headerDevice(label: String): Device =
    Device(macAddress = "$HEADER_MAC_PREFIX$label", displayName = label, ipAddress = "")

class DeviceListAdapter(
    private val devices: List<Device>,
    private val onBlockClick: (Device) -> Unit,
    private val onItemClick: (Device) -> Unit,
    private val onRenameClick: (Device) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val processingMacs = mutableSetOf<String>()

    // Block/Unblock request poori hone ke baad MainActivity ye call karta hai
    // taake button dobara enable ho jaye
    fun clearProcessingState() {
        processingMacs.clear()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (devices[position].macAddress.startsWith(HEADER_MAC_PREFIX)) VIEW_TYPE_HEADER else VIEW_TYPE_DEVICE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val textView = TextView(parent.context).apply {
                setPadding(24, 32, 24, 12)
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#64748B"))
                letterSpacing = 0.08f
            }
            HeaderViewHolder(textView)
        } else {
            val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            DeviceViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val device = devices[position]
        when (holder) {
            is HeaderViewHolder -> holder.bind(device.displayName)
            is DeviceViewHolder -> holder.bind(device)
        }
    }

    override fun getItemCount(): Int = devices.size

    class HeaderViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(label: String) {
            textView.text = label
        }
    }

    inner class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device) {
            binding.tvDeviceName.text = device.displayName.ifEmpty { "Unknown Device" }
            binding.tvDeviceIp.text = "${device.ipAddress} • ${device.macAddress}"

            binding.btnBlock.isEnabled = !processingMacs.contains(device.macAddress)
            if (device.isBlocked) {
                binding.btnBlock.text = "Unblock"
                binding.btnBlock.setBackgroundResource(R.drawable.bg_pill_outline_blue)
                binding.btnBlock.setTextColor(binding.root.context.getColor(R.color.blue_unblock))
            } else {
                binding.btnBlock.text = "Block"
                binding.btnBlock.setBackgroundResource(R.drawable.bg_pill_outline_red)
                binding.btnBlock.setTextColor(binding.root.context.getColor(R.color.danger))
            }

            binding.root.setOnLongClickListener {
                onRenameClick(device)
                true
            }
            binding.btnRename.setOnClickListener { onRenameClick(device) }
            binding.btnViewDetails.setOnClickListener { onItemClick(device) }

            binding.btnBlock.setOnClickListener {
                processingMacs.add(device.macAddress)
                notifyItemChanged(bindingAdapterPosition)
                onBlockClick(device)
            }
        }
    }
}
