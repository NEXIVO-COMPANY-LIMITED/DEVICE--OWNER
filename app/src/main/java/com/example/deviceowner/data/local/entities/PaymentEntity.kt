package com.example.deviceowner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local database entity for payment records
 * Stores payment information for offline access and sync
 */
@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val paymentId: String,
    val loanId: String,
    val deviceId: String,
    val amount: Double,
    val currency: String,
    val paymentStatus: String,  // PENDING, COMPLETED, FAILED
    val paymentDate: Long,
    val dueDate: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING",  // PENDING, SYNCED, FAILED
    val lastSyncAttempt: Long = 0,
    val isActive: Boolean = true
)
