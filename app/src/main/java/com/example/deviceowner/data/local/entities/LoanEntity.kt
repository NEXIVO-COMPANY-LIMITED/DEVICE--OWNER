package com.example.deviceowner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local database entity for loan records
 * Stores loan information for offline access and sync
 */
@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val loanId: String,
    val deviceId: String,
    val userRef: String,
    val loanAmount: Double,
    val currency: String,
    val loanStatus: String,  // ACTIVE, OVERDUE, PAID, DEFAULTED
    val dueDate: Long,
    val paymentDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING",  // PENDING, SYNCED, FAILED
    val lastSyncAttempt: Long = 0,
    val isActive: Boolean = true
)
