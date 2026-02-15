package com.example.deviceowner.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.deviceowner.data.local.database.dao.*
import com.example.deviceowner.data.local.database.entities.*

/**
 * Primary operational database: offline queue, registration, heartbeat, and tamper sync.
 *
 * Role: Single source of truth for sync/queue. Used by [OfflineSyncWorker], heartbeat
 * offline logic, and tamper event persistence. [AppDatabase] is for optional analytics/history only.
 */
@Database(
    entities = [
        DeviceRegistrationEntity::class,
        CompleteDeviceRegistrationEntity::class,
        DeviceDataEntity::class,
        HeartbeatEntity::class,
        HeartbeatHistoryEntity::class,
        TamperDetectionEntity::class,
        DeviceBaselineEntity::class,
        OfflineEvent::class,
        SimChangeHistoryEntity::class,
        LockStateRecordEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class DeviceOwnerDatabase : RoomDatabase() {

    abstract fun deviceRegistrationDao(): DeviceRegistrationDao
    abstract fun completeDeviceRegistrationDao(): CompleteDeviceRegistrationDao
    abstract fun deviceDataDao(): DeviceDataDao
    abstract fun heartbeatDao(): HeartbeatDao
    abstract fun heartbeatHistoryDao(): HeartbeatHistoryDao
    abstract fun tamperDetectionDao(): TamperDetectionDao
    abstract fun deviceBaselineDao(): DeviceBaselineDao
    abstract fun offlineEventDao(): OfflineEventDao
    abstract fun simChangeHistoryDao(): SimChangeHistoryDao
    abstract fun lockStateRecordDao(): LockStateRecordDao

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