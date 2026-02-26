package com.microspace.payo.data.remote.models

import com.google.gson.annotations.SerializedName

/**
 * Response model for loan installments
 */
data class InstallmentResponse(
    @SerializedName("id")
    val id: Long,
    
    @SerializedName("installment_number")
    val installmentNumber: Int,
    
    @SerializedName("due_date")
    val dueDate: String, // Format: "2026-01-25"
    
    @SerializedName("amount_due")
    val amountDue: String,
    
    @SerializedName("amount_paid")
    val amountPaid: String,
    
    @SerializedName("status")
    val status: String, // "pending", "paid", "overdue"
    
    @SerializedName("paid_at")
    val paidAt: String?, // ISO 8601 format
    
    @SerializedName("is_overdue")
    val isOverdue: String, // "true" or "false" as string
    
    @SerializedName("remaining_amount")
    val remainingAmount: String,
    
    @SerializedName("days_until_due")
    val daysUntilDue: String, // Number as string
    
    @SerializedName("days_overdue")
    val daysOverdue: String // Number as string
)
