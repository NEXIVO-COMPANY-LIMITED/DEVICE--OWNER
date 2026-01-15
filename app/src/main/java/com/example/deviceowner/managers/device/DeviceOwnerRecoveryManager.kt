package com.example.deviceowner.managers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enhanced Device Owner Recovery Manager
 * Handles secure restoration of device owner status with backend integration
 * Provides multiple recovery strategies with fallback mechanisms
 */
class DeviceOwnerRecoveryManager(private val context: Context) {
    
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val prefs: SharedPreferences = context.getSharedPreferences("device_owner_recovery", Context.MODE_PRIVATE)
    private val auditLog = IdentifierAuditLog(context)
    private val isRecoveryInProgress = AtomicBoolean(false)
    
    // Protected recovery state file
    private val recoveryStateFile: File by lazy {
        val file = File(context.cacheDir, "recovery_state.dat")
        file.setReadable(true, true)
        file.setWritable(true, true)
        file
    }
    
    companion object {
        private const val TAG = "DeviceOwnerRecoveryManager"
        private const val KEY_RECOVERY_ATTEMPTS = "recovery_attempts"
        private const val KEY_LAST_RECOVERY_TIME = "last_recovery_time"
        private const val KEY_RECOVERY_STATE = "recovery_state"
        private const val MAX_RECOVERY_ATTEMPTS = 5
        private const val RECOVERY_COOLDOWN_MS = 30000L // 30 seconds between attempts
    }
    
    /**
     * Perform secure device owner restoration with comprehensive error handling
     * Returns true if restoration successful, false otherwise
     */
    suspend fun secureDeviceOwnerRestore(): Boolean = withContext(Dispatchers.IO) {
        // Prevent concurrent recovery attempts
        if (!isRecoveryInProgress.compareAndSet(false, true)) {
            Log.w(TAG, "Recovery already in progress, skipping duplicate attempt")
            return@withContext false
        }
        
        try {
            Log.w(TAG, "Starting secure device owner restoration...")
            
            // Check recovery attempt limits
            if (!canAttemptRecovery()) {
                Log.e(TAG, "Recovery attempt limit exceeded")
                auditLog.logIncident(
                    type = "DEVICE_OWNER_RESTORE_BLOCKED",
                    severity = "CRITICAL",
                    details = "Max recovery attempts exceeded"
                )
                queueDeviceOwnerLossAlert(recoveryAttempted = true, recoverySuccessful = false)
                return@withContext false
            }
            
            // Step 1: Verify device admin status
            if (!verifyDeviceAdminActive()) {
                Log.e(TAG, "Device admin not active - cannot restore")
                recordRecoveryAttempt(success = false, reason = "Device admin not active")
                auditLog.logIncident(
                    type = "DEVICE_OWNER_RESTORE_FAILED",
                    severity = "CRITICAL",
                    details = "Device admin not active"
                )
                queueDeviceOwnerLossAlert(recoveryAttempted = true, recoverySuccessful = false)
                return@withContext false
            }
            
            Log.d(TAG, "✓ Device admin verified as active")
            
            // Step 2: Attempt primary recovery strategy
            val primarySuccess = attemptPrimaryRecovery()
            if (primarySuccess) {
                Log.d(TAG, "✓ Primary recovery strategy successful")
                recordRecoveryAttempt(success = true, reason = "Primary strategy succeeded")
                auditLog.logIncident(
                    type = "DEVICE_OWNER_RESTORED",
                    severity = "INFO",
                    details = "Device owner restored via primary strategy"
                )
                return@withContext true
            }
            
            Log.w(TAG, "Primary recovery failed, attempting fallback strategies...")
            
            // Step 3: Attempt fallback recovery strategies
            val fallbackSuccess = attemptFallbackRecoveries()
            if (fallbackSuccess) {
                Log.d(TAG, "✓ Fallback recovery strategy successful")
                recordRecoveryAttempt(success = true, reason = "Fallback strategy succeeded")
                auditLog.logIncident(
                    type = "DEVICE_OWNER_RESTORED",
                    severity = "INFO",
                    details = "Device owner restored via fallback strategy"
                )
                return@withContext true
            }
            
            Log.e(TAG, "✗ All recovery strategies failed")
            recordRecoveryAttempt(success = false, reason = "All strategies failed")
            auditLog.logIncident(
                type = "DEVICE_OWNER_RESTORE_FAILED",
                severity = "CRITICAL",
                details = "All recovery strategies exhausted"
            )
            queueDeviceOwnerLossAlert(recoveryAttempted = true, recoverySuccessful = false)
            return@withContext false
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during device owner restoration", e)
            recordRecoveryAttempt(success = false, reason = "Exception: ${e.message}")
            auditLog.logIncident(
                type = "DEVICE_OWNER_RESTORE_ERROR",
                severity = "CRITICAL",
                details = "Exception: ${e.message}"
            )
            return@withContext false
        } finally {
            isRecoveryInProgress.set(false)
        }
    }
    
    /**
     * Verify device admin is still active
     */
    private fun verifyDeviceAdminActive(): Boolean {
        return try {
            val adminComponent = ComponentName(context, com.example.deviceowner.receivers.AdminReceiver::class.java)
            devicePolicyManager.isAdminActive(adminComponent).also {
                Log.d(TAG, "Device admin active status: $it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying device admin", e)
            false
        }
    }
    
    /**
     * Primary recovery strategy: Re-enable uninstall prevention
     */
    private fun attemptPrimaryRecovery(): Boolean {
        return try {
            Log.d(TAG, "Attempting primary recovery: re-enable uninstall prevention")
            
            val adminComponent = ComponentName(context, com.example.deviceowner.receivers.AdminReceiver::class.java)
            
            // Attempt to set package as uninstallable
            devicePolicyManager.setUninstallBlocked(adminComponent, context.packageName, true)
            
            // Verify it was set
            val isBlocked = devicePolicyManager.isUninstallBlocked(adminComponent, context.packageName)
            Log.d(TAG, "Uninstall blocked status after primary recovery: $isBlocked")
            
            if (isBlocked) {
                Log.d(TAG, "✓ Primary recovery successful")
                return true
            }
            
            Log.w(TAG, "Primary recovery: uninstall block not verified")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Primary recovery failed: ${e.message}", e)
            false
        }
    }
    
    /**
     * Fallback recovery strategies with multiple attempts
     */
    private fun attemptFallbackRecoveries(): Boolean {
        // Strategy 1: Clear app cache and retry
        if (attemptCacheClearRecovery()) {
            return true
        }
        
        // Strategy 2: Verify and re-activate device admin
        if (attemptDeviceAdminReactivation()) {
            return true
        }
        
        // Strategy 3: Force policy update
        if (attemptForcePolicyUpdate()) {
            return true
        }
        
        return false
    }
    
    /**
     * Fallback Strategy 1: Clear cache and retry
     */
    private fun attemptCacheClearRecovery(): Boolean {
        return try {
            Log.d(TAG, "Fallback Strategy 1: Clearing cache and retrying...")
            
            // Clear device policy cache
            try {
                context.cacheDir.deleteRecursively()
                Log.d(TAG, "Cache cleared")
            } catch (e: Exception) {
                Log.w(TAG, "Could not clear cache: ${e.message}")
            }
            
            // Retry primary recovery
            Thread.sleep(1000) // Wait for cache clear to complete
            attemptPrimaryRecovery()
        } catch (e: Exception) {
            Log.e(TAG, "Cache clear recovery failed: ${e.message}")
            false
        }
    }
    
    /**
     * Fallback Strategy 2: Verify and re-activate device admin
     */
    private fun attemptDeviceAdminReactivation(): Boolean {
        return try {
            Log.d(TAG, "Fallback Strategy 2: Re-activating device admin...")
            
            val adminComponent = ComponentName(context, com.example.deviceowner.receivers.AdminReceiver::class.java)
            
            // Check if admin is still active
            if (!devicePolicyManager.isAdminActive(adminComponent)) {
                Log.w(TAG, "Device admin not active, cannot reactivate")
                return false
            }
            
            // Force re-enable uninstall prevention
            devicePolicyManager.setUninstallBlocked(adminComponent, context.packageName, false)
            Thread.sleep(500)
            devicePolicyManager.setUninstallBlocked(adminComponent, context.packageName, true)
            
            // Verify
            val isBlocked = devicePolicyManager.isUninstallBlocked(adminComponent, context.packageName)
            Log.d(TAG, "Uninstall blocked after reactivation: $isBlocked")
            
            isBlocked
        } catch (e: Exception) {
            Log.e(TAG, "Device admin reactivation failed: ${e.message}")
            false
        }
    }
    
    /**
     * Fallback Strategy 3: Force policy update
     */
    private fun attemptForcePolicyUpdate(): Boolean {
        return try {
            Log.d(TAG, "Fallback Strategy 3: Forcing policy update...")
            
            val adminComponent = ComponentName(context, com.example.deviceowner.receivers.AdminReceiver::class.java)
            
            // Lock device to force policy evaluation
            try {
                devicePolicyManager.lockNow()
                Thread.sleep(2000)
            } catch (e: Exception) {
                Log.w(TAG, "Could not lock device: ${e.message}")
            }
            
            // Retry uninstall block
            devicePolicyManager.setUninstallBlocked(adminComponent, context.packageName, true)
            
            val isBlocked = devicePolicyManager.isUninstallBlocked(adminComponent, context.packageName)
            Log.d(TAG, "Uninstall blocked after policy update: $isBlocked")
            
            isBlocked
        } catch (e: Exception) {
            Log.e(TAG, "Force policy update failed: ${e.message}")
            false
        }
    }
    
    /**
     * Check if recovery can be attempted based on rate limiting
     */
    private fun canAttemptRecovery(): Boolean {
        val attempts = prefs.getInt(KEY_RECOVERY_ATTEMPTS, 0)
        
        if (attempts >= MAX_RECOVERY_ATTEMPTS) {
            Log.e(TAG, "Recovery attempt limit reached: $attempts/$MAX_RECOVERY_ATTEMPTS")
            return false
        }
        
        val lastAttemptTime = prefs.getLong(KEY_LAST_RECOVERY_TIME, 0)
        val timeSinceLastAttempt = System.currentTimeMillis() - lastAttemptTime
        
        if (timeSinceLastAttempt < RECOVERY_COOLDOWN_MS && lastAttemptTime > 0) {
            Log.w(TAG, "Recovery cooldown active: ${RECOVERY_COOLDOWN_MS - timeSinceLastAttempt}ms remaining")
            return false
        }
        
        return true
    }
    
    /**
     * Record recovery attempt result
     */
    private fun recordRecoveryAttempt(success: Boolean, reason: String) {
        try {
            val attempts = prefs.getInt(KEY_RECOVERY_ATTEMPTS, 0)
            val newAttempts = if (success) 0 else attempts + 1
            
            prefs.edit().apply {
                putInt(KEY_RECOVERY_ATTEMPTS, newAttempts)
                putLong(KEY_LAST_RECOVERY_TIME, System.currentTimeMillis())
                putString(KEY_RECOVERY_STATE, reason)
                apply()
            }
            
            Log.d(TAG, "Recovery attempt recorded: success=$success, attempts=$newAttempts, reason=$reason")
        } catch (e: Exception) {
            Log.e(TAG, "Error recording recovery attempt", e)
        }
    }
    
    /**
     * Queue device owner loss alert for backend notification
     */
    private suspend fun queueDeviceOwnerLossAlert(
        recoveryAttempted: Boolean,
        recoverySuccessful: Boolean
    ) {
        withContext(Dispatchers.IO) {
            try {
                val deviceId = getDeviceId()
                if (deviceId.isEmpty()) {
                    Log.w(TAG, "Cannot queue alert: device ID not available")
                    return@withContext
                }
                
                // Create MismatchAlert for backend sync
                val mismatchAlert = com.example.deviceowner.data.api.MismatchAlert(
                    device_id = deviceId,
                    alert_type = "DEVICE_OWNER_LOSS",
                    mismatches = listOf(
                        com.example.deviceowner.data.api.HeartbeatMismatchResponse(
                            field = "device_owner_status",
                            expected_value = "ACTIVE",
                            actual_value = "INACTIVE",
                            severity = if (recoverySuccessful) "MEDIUM" else "CRITICAL",
                            field_type = "CRITICAL_TAMPER"
                        )
                    ),
                    severity = if (recoverySuccessful) "MEDIUM" else "CRITICAL",
                    timestamp = System.currentTimeMillis()
                )
                
                // Queue for backend sync
                val alertQueue = MismatchAlertQueue(context)
                alertQueue.queueAlert(mismatchAlert)
                
                Log.d(TAG, "Device owner loss alert queued for backend")
            } catch (e: Exception) {
                Log.e(TAG, "Error queuing device owner loss alert", e)
            }
        }
    }
    
    /**
     * Get device ID from SharedPreferences
     */
    private fun getDeviceId(): String {
        return try {
            val prefs = context.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
            prefs.getString("device_id", "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Reset recovery state (call after successful restoration)
     */
    fun resetRecoveryState() {
        try {
            prefs.edit().apply {
                putInt(KEY_RECOVERY_ATTEMPTS, 0)
                putLong(KEY_LAST_RECOVERY_TIME, 0)
                putString(KEY_RECOVERY_STATE, "RESET")
                apply()
            }
            Log.d(TAG, "Recovery state reset")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting recovery state", e)
        }
    }
    
    /**
     * Get current recovery state for diagnostics
     */
    fun getRecoveryState(): RecoveryState {
        return RecoveryState(
            attempts = prefs.getInt(KEY_RECOVERY_ATTEMPTS, 0),
            lastAttemptTime = prefs.getLong(KEY_LAST_RECOVERY_TIME, 0),
            lastState = prefs.getString(KEY_RECOVERY_STATE, "UNKNOWN") ?: "UNKNOWN",
            isDeviceAdminActive = verifyDeviceAdminActive(),
            canAttemptRecovery = canAttemptRecovery()
        )
    }
}

/**
 * Data class for device owner loss alert
 */
data class DeviceOwnerLossAlert(
    val deviceId: String,
    val timestamp: Long,
    val details: String,
    val severity: String = "CRITICAL",
    val recoveryAttempted: Boolean,
    val recoverySuccessful: Boolean,
    val recoveryAttempts: Int = 0
)

/**
 * Data class for recovery state diagnostics
 */
data class RecoveryState(
    val attempts: Int,
    val lastAttemptTime: Long,
    val lastState: String,
    val isDeviceAdminActive: Boolean,
    val canAttemptRecovery: Boolean
)
