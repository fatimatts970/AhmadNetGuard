package com.ahmad.netguard.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection_events")
data class ConnectionEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mac: String,
    val deviceNameAtTime: String,
    val eventType: String,
    val timestampMillis: Long,
)
