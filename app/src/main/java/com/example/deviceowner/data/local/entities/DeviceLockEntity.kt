package com.example.deviceowner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local database entity for device lock records
 * Stores lock information for offline access and sync
 */
@Entity(tableName = "device_locks")
data class DeviceLockEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val lockId: String,
    val deviceId: String,
    val lockType: String,  // SOFT, HARD
    val lockStatus: String,  // ACTIVE, INACTIVE, RELEASED
    val lockReason: String,  // PAYMENT_REMINDER, PAYMENT_OVERDUE, SYSTEM_TAMPER, etc.
    val message: String,
    val expiresAt: Long? = null,
    val maxAttempts: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING",  // PENDING, SYNCED, FAILED
    val lastSyncAttempt: Long = 0,
    val isActive: Boolean = true
)
