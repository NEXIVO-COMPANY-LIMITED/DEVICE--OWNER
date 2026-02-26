package com.microspace.payo.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.microspace.payo.data.local.database.dao.device.CompleteDeviceRegistrationDao
import com.microspace.payo.data.local.database.dao.device.DeviceBaselineDao
import com.microspace.payo.data.local.database.dao.device.DeviceDataDao
import com.microspace.payo.data.local.database.dao.device.DeviceRegistrationDao
import com.microspace.payo.data.local.database.dao.lock.LockStateRecordDao
import com.microspace.payo.data.local.database.dao.offline.OfflineEventDao
import com.microspace.payo.data.local.database.dao.offline.HeartbeatSyncDao
import com.microspace.payo.data.local.database.dao.heartbeat.HeartbeatResponseDao
import com.microspace.payo.data.local.database.dao.sim.SimChangeHistoryDao
import com.microspace.payo.data.local.database.dao.tamper.TamperDetectionDao
import com.microspace.payo.data.local.database.dao.audit.SyncAuditDao
import com.microspace.payo.data.local.database.entities.device.CompleteDeviceRegistrationEntity
import com.microspace.payo.data.local.database.entities.device.DeviceBaselineEntity
import com.microspace.payo.data.local.database.entities.device.DeviceDataEntity
import com.microspace.payo.data.local.database.entities.device.DeviceRegistrationEntity
import com.microspace.payo.data.local.database.entities.lock.LockStateRecordEntity
import com.microspace.payo.data.local.database.entities.offline.OfflineEvent
import com.microspace.payo.data.local.database.entities.offline.HeartbeatSyncEntity
import com.microspace.payo.data.local.database.entities.heartbeat.HeartbeatResponseEntity
import com.microspace.payo.data.local.database.entities.sim.SimChangeHistoryEntity
import com.microspace.payo.data.local.database.entities.tamper.TamperDetectionEntity
import com.microspace.payo.data.local.database.entities.payment.InstallmentEntity
import com.microspace.payo.data.local.database.entities.audit.SyncAuditEntity
import com.microspace.payo.security.crypto.DatabasePassphraseManager
import net.sqlcipher.database.SupportFactory
import net.sqlcipher.database.SQLiteDatabase

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
        InstallmentEntity::class,
        SyncAuditEntity::class
    ],
    version = 15,
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
    abstract fun heartbeatResponseDao(): HeartbeatResponseDao
    abstract fun simChangeHistoryDao(): SimChangeHistoryDao
    abstract fun lockStateRecordDao(): LockStateRecordDao
    abstract fun installmentDao(): com.microspace.payo.data.local.database.dao.InstallmentDao
    abstract fun syncAuditDao(): SyncAuditDao

    companion object {
        @Volatile
        private var INSTANCE: DeviceOwnerDatabase? = null
        
        fun getDatabase(context: Context): DeviceOwnerDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = DatabasePassphraseManager.getPassphrase(context)
                val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DeviceOwnerDatabase::class.java,
                    "device_owner_database"
                )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
