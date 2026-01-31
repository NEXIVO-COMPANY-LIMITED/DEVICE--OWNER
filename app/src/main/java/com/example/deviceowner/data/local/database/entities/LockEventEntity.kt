package com.example.deviceowner.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "lock_events")
data class LockEventEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val deviceId: String,
    val lockType: String, // "HARD", "SOFT"
    val reason: String,
    @ColumnInfo(name = "occurredAt")
    val occurredAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "syncStatus")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val syncAttempts: Int = 0,
    val lastSyncAttempt: Long = 0L
)
