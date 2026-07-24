package com.ahmad.netguard.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ConnectionEventDao {

    @Insert
    suspend fun insert(event: ConnectionEvent)

    @Query("SELECT * FROM connection_events WHERE mac = :mac ORDER BY timestampMillis DESC")
    suspend fun getHistoryForDevice(mac: String): List<ConnectionEvent>

    @Query("DELETE FROM connection_events WHERE mac = :mac")
    suspend fun clearHistoryForDevice(mac: String)

    @Query("DELETE FROM connection_events")
    suspend fun clearAllHistory()

    @Query("SELECT * FROM connection_events WHERE mac = :mac ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLastEventForDevice(mac: String): ConnectionEvent?

    @Query("SELECT * FROM connection_events WHERE mac = :mac ORDER BY timestampMillis ASC LIMIT 1")
    suspend fun getFirstEventForDevice(mac: String): ConnectionEvent?
}
