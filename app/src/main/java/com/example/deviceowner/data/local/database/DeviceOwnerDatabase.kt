package com.example.deviceowner.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.deviceowner.data.local.database.dao.*
import com.example.deviceowner.data.local.database.entities.*

@Database(
    entities = [
        DeviceRegistrationEntity::class,
        CompleteDeviceRegistrationEntity::class,
        HeartbeatEntity::class,
        TamperDetectionEntity::class,
        DeviceBaselineEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DeviceOwnerDatabase : RoomDatabase() {
    
    abstract fun deviceRegistrationDao(): DeviceRegistrationDao
    abstract fun completeDeviceRegistrationDao(): CompleteDeviceRegistrationDao
    abstract fun heartbeatDao(): HeartbeatDao
    abstract fun tamperDetectionDao(): TamperDetectionDao
    abstract fun deviceBaselineDao(): DeviceBaselineDao
    
    companion object {
        @Volatile
        private var INSTANCE: DeviceOwnerDatabase? = null
        
        fun getDatabase(context: Context): DeviceOwnerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DeviceOwnerDatabase::class.java,
                    "device_owner_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}