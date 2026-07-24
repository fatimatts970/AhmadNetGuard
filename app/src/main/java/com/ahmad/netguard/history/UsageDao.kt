package com.ahmad.netguard.history

import androidx.room.Dao
import androidx.room.Query

@Dao
interface UsageDao {

    @Query("SELECT estimatedBytes FROM usage_records WHERE mac = :mac AND dayEpoch = :dayEpoch")
    suspend fun getBytesForDay(mac: String, dayEpoch: Long): Long?

    @Query("""
        INSERT INTO usage_records (mac, dayEpoch, estimatedBytes)
        VALUES (:mac, :dayEpoch, :bytesToAdd)
        ON CONFLICT(mac, dayEpoch) DO UPDATE SET estimatedBytes = estimatedBytes + :bytesToAdd
    """)
    suspend fun addBytes(mac: String, dayEpoch: Long, bytesToAdd: Long)

    @Query("SELECT COALESCE(SUM(estimatedBytes), 0) FROM usage_records WHERE mac = :mac AND dayEpoch BETWEEN :startDay AND :endDay")
    suspend fun getBytesInRange(mac: String, startDay: Long, endDay: Long): Long

    @Query("SELECT COALESCE(SUM(estimatedBytes), 0) FROM usage_records WHERE mac = :mac")
    suspend fun getTotalBytes(mac: String): Long

    @Query("DELETE FROM usage_records WHERE mac = :mac")
    suspend fun clearForDevice(mac: String)
}
