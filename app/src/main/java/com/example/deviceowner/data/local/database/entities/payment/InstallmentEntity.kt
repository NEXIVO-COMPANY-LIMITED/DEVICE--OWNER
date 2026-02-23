package com.example.deviceowner.data.local.database.entities.payment

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Local database entity for storing loan installments fetched from Django backend.
 * Synced periodically from /api/devices/device/{device_id}/installments/
 */
@Entity(
    tableName = "installments",
    indices = [
        Index(value = ["deviceId"]),
        Index(value = ["loanNumber"]),
        Index(value = ["status"])
    ]
)
data class InstallmentEntity(
    @PrimaryKey
    val id: Int,
    
    val deviceId: String,
    val loanNumber: String,
    val installmentNumber: Int,
    val dueDate: String,
    val amountDue: Double,
    val amountPaid: Double,
    val status: String, // pending, partial, paid, overdue
    val isOverdue: Boolean,
    
    // Sync metadata
    val syncedAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun getRemainingAmount(): Double = amountDue - amountPaid
    
    fun getProgressPercentage(): Float = if (amountDue > 0) {
        (amountPaid / amountDue * 100).toFloat()
    } else {
        0f
    }
    
    fun isPaid(): Boolean = status == "paid"
    fun isPending(): Boolean = status == "pending"
    fun isPartial(): Boolean = status == "partial"
}
