package com.microspace.payo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.microspace.payo.data.db.dao.HeartbeatDao
import com.microspace.payo.data.db.converters.StringListConverter
import com.microspace.payo.data.models.heartbeat.Heartbeat

@Database(entities = [Heartbeat::class], version = 1, exportSchema = false)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun heartbeatDao(): HeartbeatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "heartbeat_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}




