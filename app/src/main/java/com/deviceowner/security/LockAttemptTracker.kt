package com.deviceowner.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.deviceowner.data.local.AppDatabase
import com.deviceowner.data.local.LockAttemptEntity
import com.deviceowner.logging.StructuredLogger
import com.deviceowner.models.LockAttempt
import com.deviceowner.models.LockAttemptSummary
import com.deviceowner.models.LockoutStatus
import com.example.deviceowner.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Lock Attempt Tracker
 * 
 * Tracks and monitors unlock attempts for security
 * Implements lockout mechanism for repeated failures
 * 
 * Feature 4.4 Enhancement: Lock Attempt Tracking
 * 
 * Features:
 * - Track all unlock attempts (admin, heartbeat, system)
 * - Limit failed attempts (configurable)
 * - Temporary lockout after max failures
 * - Report suspicious activity to backend
 * - Audit trail for compliance
 */
class LockAttemptTracker(private val context: Context) {
    
    private val database = AppDatabase.getInstance(context)
    private val logger = StructuredLogger(context)
    private val apiService = ApiClient.apiService
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "lock_attempt_tracker",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val TAG = "LockAttemptTracker"
        
        // Configuration
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 15 * 60 * 1000L // 15 minutes
        private const val ATTEMPT_WINDOW_MS = 30 * 60 * 1000L // 30 minutes
        private const val CLEANUP_DAYS = 30
        
        // Attempt types
        const val TYPE_ADMIN_BACKEND = "ADMIN_BACKEND"
        const val TYPE_HEARTBEAT_AUTO = "HEARTBEAT_AUTO"
        const val TYPE_SYSTEM = "SYSTEM"
        
        // Lockout keys
        private const val KEY_LOCKOUT_START = "lockout_start_"
        private const val KEY_LOCKOUT_EXPIRES = "lockout_expires_"
        
        @Volatile
        private var instance: LockAttemptTracker? = null
        
        fun getInstance(context: Context): LockAttemptTracker {
            return instance ?: synchronized(this) {
                instance ?: LockAttemptTracker(context).also { instance = it }
            }
        }
    }
    
    /**
     * Record unlock attempt
     * 
     * @param attempt The unlock attempt to record
     * @return true if attempt is allowed, false if locked out
     */
    suspend fun recordAttempt(attempt: LockAttempt): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Recording unlock attempt: ${attempt.attemptType}, success=${attempt.success}")
                
                // Check if currently locked out
                val lockoutStatus = getLockoutStatus(attempt.lockId)
                if (lockoutStatus.isLockedOut) {
                    Log.w(TAG, "Device is locked out. Attempt rejected.")
                    
                    // Record rejected attempt
                    saveAttempt(attempt.copy(
                        success = false,
                        reason = "Locked out - ${lockoutStatus.remainingTime}ms remaining"
                    ))
                    
                    return@withContext false
                }
                
                // Save attempt to database
                saveAttempt(attempt)
                
                // If failed attempt, check if lockout needed
                if (!attempt.success) {
                    val recentFailures = getRecentFailedAttempts(
                        attempt.lockId,
                        ATTEMPT_WINDOW_MS
                    )
                    
                    Log.d(TAG, "Recent failures: ${recentFailures.size}/$MAX_FAILED_ATTEMPTS")
                    
                    if (recentFailures.size >= MAX_FAILED_ATTEMPTS) {
                        // Trigger lockout
                        triggerLockout(attempt.lockId, attempt.deviceId)
                        
                        // Report to backend
                        reportSuspiciousActivity(attempt, recentFailures.size)
                        
                        return@withContext false
                    }
                }
                
                // If successful, clear any lockout
                if (attempt.success) {
                    clearLockout(attempt.lockId)
                }
                
                // Log to audit trail
                logger.logInfo(
                    TAG,
                    "Unlock attempt recorded",
                    "unlock_attempt",
                    mapOf(
                        "type" to attempt.attemptType,
                        "success" to attempt.success.toString(),
                        "lock_id" to attempt.lockId
                    )
                )
                
                return@withContext true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error recording attempt", e)
                return@withContext true // Allow on error to avoid blocking
            }
        }
    }
    
    /**
     * Get lockout status for a lock
     */
    suspend fun getLockoutStatus(lockId: String): LockoutStatus {
        return withContext(Dispatchers.IO) {
            try {
                val lockoutStart = prefs.getLong(KEY_LOCKOUT_START + lockId, 0L)
                val lockoutExpires = prefs.getLong(KEY_LOCKOUT_EXPIRES + lockId, 0L)
                val currentTime = System.currentTimeMillis()
                
                val isLockedOut = lockoutExpires > currentTime
                val remainingTime = if (isLockedOut) lockoutExpires - currentTime else 0L
                
                val recentFailures = getRecentFailedAttempts(lockId, ATTEMPT_WINDOW_MS)
                
                LockoutStatus(
                    isLockedOut = isLockedOut,
                    lockoutStartTime = if (isLockedOut) lockoutStart else null,
                    lockoutExpiresAt = if (isLockedOut) lockoutExpires else null,
                    remainingTime = if (isLockedOut) remainingTime else null,
                    failedAttempts = recentFailures.size,
                    maxAttempts = MAX_FAILED_ATTEMPTS
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting lockout status", e)
                LockoutStatus(
                    isLockedOut = false,
                    lockoutStartTime = null,
                    lockoutExpiresAt = null,
                    remainingTime = null,
                    failedAttempts = 0,
                    maxAttempts = MAX_FAILED_ATTEMPTS
                )
            }
        }
    }
    
    /**
     * Get attempt history for a lock
     */
    suspend fun getAttemptHistory(lockId: String): List<LockAttempt> {
        return withContext(Dispatchers.IO) {
            try {
                val entities = database.lockAttemptDao().getAttempts(lockId)
                entities.map { it.toModel() }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting attempt history", e)
                emptyList()
            }
        }
    }
    
    /**
     * Get attempt summary for a lock
     */
    suspend fun getAttemptSummary(lockId: String): LockAttemptSummary {
        return withContext(Dispatchers.IO) {
            try {
                val attempts = database.lockAttemptDao().getAttempts(lockId)
                val successful = attempts.count { it.success }
                val failed = attempts.count { !it.success }
                val lastAttempt = attempts.maxByOrNull { it.timestamp }
                val lockoutStatus = getLockoutStatus(lockId)
                
                LockAttemptSummary(
                    lockId = lockId,
                    totalAttempts = attempts.size,
                    successfulAttempts = successful,
                    failedAttempts = failed,
                    lastAttemptTime = lastAttempt?.timestamp ?: 0L,
                    isLockedOut = lockoutStatus.isLockedOut,
                    lockoutExpiresAt = lockoutStatus.lockoutExpiresAt
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting attempt summary", e)
                LockAttemptSummary(
                    lockId = lockId,
                    totalAttempts = 0,
                    successfulAttempts = 0,
                    failedAttempts = 0,
                    lastAttemptTime = 0L,
                    isLockedOut = false,
                    lockoutExpiresAt = null
                )
            }
        }
    }
    
    /**
     * Clear lockout for a lock
     */
    suspend fun clearLockout(lockId: String) {
        withContext(Dispatchers.IO) {
            try {
                prefs.edit().apply {
                    remove(KEY_LOCKOUT_START + lockId)
                    remove(KEY_LOCKOUT_EXPIRES + lockId)
                    apply()
                }
                
                Log.d(TAG, "Lockout cleared for lock: $lockId")
                
                logger.logInfo(
                    TAG,
                    "Lockout cleared",
                    "lockout_cleared",
                    mapOf("lock_id" to lockId)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing lockout", e)
            }
        }
    }
    
    /**
     * Clean up old attempts
     */
    suspend fun cleanupOldAttempts() {
        withContext(Dispatchers.IO) {
            try {
                val cutoffTime = System.currentTimeMillis() - (CLEANUP_DAYS * 24 * 60 * 60 * 1000L)
                database.lockAttemptDao().deleteOldAttempts(cutoffTime)
                
                Log.d(TAG, "Old attempts cleaned up (older than $CLEANUP_DAYS days)")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up old attempts", e)
            }
        }
    }
    
    // Private helper methods
    
    private suspend fun saveAttempt(attempt: LockAttempt) {
        try {
            val entity = LockAttemptEntity(
                id = attempt.id,
                lockId = attempt.lockId,
                deviceId = attempt.deviceId,
                timestamp = attempt.timestamp,
                attemptType = attempt.attemptType,
                success = attempt.success,
                reason = attempt.reason,
                adminId = attempt.adminId,
                ipAddress = attempt.ipAddress,
                userAgent = attempt.userAgent
            )
            
            database.lockAttemptDao().insert(entity)
            Log.d(TAG, "Attempt saved: ${attempt.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving attempt", e)
        }
    }
    
    private suspend fun getRecentFailedAttempts(lockId: String, windowMs: Long): List<LockAttemptEntity> {
        return try {
            val since = System.currentTimeMillis() - windowMs
            database.lockAttemptDao().getFailedAttempts(lockId, since)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent failed attempts", e)
            emptyList()
        }
    }
    
    private fun triggerLockout(lockId: String, deviceId: String) {
        try {
            val currentTime = System.currentTimeMillis()
            val expiresAt = currentTime + LOCKOUT_DURATION_MS
            
            prefs.edit().apply {
                putLong(KEY_LOCKOUT_START + lockId, currentTime)
                putLong(KEY_LOCKOUT_EXPIRES + lockId, expiresAt)
                apply()
            }
            
            Log.w(TAG, "LOCKOUT TRIGGERED for lock: $lockId")
            Log.w(TAG, "Lockout expires at: $expiresAt (${LOCKOUT_DURATION_MS / 60000} minutes)")
            
            logger.logInfo(
                TAG,
                "Lockout triggered",
                "lockout_triggered",
                mapOf(
                    "lock_id" to lockId,
                    "device_id" to deviceId,
                    "duration_ms" to LOCKOUT_DURATION_MS.toString(),
                    "expires_at" to expiresAt.toString()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering lockout", e)
        }
    }
    
    private suspend fun reportSuspiciousActivity(attempt: LockAttempt, failureCount: Int) {
        withContext(Dispatchers.IO) {
            try {
                Log.w(TAG, "Reporting suspicious activity to backend")
                Log.w(TAG, "Device: ${attempt.deviceId}, Failures: $failureCount")
                
                // Report to backend via manage endpoint
                val lockCommand = com.example.deviceowner.data.api.ManageRequest(
                    action = "alert",
                    reason = "Suspicious unlock activity detected - $failureCount failed attempts"
                )
                
                val response = apiService.manageDevice(attempt.deviceId, lockCommand)
                
                if (response.isSuccessful) {
                    Log.d(TAG, "✓ Suspicious activity reported to backend")
                } else {
                    Log.e(TAG, "✗ Failed to report suspicious activity: ${response.code()}")
                }
                
                logger.logInfo(
                    TAG,
                    "Suspicious activity reported",
                    "suspicious_activity",
                    mapOf(
                        "device_id" to attempt.deviceId,
                        "lock_id" to attempt.lockId,
                        "failure_count" to failureCount.toString()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error reporting suspicious activity", e)
            }
        }
    }
    
    private fun LockAttemptEntity.toModel(): LockAttempt {
        return LockAttempt(
            id = id,
            lockId = lockId,
            deviceId = deviceId,
            timestamp = timestamp,
            attemptType = attemptType,
            success = success,
            reason = reason,
            adminId = adminId,
            ipAddress = ipAddress,
            userAgent = userAgent
        )
    }
}
