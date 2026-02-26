package com.microspace.payo.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.microspace.payo.data.local.database.dao.device.DeviceDataDao
import com.microspace.payo.data.local.database.dao.heartbeat.HeartbeatHistoryDao
import com.microspace.payo.data.local.database.entities.device.DeviceDataEntity
import com.microspace.payo.data.local.database.entities.heartbeat.HeartbeatHistoryEntity
import com.microspace.payo.data.local.database.converters.JsonConverters
import com.microspace.payo.security.crypto.DatabasePassphraseManager
import net.sqlcipher.database.SupportFactory
import net.sqlcipher.database.SQLiteDatabase

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
            val passphrase = DatabasePassphraseManager.getPassphrase(context)
            // SQLCipher's SupportFactory expects the byte array from the passphrase string
            val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
