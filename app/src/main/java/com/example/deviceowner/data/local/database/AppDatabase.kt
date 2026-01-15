package com.example.deviceowner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.deviceowner.data.local.LockAttemptEntity
import com.deviceowner.data.local.LockAttemptDao

@Database(
    entities = [
        DeviceRegistrationEntity::class,
        PaymentEntity::class,
        LoanEntity::class,
        DeviceLockEntity::class,
        LockAttemptEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun deviceRegistrationDao(): DeviceRegistrationDao
    abstract fun paymentDao(): PaymentDao
    abstract fun loanDao(): LoanDao
    abstract fun deviceLockDao(): DeviceLockDao
    abstract fun lockAttemptDao(): LockAttemptDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "device_owner_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
