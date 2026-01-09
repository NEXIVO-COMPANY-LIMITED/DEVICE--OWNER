package com.example.deviceowner.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Payment record for loan enforcement
 * Feature 4.4: Remote Lock/Unlock
 */
data class PaymentRecord(
    @SerializedName("payment_id")
    val paymentId: String,
    
    @SerializedName("loan_number")
    val loanId: String,
    
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("user_ref")
    val userRef: String,
    
    @SerializedName("amount")
    val amount: Double,
    
    @SerializedName("currency")
    val currency: String = "TZS",
    
    @SerializedName("payment_status")
    val paymentStatus: String, // PENDING, COMPLETED, OVERDUE, DEFAULTED
    
    @SerializedName("payment_date")
    val paymentDate: Long,
    
    @SerializedName("due_date")
    val dueDate: Long,
    
    @SerializedName("days_overdue")
    val daysOverdue: Int = 0,
    
    @SerializedName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @SerializedName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable

/**
 * Payment status enumeration
 */
enum class PaymentStatus {
    PENDING,
    COMPLETED,
    OVERDUE,
    DEFAULTED
}

