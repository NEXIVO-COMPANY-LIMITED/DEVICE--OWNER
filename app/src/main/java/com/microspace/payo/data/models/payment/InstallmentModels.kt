package com.microspace.payo.data.models.payment

import com.google.gson.annotations.SerializedName

/**
 * Response from GET /api/devices/device/{device_id}/installments/
 */
data class InstallmentsResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("installments")
    val installments: List<InstallmentData>
)

/**
 * Individual installment data from Django backend
 */
data class InstallmentData(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("installment_number")
    val installmentNumber: Int,
    
    @SerializedName("due_date")
    val dueDate: String,
    
    @SerializedName("amount_due")
    val amountDue: Double,
    
    @SerializedName("amount_paid")
    val amountPaid: Double,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("loan_number")
    val loanNumber: String,
    
    @SerializedName("is_overdue")
    val isOverdue: Boolean
)




