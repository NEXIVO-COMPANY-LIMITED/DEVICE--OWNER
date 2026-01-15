package com.example.deviceowner.managers

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Real-Time Removal Detection Manager
 * 
 * Enhanced real-time detection with multiple triggers
 * Feature 4.7 Enhancement #5: Real-Time Removal Detection
 * 
 * Features:
 * - Package removal monitoring
 * - Device admin disable monitoring
 * - Settings change monitoring
 * - Immediate incident response
 * - Automatic device lock on threshold
 */
class RealTimeRemovalDetection(private val context: Context) {
    
    private val preventionManager = UninstallPreventionManager(context)
    private val auditLog = IdentifierAuditLog(context)
    private val deviceOwnerManager = DeviceOwnerManager(context)
    private val alertQueue = MismatchAlertQueue(context)
    private val prefs = context.getSharedPreferences("removal_tracking", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var packageRemovalReceiver: PackageRemovalReceiver? = null
    private var adminDisableReceiver: AdminDisableReceiver? = null
    private var settingsObserver: SettingsObserver? = null
    
    companion object {
        private const val TAG = "RealTimeRemovalDetection"
        private const val KEY_ATTEMPT_COUNT = "attempt_count"
        private const val KEY_LAST_ATTEMPT = "last_attempt"
        private const val ATTEMPT_THRESHOLD = 3
        
        @Volatile
        private var instance: RealTimeRemovalDetection? = null
        
        fun getInstance(context: Context): RealTimeRemovalDetection {
            return instance ?: synchronized(this) {
                instance ?: RealTimeRemovalDetection(context).also { instance = it }
            }
        }
    }
    
    /**
     * Setup real-time monitoring
     * Registers all broadcast receivers and observers
     */
    fun setupRealTimeMonitoring() {
        try {
            Log.d(TAG, "Setting up real-time monitoring")
            
            // Monitor 1: Package removal broadcast
            setupPackageRemovalMonitor()
            
            // Monitor 2: Device admin disable broadcast
            setupAdminDisableMonitor()
            
            // Monitor 3: Settings changes
            setupSettingsMonitor()
            
            Log.d(TAG, "✓ Real-time monitoring setup complete")
            
            auditLog.logAction(
                "REAL_TIME_MONITORING_ENABLED",
                "Real-time removal detection enabled"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up real-time monitoring", e)
        }
    }
    
    /**
     * Setup package removal monitor
     */
    private fun setupPackageRemovalMonitor() {
        try {
            packageRemovalReceiver = PackageRemovalReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                addDataScheme("package")
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    packageRemovalReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(packageRemovalReceiver, filter)
            }
            
            Log.d(TAG, "✓ Package removal monitor registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up package removal monitor", e)
        }
    }
    
    /**
     * Setup device admin disable monitor
     */
    private fun setupAdminDisableMonitor() {
        try {
            adminDisableReceiver = AdminDisableReceiver()
            val filter = IntentFilter().apply {
                addAction(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_DISABLED)
                addAction(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_DISABLE_REQUESTED)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    adminDisableReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(adminDisableReceiver, filter)
            }
            
            Log.d(TAG, "✓ Admin disable monitor registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up admin disable monitor", e)
        }
    }
    
    /**
     * Setup settings monitor
     */
    private fun setupSettingsMonitor() {
        try {
            settingsObserver = SettingsObserver(Handler(Looper.getMainLooper()))
            
            // Monitor secure settings changes
            context.contentResolver.registerContentObserver(
                Settings.Secure.CONTENT_URI,
                true,
                settingsObserver!!
            )
            
            Log.d(TAG, "✓ Settings monitor registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up settings monitor", e)
        }
    }
    
    /**
     * Shutdown real-time monitoring
     */
    fun shutdownRealTimeMonitoring() {
        try {
            Log.d(TAG, "Shutting down real-time monitoring")
            
            // Unregister package removal receiver
            packageRemovalReceiver?.let {
                try {
                    context.unregisterReceiver(it)
                    Log.d(TAG, "✓ Package removal monitor unregistered")
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering package removal receiver", e)
                }
            }
            
            // Unregister admin disable receiver
            adminDisableReceiver?.let {
                try {
                    context.unregisterReceiver(it)
                    Log.d(TAG, "✓ Admin disable monitor unregistered")
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering admin disable receiver", e)
                }
            }
            
            // Unregister settings observer
            settingsObserver?.let {
                try {
                    context.contentResolver.unregisterContentObserver(it)
                    Log.d(TAG, "✓ Settings monitor unregistered")
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering settings observer", e)
                }
            }
            
            Log.d(TAG, "✓ Real-time monitoring shutdown complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down real-time monitoring", e)
        }
    }
    
    /**
     * Package Removal Receiver
     */
    inner class PackageRemovalReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val packageName = intent.data?.schemeSpecificPart
                
                if (packageName == context.packageName) {
                    Log.e(TAG, "✗ CRITICAL: Package removal detected in real-time!")
                    Log.e(TAG, "Package: $packageName")
                    
                    handleRemovalDetected("PACKAGE_REMOVED", "App package removal detected")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in package removal receiver", e)
            }
        }
    }
    
    /**
     * Admin Disable Receiver
     */
    inner class AdminDisableReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                when (intent.action) {
                    DeviceAdminReceiver.ACTION_DEVICE_ADMIN_DISABLED -> {
                        Log.e(TAG, "✗ CRITICAL: Device admin disabled detected in real-time!")
                        handleRemovalDetected("ADMIN_DISABLED", "Device admin was disabled")
                    }
                    DeviceAdminReceiver.ACTION_DEVICE_ADMIN_DISABLE_REQUESTED -> {
                        Log.w(TAG, "⚠ WARNING: Device admin disable requested!")
                        handleRemovalDetected("ADMIN_DISABLE_REQUESTED", "Device admin disable requested")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in admin disable receiver", e)
            }
        }
    }
    
    /**
     * Settings Observer
     */
    inner class SettingsObserver(handler: Handler) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }
        
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            try {
                // Check if protection settings changed
                scope.launch {
                    val uninstallBlocked = preventionManager.isUninstallBlocked()
                    val forceStopBlocked = preventionManager.isForceStopBlocked()
                    
                    if (!uninstallBlocked) {
                        Log.e(TAG, "✗ CRITICAL: Uninstall block removed detected!")
                        handleRemovalDetected("UNINSTALL_BLOCK_REMOVED", "Uninstall protection was removed")
                    }
                    
                    if (!forceStopBlocked) {
                        Log.e(TAG, "✗ CRITICAL: Force-stop block removed detected!")
                        handleRemovalDetected("FORCE_STOP_BLOCK_REMOVED", "Force-stop protection was removed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in settings observer", e)
            }
        }
    }
    
    /**
     * Handle removal detected
     * Immediate response to removal attempts
     */
    private fun handleRemovalDetected(type: String, details: String) {
        scope.launch {
            try {
                // Increment attempt counter
                val attemptCount = getRemovalAttemptCount()
                
                Log.w(TAG, "Removal attempt #$attemptCount detected: $type")
                Log.w(TAG, "Details: $details")
                
                // Save attempt
                prefs.edit()
                    .putInt(KEY_ATTEMPT_COUNT, attemptCount)
                    .putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis())
                    .apply()
                
                // Log to audit
                auditLog.logIncident(
                    "REMOVAL_ATTEMPT",
                    "HIGH",
                    "Real-time detection: $type - $details (Attempt #$attemptCount)"
                )
                
                // Queue alert for backend
                val alert = RemovalAlert(
                    deviceId = getDeviceId(),
                    attemptNumber = attemptCount,
                    timestamp = System.currentTimeMillis(),
                    details = "Real-time: $type - $details",
                    severity = when {
                        attemptCount >= 3 -> "CRITICAL"
                        attemptCount >= 2 -> "HIGH"
                        else -> "MEDIUM"
                    },
                    deviceLocked = attemptCount >= ATTEMPT_THRESHOLD,
                    escalationLevel = attemptCount
                )
                alertQueue.queueRemovalAlert(alert)
                
                // Check threshold
                if (attemptCount >= ATTEMPT_THRESHOLD) {
                    Log.e(TAG, "⚠ THRESHOLD REACHED - LOCKING DEVICE")
                    
                    auditLog.logIncident(
                        "REMOVAL_THRESHOLD_REACHED",
                        "CRITICAL",
                        "Device locked after $attemptCount removal attempts (real-time detection)"
                    )
                    
                    // Lock device
                    deviceOwnerManager.lockDevice()
                    
                    // Send critical alert
                    val criticalAlert = RemovalAlert(
                        deviceId = getDeviceId(),
                        attemptNumber = attemptCount,
                        timestamp = System.currentTimeMillis(),
                        details = "CRITICAL: Device locked after $attemptCount attempts - $type",
                        severity = "CRITICAL",
                        deviceLocked = true,
                        escalationLevel = attemptCount
                    )
                    alertQueue.queueRemovalAlert(criticalAlert)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling removal detected", e)
            }
        }
    }
    
    /**
     * Get removal attempt count
     */
    private fun getRemovalAttemptCount(): Int {
        val currentCount = prefs.getInt(KEY_ATTEMPT_COUNT, 0)
        return currentCount + 1
    }
    
    /**
     * Reset attempt counter
     */
    fun resetAttemptCounter() {
        try {
            prefs.edit()
                .putInt(KEY_ATTEMPT_COUNT, 0)
                .putLong(KEY_LAST_ATTEMPT, 0)
                .apply()
            
            Log.d(TAG, "Attempt counter reset")
            auditLog.logAction("ATTEMPT_COUNTER_RESET", "Real-time removal attempt counter reset")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting attempt counter", e)
        }
    }
    
    /**
     * Get current attempt count
     */
    fun getCurrentAttemptCount(): Int {
        return prefs.getInt(KEY_ATTEMPT_COUNT, 0)
    }
    
    /**
     * Get last attempt timestamp
     */
    fun getLastAttemptTimestamp(): Long {
        return prefs.getLong(KEY_LAST_ATTEMPT, 0)
    }
    
    /**
     * Check if monitoring is active
     */
    fun isMonitoringActive(): Boolean {
        return packageRemovalReceiver != null || 
               adminDisableReceiver != null || 
               settingsObserver != null
    }
    
    /**
     * Get device ID
     */
    private fun getDeviceId(): String {
        return try {
            val devicePrefs = context.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
            devicePrefs.getString("device_id", "") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device ID", e)
            ""
        }
    }
}
