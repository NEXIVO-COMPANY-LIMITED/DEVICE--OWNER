package com.example.deviceowner.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.deviceowner.data.local.database.dao.device.CompleteDeviceRegistrationDao
import com.example.deviceowner.data.local.database.dao.device.DeviceBaselineDao
import com.example.deviceowner.data.local.database.dao.device.DeviceDataDao
import com.example.deviceowner.data.local.database.dao.device.DeviceRegistrationDao
import com.example.deviceowner.data.local.database.dao.lock.LockStateRecordDao
import com.example.deviceowner.data.local.database.dao.offline.OfflineEventDao
import com.example.deviceowner.data.local.database.dao.offline.HeartbeatSyncDao
import com.example.deviceowner.data.local.database.dao.heartbeat.HeartbeatResponseDao
import com.example.deviceowner.data.local.database.dao.sim.SimChangeHistoryDao
import com.example.deviceowner.data.local.database.dao.tamper.TamperDetectionDao
import com.example.deviceowner.data.local.database.entities.device.CompleteDeviceRegistrationEntity
import com.example.deviceowner.data.local.database.entities.device.DeviceBaselineEntity
import com.example.deviceowner.data.local.database.entities.device.DeviceDataEntity
import com.example.deviceowner.data.local.database.entities.device.DeviceRegistrationEntity
import com.example.deviceowner.data.local.database.entities.lock.LockStateRecordEntity
import com.example.deviceowner.data.local.database.entities.offline.OfflineEvent
import com.example.deviceowner.data.local.database.entities.offline.HeartbeatSyncEntity
import com.example.deviceowner.data.local.database.entities.heartbeat.HeartbeatResponseEntity
import com.example.deviceowner.data.local.database.entities.sim.SimChangeHistoryEntity
import com.example.deviceowner.data.local.database.entities.tamper.TamperDetectionEntity
import com.example.deviceowner.data.local.database.entities.payment.InstallmentEntity

/**
 * Primary operational database: offline queue, registration, and tamper sync.
 *
 * Role: Single source of truth for sync/queue. Used by [OfflineSyncWorker]
 * and tamper event persistence. [AppDatabase] is for optional analytics/history only.
 */
@Database(
    entities = [
        DeviceRegistrationEntity::class,
        CompleteDeviceRegistrationEntity::class,
        DeviceDataEntity::class,
        TamperDetectionEntity::class,
        DeviceBaselineEntity::class,
        OfflineEvent::class,
        HeartbeatSyncEntity::class,
        HeartbeatResponseEntity::class,
        SimChangeHistoryEntity::class,
        LockStateRecordEntity::class,
        InstallmentEntity::class
    ],
    version = 14,
    exportSchema = false
)
abstract class DeviceOwnerDatabase : RoomDatabase() {

    abstract fun deviceRegistrationDao(): DeviceRegistrationDao
    abstract fun completeDeviceRegistrationDao(): CompleteDeviceRegistrationDao
    abstract fun deviceDataDao(): DeviceDataDao
    abstract fun tamperDetectionDao(): TamperDetectionDao
    abstract fun deviceBaselineDao(): DeviceBaselineDao
    abstract fun offlineEventDao(): OfflineEventDao
    abstract fun heartbeatSyncDao(): HeartbeatSyncDao
    abstract fun heartbeatResponseDao(): com.example.deviceowner.data.local.database.dao.heartbeat.HeartbeatResponseDao
    abstract fun simChangeHistoryDao(): SimChangeHistoryDao
    abstract fun lockStateRecordDao(): LockStateRecordDao
    abstract fun installmentDao(): com.example.deviceowner.data.local.database.dao.InstallmentDao

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
