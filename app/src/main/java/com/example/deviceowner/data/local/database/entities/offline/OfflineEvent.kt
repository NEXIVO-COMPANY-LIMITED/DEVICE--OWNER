package com.microspace.payo.data.local.database.entities.offline

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity to store data that needs to be synchronized when the device comes back online.
 */
@Entity(tableName = "offline_events")
data class OfflineEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String, // "HEARTBEAT", "TAMPER_SIGNAL", "LOCK_STATUS"
    val jsonData: String,  // JSON representation of the data payload
    val timestamp: Long = System.currentTimeMillis()
)
