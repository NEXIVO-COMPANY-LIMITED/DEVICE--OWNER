package com.deviceowner.models

/**
 * Lock Attempt Model
 * Tracks unlock attempts for security monitoring
 * 
 * Feature 4.4 Enhancement: Lock Attempt Tracking
 */
data class LockAttempt(
    val id: String,
    val lockId: String,
    val deviceId: String,
    val timestamp: Long,
    val attemptType: String, // "ADMIN_BACKEND", "HEARTBEAT_AUTO", "SYSTEM"
    val success: Boolean,
    val reason: String? = null,
    val adminId: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null
)

/**
 * Lock Attempt Summary
 * Aggregated statistics for monitoring
 */
data class LockAttemptSummary(
    val lockId: String,
    val totalAttempts: Int,
    val successfulAttempts: Int,
    val failedAttempts: Int,
    val lastAttemptTime: Long,
    val isLockedOut: Boolean,
    val lockoutExpiresAt: Long?
)

/**
 * Lockout Status
 * Current lockout state
 */
data class LockoutStatus(
    val isLockedOut: Boolean,
    val lockoutStartTime: Long?,
    val lockoutExpiresAt: Long?,
    val remainingTime: Long?,
    val failedAttempts: Int,
    val maxAttempts: Int
)
