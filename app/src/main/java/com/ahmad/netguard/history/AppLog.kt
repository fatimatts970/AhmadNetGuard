package com.ahmad.netguard.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_logs")
data class AppLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,       // "CONNECTION", "LOGIN", "BLOCK", "UNBLOCK", "SECURITY"
    val message: String,
    val success: Boolean,
    val timestampMillis: Long
)
