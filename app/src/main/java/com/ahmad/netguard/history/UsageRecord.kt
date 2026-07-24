package com.ahmad.netguard.history

import androidx.room.Entity

@Entity(tableName = "usage_records", primaryKeys = ["mac", "dayEpoch"])
data class UsageRecord(
    val mac: String,
    val dayEpoch: Long,
    val estimatedBytes: Long
)
