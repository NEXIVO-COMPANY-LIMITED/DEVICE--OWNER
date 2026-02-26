package com.microspace.payo.security.crypto

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.microspace.payo.data.db.dao.HeartbeatDao
import com.microspace.payo.data.models.heartbeat.Heartbeat
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * Encrypted database helper using SQLCipher for database-level encryption.
 * All data in the database is encrypted at rest using AES-256.
 */
class EncryptedDatabaseHelper {

    companion object {
        private const val DATABASE_NAME = "heartbeat_database_encrypted"

        fun createEncryptedDatabase(context: Context): EncryptedAppDatabase {
            // Retrieve the high-entropy passphrase for SQLCipher
            val passphrase = DatabasePassphraseManager.getPassphrase(context)
            
            // SQLCipher uses a SupportFactory to hook into Room
            val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))
            
            return Room.databaseBuilder(
                context.applicationContext,
                EncryptedAppDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration() // Specify migration strategy
                .build()
        }
    }
}

@Database(entities = [Heartbeat::class], version = 1, exportSchema = false)
@TypeConverters(StringListConverter::class)
abstract class EncryptedAppDatabase : RoomDatabase() {
    abstract fun heartbeatDao(): HeartbeatDao
}

// Type converter for Room
class StringListConverter {
    @androidx.room.TypeConverter
    fun fromString(value: String?): List<String>? {
        return value?.split(",")?.map { it.trim() }
    }

    @androidx.room.TypeConverter
    fun toString(list: List<String>?): String? {
        return list?.joinToString(",")
    }
}
