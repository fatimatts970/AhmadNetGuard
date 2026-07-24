package com.ahmad.netguard.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AppLogDao {

    @Insert
    suspend fun insert(log: AppLog)

    @Query("SELECT * FROM app_logs ORDER BY timestampMillis DESC LIMIT 300")
    suspend fun getAllLogs(): List<AppLog>

    @Query("SELECT * FROM app_logs WHERE type = :type ORDER BY timestampMillis DESC LIMIT 300")
    suspend fun getLogsByType(type: String): List<AppLog>

    @Query("DELETE FROM app_logs")
    suspend fun clearAllLogs()
}
