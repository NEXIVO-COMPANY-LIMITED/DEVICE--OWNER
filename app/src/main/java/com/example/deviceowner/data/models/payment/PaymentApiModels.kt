package com.microspace.payo.data.models.payment

import com.google.gson.annotations.SerializedName

/**
 * Models for Payment and Installment APIs
 */

data class Installment(
    @SerializedName("id") val id: Int,
    @SerializedName("installment_number") val installmentNumber: Int,
    @SerializedName("due_date") val dueDate: String,
    @SerializedName("amount_due") val amountDue: Double,
    @SerializedName("amount_paid") val amountPaid: Double,
    @SerializedName("status") val status: String
)

data class InstallmentResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("installments") val installments: List<Installment>
)

data class PaymentRequest(
    @SerializedName("loan_number") val loanNumber: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("amount") val amount: Double
)

data class PaymentResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("payment_reference") val paymentReference: String?,
    @SerializedName("payment_gateway_url") val paymentGatewayUrl: String?,
    @SerializedName("message") val message: String?
)
