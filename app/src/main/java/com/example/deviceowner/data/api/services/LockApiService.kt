package com.example.deviceowner.data.api

import com.example.deviceowner.models.*
import retrofit2.Call
import retrofit2.http.*

/**
 * API service for lock/unlock operations
 * Feature 4.4: Remote Lock/Unlock
 */
interface LockApiService {

    /**
     * Send lock command to backend
     */
    @POST("/api/locks/send")
    fun sendLockCommand(@Body command: LockCommand): Call<ApiResponse<LockCommandResponse>>

    /**
     * Send unlock command to backend
     */
    @POST("/api/locks/unlock")
    fun sendUnlockCommand(@Body command: UnlockCommand): Call<ApiResponse<UnlockCommandResponse>>

    /**
     * Get active locks for device
     */
    @GET("/api/locks/{deviceId}")
    fun getActiveLocks(@Path("deviceId") deviceId: String): Call<ApiResponse<LocksResponse>>

    /**
     * Get payment records for device
     */
    @GET("/api/payments/{deviceId}")
    fun getPayments(@Path("deviceId") deviceId: String): Call<ApiResponse<PaymentsResponse>>

    /**
     * Update payment status
     */
    @POST("/api/payments/{paymentId}/status")
    fun updatePaymentStatus(
        @Path("paymentId") paymentId: String,
        @Body request: UpdatePaymentStatusRequest
    ): Call<ApiResponse<PaymentUpdateResponse>>

    /**
     * Get lock history
     */
    @GET("/api/locks/history")
    fun getLockHistory(): Call<ApiResponse<LockHistoryResponse>>
}

/**
 * API Response wrapper
 */
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
    val timestamp: String? = null
)

/**
 * Lock command response
 */
data class LockCommandResponse(
    val commandId: String,
    val status: String,
    val timestamp: String
)

/**
 * Unlock command response
 */
data class UnlockCommandResponse(
    val commandId: String,
    val status: String,
    val timestamp: String
)

/**
 * Locks response
 */
data class LocksResponse(
    val locks: List<DeviceLock>,
    val count: Int,
    val timestamp: String
)

/**
 * Payments response
 */
data class PaymentsResponse(
    val payments: List<PaymentRecord>,
    val count: Int,
    val timestamp: String
)

/**
 * Update payment status request
 */
data class UpdatePaymentStatusRequest(
    val status: String
)

/**
 * Payment update response
 */
data class PaymentUpdateResponse(
    val payment: PaymentRecord,
    val timestamp: String
)

/**
 * Lock history response
 */
data class LockHistoryResponse(
    val history: List<Any>,
    val count: Int,
    val timestamp: String
)
