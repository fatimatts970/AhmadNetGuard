package com.ahmad.netguard.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ConnectionEvent::class, UsageRecord::class, AppLog::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionEventDao(): ConnectionEventDao
    abstract fun usageDao(): UsageDao
    abstract fun appLogDao(): AppLogDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ahmad_netguard.db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
