package com.example.deviceowner.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "heartbeats")
data class HeartbeatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: String,
    val timestamp: Long,
    val batteryLevel: Int?,
    val isOnline: Boolean,
    val syncStatus: String = "pending"
)