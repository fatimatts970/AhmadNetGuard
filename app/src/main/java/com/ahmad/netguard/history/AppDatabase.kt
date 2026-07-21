package com.ahmad.netguard.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ConnectionEvent::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionEventDao(): ConnectionEventDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ahmad_netguard.db"
                ).build().also { instance = it }
            }
    }
}
