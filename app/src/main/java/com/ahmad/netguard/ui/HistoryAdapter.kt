package com.ahmad.netguard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ahmad.netguard.R
import com.ahmad.netguard.databinding.ItemHistoryEventBinding
import com.ahmad.netguard.history.ConnectionEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.EventViewHolder>() {

    private var events: List<ConnectionEvent> = emptyList()
    private val formatter = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    fun submitList(newEvents: List<ConnectionEvent>) {
        events = newEvents
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemHistoryEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        val isOnline = event.eventType == "connected"
        val color = holder.binding.root.context.getColor(
            if (isOnline) R.color.green_online else R.color.red_blocked
        )
        holder.binding.textEvent.text = if (isOnline) "Went Online" else "Went Offline"
        holder.binding.textEvent.setTextColor(color)
        holder.binding.dotStatus.background.setTint(color)
        holder.binding.textTimestamp.text = formatter.format(Date(event.timestampMillis))
    }

    override fun getItemCount() = events.size

    class EventViewHolder(val binding: ItemHistoryEventBinding) : RecyclerView.ViewHolder(binding.root)
}
