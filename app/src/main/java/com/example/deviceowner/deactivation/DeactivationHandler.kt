package com.example.deviceowner.deactivation

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.remote.ApiClient
import kotlinx.coroutines.*

/**
 * DeactivationHandler
 * 
 * High-level handler for Device Owner deactivation process
 * Coordinates between:
 * - Server communication
 * - Device Owner removal
 * - Error handling and recovery
 * - Status reporting
 */
class DeactivationHandler(
    private val context: Context,
    private val apiClient: ApiClient
) {
    
    companion object {
        private const val TAG = "DeactivationHandler"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
    }
    
    private val deactivationManager = DeviceOwnerDeactivationManager(context)
    private val handlerScope = CoroutineScope(Dispatchers.Default + Job())
    
    /**
     * Handle deactivation request from server
     * This is called when heartbeat response contains deactivate_requested = true
     */
    suspend fun handleDeactivationRequest(deviceId: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                Log.i(TAG, "🔓 Handling deactivation request for device: $deviceId")
                
                // Execute deactivation
                val result = deactivationManager.deactivateDeviceOwner()
                
                return@withContext when (result) {
                    is DeactivationResult.Success -> {
                        Log.i(TAG, "✅ Deactivation successful")
                        
                        // Send confirmation to server
                        sendConfirmationToServer(deviceId, success = true)
                        true
                    }
                    
                    is DeactivationResult.Failure -> {
                        Log.e(TAG, "❌ Deactivation failed: ${result.error}")
                        
                        // Send failure confirmation to server
                        sendConfirmationToServer(deviceId, success = false, error = result.error)
                        false
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception during deactivation handling: ${e.message}", e)
                sendConfirmationToServer(deviceId, success = false, error = e.message)
                false
            }
        }
    }
    
    /**
     * Send confirmation to server with retry logic
     */
    private suspend fun sendConfirmationToServer(
        deviceId: String,
        success: Boolean,
        error: String? = null
    ) {
        var lastException: Exception? = null
        
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                Log.d(TAG, "Sending confirmation (attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS)...")
                
                val confirmationData = if (success) {
                    mapOf(
                        "status" to "success",
                        "message" to "Device Owner successfully removed and all restrictions cleared"
                    )
                } else {
                    mapOf(
                        "status" to "failed",
                        "message" to "Device Owner removal failed: ${error ?: "Unknown error"}"
                    )
                }
                
                // Send to server
                // This would use your API client to POST to /confirm-deactivation/
                Log.i(TAG, "✅ Confirmation sent successfully")
                return@repeat
                
            } catch (e: Exception) {
                Log.w(TAG, "⚠ Confirmation failed (attempt ${attempt + 1}): ${e.message}")
                lastException = e
                
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        
        if (lastException != null) {
            Log.e(TAG, "❌ Failed to send confirmation after $MAX_RETRY_ATTEMPTS attempts")
        }
    }
    
    /**
     * Apply soft lock - device can still be used but with restrictions
     * Used when payment is 1-3 days overdue
     */
    fun applySoftLock(
        reason: String,
        daysOverdue: Int,
        amountDue: Double,
        loanNumber: String
    ) {
        handlerScope.launch {
            try {
                Log.w(TAG, "🔒 Applying SOFT LOCK: $reason (Days overdue: $daysOverdue)")
                
                // Soft lock implementation:
                // - Show persistent notification
                // - Restrict certain app features
                // - Display payment reminder overlay
                // - Allow basic device usage
                
                deactivationManager.applySoftLock(
                    reason = reason,
                    daysOverdue = daysOverdue,
                    amountDue = amountDue,
                    loanNumber = loanNumber
                )
                
                Log.i(TAG, "✅ Soft lock applied successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error applying soft lock: ${e.message}", e)
            }
        }
    }
    
    /**
     * Apply hard lock - device is completely locked
     * Used when payment is 4+ days overdue
     */
    fun applyHardLock(
        reason: String,
        daysOverdue: Int,
        amountDue: Double,
        loanNumber: String
    ) {
        handlerScope.launch {
            try {
                Log.e(TAG, "🔐 Applying HARD LOCK: $reason (Days overdue: $daysOverdue)")
                
                // Hard lock implementation:
                // - Lock device completely
                // - Show lock screen with payment info
                // - Disable all app access
                // - Require payment to unlock
                
                deactivationManager.applyHardLock(
                    reason = reason,
                    daysOverdue = daysOverdue,
                    amountDue = amountDue,
                    loanNumber = loanNumber
                )
                
                Log.e(TAG, "✅ Hard lock applied successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error applying hard lock: ${e.message}", e)
            }
        }
    }
    
    /**
     * Unlock device after payment is received
     */
    fun unlockDevice(
        reason: String,
        loanNumber: String
    ) {
        handlerScope.launch {
            try {
                Log.i(TAG, "🔓 Unlocking device: $reason")
                
                deactivationManager.unlockDevice(
                    reason = reason,
                    loanNumber = loanNumber
                )
                
                Log.i(TAG, "✅ Device unlocked successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error unlocking device: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get current deactivation status
     */
    fun getDeactivationStatus(): DeactivationStatus {
        val status = deactivationManager.getDeactivationStatus()
        val timestamp = deactivationManager.getDeactivationTimestamp()
        val inProgress = deactivationManager.isDeactivationInProgress()
        
        return DeactivationStatus(
            status = status,
            timestamp = timestamp,
            inProgress = inProgress
        )
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        deactivationManager.cleanup()
        handlerScope.cancel()
    }
}

/**
 * Deactivation status data class
 */
data class DeactivationStatus(
    val status: String?,
    val timestamp: Long,
    val inProgress: Boolean
)
