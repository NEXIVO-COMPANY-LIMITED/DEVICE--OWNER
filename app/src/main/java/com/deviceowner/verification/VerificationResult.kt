package com.deviceowner.verification

/**
 * Represents the result of device owner verification
 */
enum class VerificationResult {
    SUCCESS,      // Device owner status verified successfully
    DEGRADED,     // Device owner exists but some capabilities are missing
    FAILED,       // Device owner status lost
    ERROR         // Error during verification process
}

/**
 * Detailed verification status with additional information
 */
data class VerificationStatus(
    val result: VerificationResult,
    val isDeviceOwner: Boolean,
    val canLock: Boolean,
    val canSetPassword: Boolean,
    val failureCount: Int = 0,
    val lastVerificationTime: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

/**
 * Represents a verification incident
 */
data class VerificationIncident(
    val type: String,
    val severity: String,
    val details: Map<String, String>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a recovery attempt
 */
data class RecoveryAttempt(
    val timestamp: Long,
    val success: Boolean,
    val reason: String
)
