package com.example.deviceowner.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Lock type enumeration
 * Feature 4.4: Remote Lock/Unlock
 */
enum class LockType {
    SOFT,      // Warning overlay, device usable
    HARD,      // Full device lock, no interaction
    PERMANENT  // Repossession lock, backend unlock only
}

/**
 * Lock status enumeration
 */
enum class LockStatus {
    ACTIVE,
    INACTIVE,
    PENDING,
    FAILED
}

/**
 * Lock reason enumeration
 */
enum class LockReason {
    LOAN_OVERDUE,
    PAYMENT_DEFAULT,
    DEVICE_MISMATCH,
    SECURITY_BREACH,
    ADMIN_ACTION,
    MANUAL_LOCK,
    UNKNOWN
}

/**
 * Data model for device lock
 */
data class DeviceLock(
    @SerializedName("lock_id")
    val lockId: String,
    
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("lock_type")
    val lockType: LockType,
    
    @SerializedName("lock_status")
    val lockStatus: LockStatus,
    
    @SerializedName("lock_reason")
    val lockReason: LockReason,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @SerializedName("expires_at")
    val expiresAt: Long? = null,
    
    @SerializedName("pin_required")
    val pinRequired: Boolean = false,
    
    @SerializedName("pin_hash")
    val pinHash: String? = null,
    
    @SerializedName("unlock_attempts")
    val unlockAttempts: Int = 0,
    
    @SerializedName("max_attempts")
    val maxAttempts: Int = 3,
    
    @SerializedName("backend_unlock_only")
    val backendUnlockOnly: Boolean = false,
    
    @SerializedName("metadata")
    val metadata: Map<String, String> = emptyMap()
) : Serializable

/**
 * Data model for unlock request
 */
data class UnlockRequest(
    @SerializedName("lock_id")
    val lockId: String,
    
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("pin")
    val pin: String? = null,
    
    @SerializedName("reason")
    val reason: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * Data model for loan record
 */
data class LoanRecord(
    @SerializedName("loan_id")
    val loanId: String,
    
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("user_ref")
    val userRef: String,
    
    @SerializedName("loan_amount")
    val loanAmount: Double,
    
    @SerializedName("currency")
    val currency: String = "TZS",
    
    @SerializedName("loan_status")
    val loanStatus: String, // ACTIVE, OVERDUE, PAID, DEFAULTED
    
    @SerializedName("due_date")
    val dueDate: Long,
    
    @SerializedName("payment_date")
    val paymentDate: Long? = null,
    
    @SerializedName("days_overdue")
    val daysOverdue: Int = 0,
    
    @SerializedName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @SerializedName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable

/**
 * Data model for lock command from backend
 */
data class LockCommand(
    @SerializedName("command_id")
    val commandId: String,
    
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("lock_type")
    val lockType: LockType,
    
    @SerializedName("reason")
    val reason: LockReason,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("pin")
    val pin: String? = null,
    
    @SerializedName("expires_at")
    val expiresAt: Long? = null,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * Data model for unlock command from backend
 */
data class UnlockCommand(
    @SerializedName("command_id")
    val commandId: String,
    
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("lock_id")
    val lockId: String,
    
    @SerializedName("reason")
    val reason: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
