package com.deviceowner.manager

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.deviceowner.logging.StructuredLogger
import com.deviceowner.services.OfflineLockQueue
import com.deviceowner.security.LockAttemptTracker
import com.deviceowner.models.LockAttempt
import com.deviceowner.manager.LockReasonManager
import com.deviceowner.models.LockReason
import com.deviceowner.models.CategorizedLockCommand
import com.example.deviceowner.data.api.ApiClient
import com.example.deviceowner.data.api.ManageRequest
import com.example.deviceowner.overlay.OverlayController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * LockManager - Feature 4.4: Remote Lock/Unlock
 * 
 * Same implementation as Feature 4.6
 * Uses single API: POST /api/devices/{device_id}/manage/
 * Lock status stored in backend database
 * Heartbeat response (POST /api/devices/{device_id}/data/) includes lock status
 * Device auto-locks/unlocks based on heartbeat response
 * 
 * Enhancement: Lock Attempt Tracking
 * - Tracks all unlock attempts
 * - Implements lockout after repeated failures
 * - Reports suspicious activity to backend
 */
class LockManager(private val context: Context) {
    
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) 
        as DevicePolicyManager
    private val logger = StructuredLogger(context)
    private val apiService = ApiClient.apiService
    private val offlineLockQueue = OfflineLockQueue(context)
    private val attemptTracker = LockAttemptTracker.getInstance(context)
    private val reasonManager = LockReasonManager.getInstance(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("lock_status", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "LockManager"
        private var instance: LockManager? = null
        
        fun getInstance(context: Context): LockManager {
            return instance ?: LockManager(context).also { instance = it }
        }
    }
    
    /**
     * Lock device
     * Same as Feature 4.6 implementation
     * Sends to: POST /api/devices/{device_id}/manage/
     * Backend stores lock status in database
     * 
     * Enhancement: Generate and store lock ID for tracking
     */
    suspend fun lockDevice(reason: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isDeviceOwner()) {
                    Log.e(TAG, "Not device owner")
                    return@withContext Result.failure(Exception("Not device owner"))
                }
                
                // Generate lock ID for tracking
                val lockId = "lock_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
                prefs.edit().putString("current_lock_id", lockId).apply()
                
                // Apply lock locally - same as Feature 4.6
                devicePolicyManager.lockNow()
                Log.d(TAG, "✓ Device locked locally (Lock ID: $lockId)")
                
                // Show overlay - same as Feature 4.6
                showLockOverlay("HARD", reason)
                
                // Save local lock status
                saveLocalLockStatus(true, reason)
                
                val deviceId = getDeviceId()
                
                // Send to backend - same endpoint as Feature 4.6
                val lockCommand = ManageRequest(
                    action = "lock",
                    reason = reason
                )
                
                val response = apiService.manageDevice(deviceId, lockCommand)
                
                if (response.isSuccessful) {
                    Log.d(TAG, "✓ Lock command sent to backend")
                    Log.d(TAG, "✓ Backend stored lock status in database")
                    logger.logInfo(TAG, "Device locked", "lock", mapOf("reason" to reason, "lock_id" to lockId))
                    Result.success(true)
                } else {
                    Log.e(TAG, "✗ Failed to send lock command: ${response.code()}")
                    offlineLockQueue.queueManageCommand("lock", reason)
                    Result.success(true) // Still locked locally
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error locking device", e)
                offlineLockQueue.queueManageCommand("lock", reason)
                Result.success(true) // Still locked locally
            }
        }
    }
    
    /**
     * Unlock device (Admin-only via backend)
     * Device can ONLY be unlocked by admin through backend
     * NO PIN verification - admin controls unlock via heartbeat
     * 
     * This method is NOT called by user - only by heartbeat response
     * Admin unlocks device via backend, heartbeat triggers auto-unlock
     */
    private suspend fun unlockDeviceInternal(reason: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // Dismiss overlay
                dismissLockOverlay()
                Log.d(TAG, "✓ Device unlocked by admin")
                
                // Save local unlock status
                saveLocalLockStatus(false, reason)
                
                logger.logInfo(TAG, "Device unlocked by admin", "unlock", mapOf("reason" to reason))
                Result.success(true)
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error unlocking device", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get local lock status
     * Used by heartbeat to send current status to backend
     * Heartbeat endpoint: POST /api/devices/{device_id}/data/
     */
    fun getLocalLockStatus(): Map<String, Any?> {
        return mapOf(
            "is_locked" to prefs.getBoolean("is_locked", false),
            "reason" to prefs.getString("reason", null),
            "locked_at" to prefs.getLong("locked_at", 0)
        )
    }
    
    /**
     * Handle heartbeat response
     * Heartbeat response from POST /api/devices/{device_id}/data/ includes lock status
     * Auto-locks/unlocks device based on response
     * 
     * Same logic as Feature 4.6
     */
    suspend fun handleHeartbeatResponse(heartbeatResponse: Map<String, Any?>): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val lockStatus = heartbeatResponse["lock_status"] as? Map<String, Any?>
                
                if (lockStatus != null) {
                    val shouldBeLocked = lockStatus["is_locked"] as? Boolean ?: false
                    val reason = lockStatus["reason"] as? String ?: ""
                    
                    val currentlyLocked = prefs.getBoolean("is_locked", false)
                    
                    if (shouldBeLocked && !currentlyLocked) {
                        // Backend says lock → Lock device (same as Feature 4.6)
                        Log.d(TAG, "Heartbeat response: Device should be LOCKED")
                        lockDeviceFromHeartbeat(reason)
                    } else if (!shouldBeLocked && currentlyLocked) {
                        // Backend says unlock → AUTO-UNLOCK device (same as Feature 4.6)
                        Log.d(TAG, "Heartbeat response: Device should be UNLOCKED")
                        unlockDeviceFromHeartbeat(reason)
                    } else {
                        Log.d(TAG, "Heartbeat response: Lock status unchanged")
                    }
                }
                
                Result.success(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling heartbeat response", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Lock device from heartbeat response
     * Same as Feature 4.6 implementation
     */
    private fun lockDeviceFromHeartbeat(reason: String) {
        try {
            devicePolicyManager.lockNow()
            Log.d(TAG, "✓ Device locked from heartbeat")
            
            showLockOverlay("HARD", reason)
            saveLocalLockStatus(true, reason)
            
            logger.logInfo(TAG, "Device locked from heartbeat", "lock_from_heartbeat", mapOf("reason" to reason))
        } catch (e: Exception) {
            Log.e(TAG, "Error locking device from heartbeat", e)
        }
    }
    
    /**
     * Unlock device from heartbeat response
     * AUTO-UNLOCKS device when admin unlocks via backend
     * ONLY way to unlock device - no PIN verification
     * 
     * Enhancement: Track unlock attempt
     */
    private fun unlockDeviceFromHeartbeat(reason: String) {
        try {
            val deviceId = getDeviceId()
            val lockId = prefs.getString("current_lock_id", "unknown") ?: "unknown"
            
            // Record unlock attempt
            val attempt = LockAttempt(
                id = UUID.randomUUID().toString(),
                lockId = lockId,
                deviceId = deviceId,
                timestamp = System.currentTimeMillis(),
                attemptType = LockAttemptTracker.TYPE_HEARTBEAT_AUTO,
                success = true,
                reason = reason,
                adminId = "backend_system",
                ipAddress = null,
                userAgent = "HeartbeatService"
            )
            
            GlobalScope.launch {
                attemptTracker.recordAttempt(attempt)
            }
            
            dismissLockOverlay()
            Log.d(TAG, "✓ Device AUTO-UNLOCKED by admin via heartbeat")
            
            saveLocalLockStatus(false, reason)
            logger.logInfo(TAG, "Device unlocked by admin via heartbeat", "unlock_from_heartbeat", mapOf("reason" to reason))
        } catch (e: Exception) {
            Log.e(TAG, "Error unlocking device from heartbeat", e)
        }
    }
    
    /**
     * Show lock overlay
     * Same as Feature 4.6 implementation
     */
    private fun showLockOverlay(lockType: String, reason: String) {
        try {
            val lockIntent = Intent(context, OverlayController::class.java)
            lockIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            lockIntent.putExtra("lock_type", lockType)
            lockIntent.putExtra("reason", reason)
            context.startActivity(lockIntent)
            Log.d(TAG, "✓ Lock overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing lock overlay", e)
        }
    }
    
    /**
     * Dismiss lock overlay
     * Same as Feature 4.6 implementation
     */
    private fun dismissLockOverlay() {
        try {
            val intent = Intent("com.deviceowner.DISMISS_OVERLAY")
            context.sendBroadcast(intent)
            Log.d(TAG, "✓ Overlay dismissed")
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing overlay", e)
        }
    }
    
    /**
     * Queue manage command for offline
     */
    suspend fun queueManageCommand(action: String, reason: String): Boolean {
        return offlineLockQueue.queueManageCommand(action, reason)
    }
    
    /**
     * Apply all queued commands when reconnected
     */
    suspend fun applyQueuedCommands(): Boolean {
        return offlineLockQueue.applyQueuedCommands()
    }
    
    /**
     * Get lock attempt tracker instance
     * For monitoring and analytics
     */
    fun getAttemptTracker(): LockAttemptTracker {
        return attemptTracker
    }
    
    /**
     * Get lockout status for current lock
     */
    suspend fun getLockoutStatus(): com.deviceowner.models.LockoutStatus {
        val lockId = prefs.getString("current_lock_id", "unknown") ?: "unknown"
        return attemptTracker.getLockoutStatus(lockId)
    }
    
    /**
     * Get attempt summary for current lock
     */
    suspend fun getAttemptSummary(): com.deviceowner.models.LockAttemptSummary {
        val lockId = prefs.getString("current_lock_id", "unknown") ?: "unknown"
        return attemptTracker.getAttemptSummary(lockId)
    }
    
    /**
     * Lock device with categorized reason
     * Enhanced version with reason categorization
     * 
     * @param reason Free-form reason text
     * @param customMessage Optional custom message for user
     * @return Result of lock operation
     */
    suspend fun lockDeviceWithReason(
        reason: String,
        customMessage: String? = null
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isDeviceOwner()) {
                    Log.e(TAG, "Not device owner")
                    return@withContext Result.failure(Exception("Not device owner"))
                }
                
                val deviceId = getDeviceId()
                
                // Create categorized lock command
                val categorizedCommand = reasonManager.createCategorizedLockCommand(
                    deviceId = deviceId,
                    lockType = "HARD",
                    reasonText = reason,
                    customMessage = customMessage
                )
                
                // Generate lock ID for tracking
                val lockId = categorizedCommand.id
                prefs.edit().putString("current_lock_id", lockId).apply()
                prefs.edit().putString("current_lock_reason", categorizedCommand.reason.name).apply()
                
                // Apply lock locally
                devicePolicyManager.lockNow()
                Log.d(TAG, "✓ Device locked locally (Lock ID: $lockId)")
                Log.d(TAG, "✓ Reason: ${categorizedCommand.reason.name} (${categorizedCommand.reason.category})")
                
                // Show overlay with categorized message
                val displayMessage = reasonManager.getFormattedLockMessage(categorizedCommand)
                showLockOverlay("HARD", displayMessage)
                
                // Save local lock status
                saveLocalLockStatus(true, reason)
                
                // Record lock reason for analytics
                reasonManager.recordLockReason(categorizedCommand)
                
                // Send to backend
                val lockCommand = ManageRequest(
                    action = "lock",
                    reason = "${categorizedCommand.reason.name}: $reason"
                )
                
                val response = apiService.manageDevice(deviceId, lockCommand)
                
                if (response.isSuccessful) {
                    Log.d(TAG, "✓ Lock command sent to backend")
                    logger.logInfo(
                        TAG,
                        "Device locked with categorized reason",
                        "lock",
                        mapOf(
                            "reason" to reason,
                            "category" to categorizedCommand.reason.category,
                            "severity" to categorizedCommand.reason.severity.name,
                            "lock_id" to lockId
                        )
                    )
                    Result.success(true)
                } else {
                    Log.e(TAG, "✗ Failed to send lock command: ${response.code()}")
                    offlineLockQueue.queueManageCommand("lock", reason)
                    Result.success(true) // Still locked locally
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error locking device", e)
                offlineLockQueue.queueManageCommand("lock", reason)
                Result.success(true) // Still locked locally
            }
        }
    }
    
    /**
     * Get lock reason manager instance
     */
    fun getReasonManager(): LockReasonManager {
        return reasonManager
    }
    
    /**
     * Get current lock reason
     */
    fun getCurrentLockReason(): LockReason? {
        val reasonName = prefs.getString("current_lock_reason", null)
        return reasonName?.let {
            try {
                LockReason.valueOf(it)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun isDeviceOwner(): Boolean {
        return try {
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }
    
    // PIN verification removed - device can ONLY be unlocked by admin via backend
    
    private fun getDeviceId(): String {
        return try {
            val devicePrefs = context.getSharedPreferences("device_info", Context.MODE_PRIVATE)
            devicePrefs.getString("device_id", "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun saveLocalLockStatus(isLocked: Boolean, reason: String) {
        try {
            prefs.edit().apply {
                putBoolean("is_locked", isLocked)
                putString("reason", reason)
                putLong("locked_at", if (isLocked) System.currentTimeMillis() else 0)
                apply()
            }
            Log.d(TAG, "✓ Local lock status saved")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving local lock status", e)
        }
    }
}
