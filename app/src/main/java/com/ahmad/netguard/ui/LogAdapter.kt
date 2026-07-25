package com.ahmad.netguard.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ahmad.netguard.databinding.ItemLogEntryBinding
import com.ahmad.netguard.history.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private var logs: List<AppLog> = emptyList()
    private val formatter = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    fun submitList(newLogs: List<AppLog>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]
        val color = if (log.success) Color.parseColor("#22A559") else Color.parseColor("#E14747")
        val bgColor = if (log.success) Color.parseColor("#E4F5EA") else Color.parseColor("#FCE7E7")

        holder.binding.textLogMessage.text = log.message
        holder.binding.textLogTimestamp.text = formatter.format(Date(log.timestampMillis))
        holder.binding.textLogType.text = log.type
        holder.binding.textLogType.setTextColor(color)
        holder.binding.textLogType.setBackgroundColor(bgColor)
        holder.binding.dotLogStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }

    override fun getItemCount() = logs.size

    class LogViewHolder(val binding: ItemLogEntryBinding) : RecyclerView.ViewHolder(binding.root)
}
