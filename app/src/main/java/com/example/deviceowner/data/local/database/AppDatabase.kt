package com.example.deviceowner.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.deviceowner.data.local.database.dao.DeviceDataDao
import com.example.deviceowner.data.local.database.dao.HeartbeatHistoryDao
import com.example.deviceowner.data.local.database.entities.DeviceDataEntity
import com.example.deviceowner.data.local.database.entities.HeartbeatHistoryEntity
import com.example.deviceowner.data.local.database.converters.JsonConverters

/**
 * Room Database for optional analytics and history (secondary store).
 *
 * Role: Device data snapshots and heartbeat history for local analytics/reporting.
 * Not used for offline queue or sync – use [DeviceOwnerDatabase] for operational data
 * (offline events, registration, heartbeat queue, tamper events).
 */
@Database(
    entities = [
        DeviceDataEntity::class,
        HeartbeatHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(JsonConverters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun deviceDataDao(): DeviceDataDao
    abstract fun heartbeatHistoryDao(): HeartbeatHistoryDao
    
    companion object {
        private const val DATABASE_NAME = "device_owner.db"
        
        @Volatile
        private var instance: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }
        
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
