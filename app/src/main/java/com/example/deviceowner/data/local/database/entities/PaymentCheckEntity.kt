package com.example.deviceowner.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "payment_checks")
data class PaymentCheckEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val loanId: String,
    val paymentStatus: String, // "PAID", "OVERDUE", "DUE_SOON"
    val amountDue: Double,
    val dueDate: Long,
    @ColumnInfo(name = "checkedAt")
    val checkedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "syncStatus")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val syncAttempts: Int = 0,
    val lastSyncAttempt: Long = 0L
)
