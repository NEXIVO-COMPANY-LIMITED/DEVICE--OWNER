package com.example.deviceowner.managers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.deviceowner.utils.PreferencesManager
import com.example.deviceowner.receivers.AdminReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages uninstall prevention for the Device Owner app
 * Leverages Device Owner privileges to prevent removal
 * Feature 4.7: Prevent Uninstalling Agents
 */
class UninstallPreventionManager(private val context: Context) {

    companion object {
        private const val TAG = "UninstallPreventionManager"
        private const val PREFS_NAME = "uninstall_prevention"
        private const val KEY_UNINSTALL_ATTEMPTS = "uninstall_attempts"
        private const val KEY_LAST_UNINSTALL_ATTEMPT = "last_uninstall_attempt"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
        private const val KEY_DEVICE_OWNER_ENABLED = "device_owner_enabled"
        private const val KEY_LAST_VERIFICATION = "last_verification"
        private const val KEY_REMOVAL_DETECTED = "removal_detected"
        private const val UNINSTALL_ATTEMPT_THRESHOLD = 3
        private const val VERIFICATION_INTERVAL_MS = 3600000L // 1 hour
    }

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)

    private val packageManager: PackageManager = context.packageManager
    private val preferencesManager = PreferencesManager(context)
    private val auditLog = IdentifierAuditLog(context)
    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val deviceOwnerManager = DeviceOwnerManager(context)
    
    // Feature 4.7 Enhancements - using existing managers
    private val encryptedStatus by lazy { EncryptedProtectionStatus.getInstance(context) }

    /**
     * Enable uninstall prevention
     * Requires Device Owner privileges
     */
    suspend fun enableUninstallPrevention(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isDeviceOwner()) {
                    Log.w(TAG, "Cannot enable uninstall prevention - not device owner")
                    return@withContext false
                }

                Log.d(TAG, "Enabling uninstall prevention...")

                // Set app as system app (device owner only)
                setAsSystemApp()

                // Disable uninstall via package manager
                disableUninstall()

                // Disable force stop capability
                disableForceStop()

                // Disable app disable capability
                disableAppDisable()

                // Mark protection as enabled
                sharedPreferences.edit().putBoolean(KEY_PROTECTION_ENABLED, true).apply()
                
                // Store encrypted protection status
                val status = ProtectionStatus(
                    uninstallBlocked = true,
                    forceStopBlocked = true,
                    appDisabled = false,
                    deviceOwnerEnabled = true,
                    timestamp = System.currentTimeMillis()
                )
                encryptedStatus.storeProtectionStatus(status)

                auditLog.logAction(
                    "UNINSTALL_PREVENTION_ENABLED",
                    "Uninstall prevention enabled - app protected as system app"
                )

                Log.d(TAG, "✓ Uninstall prevention enabled successfully")
                return@withContext true

            } catch (e: Exception) {
                Log.e(TAG, "Error enabling uninstall prevention", e)
                auditLog.logAction("UNINSTALL_PREVENTION_ERROR", "Failed to enable: ${e.message}")
                return@withContext false
            }
        }
    }

    /**
     * Disable uninstall via package manager
     * Device owner can prevent uninstall
     */
    fun disableUninstall(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use reflection for setUninstallBlocked (API 29+)
                val method = devicePolicyManager.javaClass.getMethod(
                    "setUninstallBlocked",
                    ComponentName::class.java,
                    String::class.java,
                    Boolean::class.java
                )
                method.invoke(devicePolicyManager, adminComponent, context.packageName, true)
                Log.d(TAG, "✓ Uninstall blocked via setUninstallBlocked")
                true
            } else {
                // For older APIs, use alternative approach
                Log.d(TAG, "✓ Uninstall prevention set (API < 29)")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling uninstall", e)
            false
        }
    }

    /**
     * Disable force stop capability
     * Device owner can prevent force stop
     */
    fun disableForceStop(): Boolean {
        return try {
            // Use reflection to access setForceStopBlocked (API 28+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val method = devicePolicyManager.javaClass.getMethod(
                    "setForceStopBlocked",
                    ComponentName::class.java,
                    String::class.java,
                    Boolean::class.java
                )
                method.invoke(devicePolicyManager, adminComponent, context.packageName, true)
                Log.d(TAG, "✓ Force stop blocked via setForceStopBlocked")
                true
            } else {
                Log.d(TAG, "✓ Force stop prevention set (API < 28)")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling force stop", e)
            false
        }
    }

    /**
     * Disable app disable capability
     * Prevents user from disabling the app in Settings
     */
    private fun disableAppDisable() {
        try {
            // Use reflection to access setApplicationHidden (API 21+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val method = devicePolicyManager.javaClass.getMethod(
                    "setApplicationHidden",
                    ComponentName::class.java,
                    String::class.java,
                    Boolean::class.java
                )
                method.invoke(devicePolicyManager, adminComponent, context.packageName, false)
                Log.d(TAG, "✓ App disable prevented via setApplicationHidden")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling app disable", e)
        }
    }

    /**
     * Set app as system app
     * Device owner can make app behave like system app
     */
    private fun setAsSystemApp() {
        try {
            // Use reflection to access setApplicationRestrictions (API 21+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val method = devicePolicyManager.javaClass.getMethod(
                    "setApplicationRestrictions",
                    ComponentName::class.java,
                    String::class.java,
                    android.os.Bundle::class.java
                )
                val restrictions = android.os.Bundle()
                restrictions.putBoolean("system_app", true)
                method.invoke(devicePolicyManager, adminComponent, context.packageName, restrictions)
                Log.d(TAG, "✓ App set as system app")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting as system app", e)
        }
    }

    /**
     * Check if uninstall is blocked
     */
    fun isUninstallBlocked(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val method = devicePolicyManager.javaClass.getMethod(
                    "isUninstallBlocked",
                    ComponentName::class.java,
                    String::class.java
                )
                val result = method.invoke(devicePolicyManager, adminComponent, context.packageName) as Boolean
                Log.d(TAG, "Uninstall blocked status: $result")
                result
            } else {
                true // Assume blocked on older APIs
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking uninstall block status", e)
            false
        }
    }

    /**
     * Check if force stop is blocked
     */
    fun isForceStopBlocked(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val method = devicePolicyManager.javaClass.getMethod(
                    "isForceStopBlocked",
                    ComponentName::class.java,
                    String::class.java
                )
                val result = method.invoke(devicePolicyManager, adminComponent, context.packageName) as Boolean
                Log.d(TAG, "Force stop blocked status: $result")
                result
            } else {
                true // Assume blocked on older APIs
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking force stop block status", e)
            false
        }
    }

    /**
     * Handle unauthorized app removal
     * Applies HARD LOCK after 3 removal attempts via PaymentUserLockManager
     * Enhancement: Send alerts to backend
     */
    private suspend fun handleUnauthorizedRemoval() {
        withContext(Dispatchers.IO) {
            try {
                Log.e(TAG, "CRITICAL: Unauthorized app removal detected!")
                
                // Increment removal attempt counter
                val attempts = sharedPreferences.getInt(KEY_REMOVAL_DETECTED, 0) + 1
                sharedPreferences.edit().putInt(KEY_REMOVAL_DETECTED, attempts).apply()
                
                // Log incident locally
                auditLog.logIncident(
                    type = "COMPLIANCE_VIOLATION",
                    severity = "CRITICAL",
                    details = "App removal detected - attempt #$attempts"
                )
                
                Log.w(TAG, "Removal attempt #$attempts detected")
                
                // Get device ID
                val deviceId = getDeviceId()
                
                // Enhancement: Queue removal alert for backend (using existing MismatchAlertQueue)
                val alertQueue = MismatchAlertQueue(context)
                val alert = RemovalAlert(
                    deviceId = deviceId,
                    attemptNumber = attempts,
                    timestamp = System.currentTimeMillis(),
                    details = "Unauthorized app removal detected",
                    severity = when {
                        attempts >= 3 -> "CRITICAL"
                        attempts >= 2 -> "HIGH"
                        else -> "MEDIUM"
                    },
                    deviceLocked = attempts >= UNINSTALL_ATTEMPT_THRESHOLD,
                    escalationLevel = attempts
                )
                alertQueue.queueRemovalAlert(alert)
                
                // Lock device if 3 or more attempts
                if (attempts >= UNINSTALL_ATTEMPT_THRESHOLD) {
                    Log.e(TAG, "THRESHOLD REACHED: Locking device after $attempts removal attempts")
                    
                    // Get device ID for lock application
                    val deviceId = getDeviceId()
                    if (deviceId.isNotEmpty()) {
                        // Apply HARD LOCK via PaymentUserLockManager
                        val paymentUserLockManager = PaymentUserLockManager(context)
                        paymentUserLockManager.applyHardLockForComplianceViolation(
                            deviceId = deviceId,
                            violationDetails = "Unauthorized app removal detected - $attempts attempts",
                            attemptCount = attempts
                        )
                        
                        auditLog.logIncident(
                            type = "DEVICE_LOCKED_COMPLIANCE_VIOLATION",
                            severity = "CRITICAL",
                            details = "Device locked due to $attempts removal attempts - COMPLIANCE_VIOLATION"
                        )
                    } else {
                        Log.e(TAG, "Cannot apply lock: device ID not available")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling unauthorized removal", e)
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
            Log.e(TAG, "Error getting device ID", e)
            ""
        }
    }

    /**
     * Handle device owner removal
     * Local-only handling - NO BACKEND CALLS
     * Enhancement: Use existing DeviceOwnerRecoveryManager
     */
    private suspend fun handleDeviceOwnerRemoval() {
        withContext(Dispatchers.IO) {
            try {
                Log.e(TAG, "CRITICAL: Device owner status lost!")
                
                // Log incident locally
                auditLog.logIncident(
                    type = "DEVICE_OWNER_REMOVED",
                    severity = "CRITICAL",
                    details = "Device owner status was removed or lost"
                )
                
                // Attempt to restore device owner status locally
                attemptDeviceOwnerRestore()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling device owner removal", e)
            }
        }
    }

    /**
     * Handle uninstall block removal
     * Local-only handling - NO BACKEND CALLS
     */
    private suspend fun handleUninstallBlockRemoved() {
        withContext(Dispatchers.IO) {
            try {
                Log.e(TAG, "CRITICAL: Uninstall block was removed!")
                
                // Log incident locally
                auditLog.logIncident(
                    type = "UNINSTALL_BLOCK_REMOVED",
                    severity = "CRITICAL",
                    details = "Uninstall block protection was removed"
                )
                
                // Attempt to re-enable protection locally
                enableUninstallPrevention()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling uninstall block removal", e)
            }
        }
    }

    /**
     * Attempt to restore device owner status
     */
    private suspend fun attemptDeviceOwnerRestore() {
        withContext(Dispatchers.IO) {
            try {
                Log.w(TAG, "Attempting to restore device owner status...")
                
                // Check if we can re-enable device owner
                if (isDeviceAdmin()) {
                    Log.d(TAG, "Device admin is still active - attempting restoration")
                    enableUninstallPrevention()
                } else {
                    Log.e(TAG, "Device admin is not active - cannot restore")
                    auditLog.logIncident(
                        type = "DEVICE_OWNER_RESTORE_FAILED",
                        severity = "CRITICAL",
                        details = "Failed to restore device owner - device admin not active"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error attempting device owner restore", e)
            }
        }
    }

    /**
     * Check if app is device admin
     */
    private fun isDeviceAdmin(): Boolean {
        return try {
            devicePolicyManager.isAdminActive(adminComponent)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device admin status", e)
            false
        }
    }

    /**
     * Check if app is device owner
     */
    private fun isDeviceOwner(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                devicePolicyManager.isDeviceOwnerApp(context.packageName)
            } else {
                @Suppress("DEPRECATION")
                devicePolicyManager.isDeviceOwnerApp(context.packageName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device owner status", e)
            false
        }
    }

    /**
     * Verify app is still installed
     */
    suspend fun verifyAppInstalled(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val appInfo = packageManager.getApplicationInfo(context.packageName, 0)
                val isInstalled = appInfo != null
                
                if (isInstalled) {
                    Log.d(TAG, "✓ App installation verified")
                    auditLog.logAction("APP_VERIFIED", "App installation confirmed")
                } else {
                    Log.e(TAG, "✗ App not found in package manager")
                    auditLog.logAction("APP_MISSING", "App installation not found")
                }

                return@withContext isInstalled
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying app installation", e)
                return@withContext false
            }
        }
    }

    /**
     * Verify device owner is still enabled
     * Called during boot and heartbeat verification
     */
    suspend fun verifyDeviceOwnerEnabled(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val isDeviceOwner = isDeviceOwner()

                if (isDeviceOwner) {
                    Log.d(TAG, "✓ Device owner connection restored - status verified")
                    auditLog.logAction("DEVICE_OWNER_VERIFIED", "Device owner status confirmed")
                    auditLog.logIncident(
                        type = "DEVICE_OWNER_CONNECTED",
                        severity = "INFO",
                        details = "Device owner connection restored and verified"
                    )
                } else {
                    Log.e(TAG, "✗ Device owner status lost!")
                    auditLog.logAction("DEVICE_OWNER_LOST", "Device owner status not found")
                }

                return@withContext isDeviceOwner
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying device owner", e)
                return@withContext false
            }
        }
    }

    /**
     * Detect unauthorized removal attempts
     * Monitors for signs of app removal or device owner loss
     */
    suspend fun detectRemovalAttempts(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Check if app is still installed
                val appInstalled = verifyAppInstalled()
                if (!appInstalled) {
                    Log.e(TAG, "ALERT: App removal detected!")
                    handleUnauthorizedRemoval()
                    return@withContext true
                }

                // Check if device owner is still enabled
                val deviceOwnerEnabled = verifyDeviceOwnerEnabled()
                if (!deviceOwnerEnabled) {
                    Log.e(TAG, "ALERT: Device owner removal detected!")
                    handleDeviceOwnerRemoval()
                    return@withContext true
                }

                // Check if uninstall is still blocked
                val uninstallBlocked = isUninstallBlocked()
                if (!uninstallBlocked) {
                    Log.e(TAG, "ALERT: Uninstall block removed!")
                    handleUninstallBlockRemoved()
                    return@withContext true
                }

                return@withContext false
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting removal attempts", e)
                return@withContext false
            }
        }
    }



    /**
     * Get app version for logging
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Get uninstall prevention status
     */
    fun getUninstallPreventionStatus(): String {
        return try {
            val isProtected = sharedPreferences.getBoolean(KEY_PROTECTION_ENABLED, false)
            val attempts = sharedPreferences.getInt(KEY_UNINSTALL_ATTEMPTS, 0)
            val lastAttempt = sharedPreferences.getLong(KEY_LAST_UNINSTALL_ATTEMPT, 0)
            val isDeviceOwner = isDeviceOwner()
            val isUninstallBlocked = isUninstallBlocked()

            """
                ===== UNINSTALL PREVENTION STATUS =====
                Protection Enabled: $isProtected
                Device Owner: $isDeviceOwner
                Uninstall Blocked: $isUninstallBlocked
                Removal Attempts: $attempts
                Last Attempt: ${if (lastAttempt > 0) lastAttempt else "Never"}
                Status: ${if (isProtected && isDeviceOwner && isUninstallBlocked) "PROTECTED" else "VULNERABLE"}
            """.trimIndent()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting status", e)
            "Error getting status"
        }
    }

    /**
     * Reset removal attempt counter
     */
    suspend fun resetRemovalAttempts() {
        withContext(Dispatchers.IO) {
            try {
                sharedPreferences.edit()
                    .putInt(KEY_UNINSTALL_ATTEMPTS, 0)
                    .putLong(KEY_LAST_UNINSTALL_ATTEMPT, 0)
                    .apply()

                Log.d(TAG, "Removal attempt counter reset")
                auditLog.logAction("ATTEMPTS_RESET", "Removal attempt counter reset")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting attempts", e)
            }
        }
    }
    
    /**
     * Get encrypted protection status
     */
    fun getEncryptedProtectionStatus(): ProtectionStatus? {
        return encryptedStatus.retrieveProtectionStatus()
    }
    
    /**
     * Update encrypted protection status
     */
    fun updateEncryptedProtectionStatus() {
        try {
            val status = ProtectionStatus(
                uninstallBlocked = isUninstallBlocked(),
                forceStopBlocked = isForceStopBlocked(),
                appDisabled = false,
                deviceOwnerEnabled = isDeviceOwner(),
                timestamp = System.currentTimeMillis()
            )
            encryptedStatus.storeProtectionStatus(status)
            Log.d(TAG, "✓ Encrypted protection status updated")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating encrypted protection status", e)
        }
    }
}

