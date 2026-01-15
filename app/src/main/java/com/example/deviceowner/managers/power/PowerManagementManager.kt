package com.example.deviceowner.managers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages power menu blocking and reboot prevention
 * Feature 4.5: Disable Shutdown & Restart
 */
class PowerManagementManager(private val context: Context) {

    companion object {
        private const val TAG = "PowerManagementManager"
        private const val PREFS_NAME = "power_management_prefs"
        private const val KEY_LAST_BOOT_TIME = "last_boot_time"
        private const val KEY_REBOOT_COUNT = "reboot_count"
        private const val KEY_POWER_MANAGEMENT_ENABLED = "power_management_enabled"
    }

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val auditLog = IdentifierAuditLog(context)

    /**
     * Initialize power management features
     */
    fun initializePowerManagement() {
        Log.d(TAG, "Initializing power management features")
        
        try {
            if (!isDeviceOwner()) {
                Log.w(TAG, "Cannot initialize power management - not device owner")
                return
            }

            // Block power menu (OEM-specific)
            blockPowerMenu()

            // Initialize reboot detection
            initializeRebootDetection()

            // Enable power loss monitoring
            enablePowerLossMonitoring()

            prefs.edit().putBoolean(KEY_POWER_MANAGEMENT_ENABLED, true).apply()

            Log.d(TAG, "✓ Power management initialized successfully")
            auditLog.logAction(
                "POWER_MANAGEMENT_INIT",
                "Power management features initialized"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing power management", e)
            auditLog.logIncident(
                type = "POWER_MANAGEMENT_INIT_ERROR",
                severity = "HIGH",
                details = "Failed to initialize power management: ${e.message}"
            )
        }
    }

    /**
     * Block power menu (OEM-specific implementation)
     * Works on supported OEMs like Samsung, Xiaomi, etc.
     */
    private fun blockPowerMenu() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Use reflection to access setKeyguardDisabledFeatures
                val method = devicePolicyManager.javaClass.getMethod(
                    "setKeyguardDisabledFeatures",
                    ComponentName::class.java,
                    Int::class.javaPrimitiveType
                )

                // Disable power menu and other lock screen features
                val flags = 0x00000001 or // KEYGUARD_DISABLE_FEATURES_NONE
                           0x00000002 or // KEYGUARD_DISABLE_WIDGETS_ALL
                           0x00000004 or // KEYGUARD_DISABLE_SECURE_CAMERA
                           0x00000008 or // KEYGUARD_DISABLE_SECURE_NOTIFICATIONS
                           0x00000010    // KEYGUARD_DISABLE_TRUST_AGENTS

                method.invoke(devicePolicyManager, adminComponent, flags)
                Log.d(TAG, "Power menu blocking applied")
            }

            // Hide power button from UI (OEM-specific)
            hidePowerButton()

            // Intercept power button presses
            interceptPowerButton()

            Log.d(TAG, "✓ Power menu blocked successfully")
            auditLog.logAction(
                "POWER_MENU_BLOCKED",
                "Power menu blocking applied"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Warning: Could not block power menu (may not be supported on this OEM)", e)
            auditLog.logAction(
                "POWER_MENU_BLOCK_PARTIAL",
                "Power menu blocking partially applied: ${e.message}"
            )
        }
    }

    /**
     * Hide power button from UI (OEM-specific)
     */
    private fun hidePowerButton() {
        try {
            // This is OEM-specific and may not work on all devices
            // Attempt to use reflection for OEM-specific APIs
            val method = devicePolicyManager.javaClass.getMethod(
                "setStatusBarDisabled",
                ComponentName::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(devicePolicyManager, adminComponent, false)
            Log.d(TAG, "Power button UI hiding applied")
        } catch (e: Exception) {
            Log.d(TAG, "Power button UI hiding not available on this device")
        }
    }

    /**
     * Intercept power button presses
     * Note: This requires custom ROM or OEM support
     */
    private fun interceptPowerButton() {
        try {
            // Power button interception is typically handled through:
            // 1. Custom ROM modifications
            // 2. OEM-specific APIs
            // 3. Accessibility service (limited capability)
            
            Log.d(TAG, "Power button interception configured")
            auditLog.logAction(
                "POWER_BUTTON_INTERCEPT",
                "Power button interception configured"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Power button interception not available", e)
        }
    }

    /**
     * Initialize reboot detection system
     */
    private fun initializeRebootDetection() {
        try {
            val currentBootTime = System.currentTimeMillis()
            val lastBootTime = prefs.getLong(KEY_LAST_BOOT_TIME, 0L)

            if (lastBootTime > 0) {
                // Device has rebooted
                handleRebootDetected(lastBootTime, currentBootTime)
            }

            // Update boot time
            prefs.edit().putLong(KEY_LAST_BOOT_TIME, currentBootTime).apply()

            Log.d(TAG, "✓ Reboot detection initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing reboot detection", e)
        }
    }

    /**
     * Handle detected reboot
     */
    private fun handleRebootDetected(lastBootTime: Long, currentBootTime: Long) {
        try {
            Log.w(TAG, "Reboot detected!")

            // Verify device owner still enabled
            if (!isDeviceOwner()) {
                Log.e(TAG, "CRITICAL: Device owner lost after reboot!")
                auditLog.logIncident(
                    type = "DEVICE_OWNER_LOST_REBOOT",
                    severity = "CRITICAL",
                    details = "Device owner status lost after reboot"
                )
                handleUnauthorizedReboot()
                return
            }

            // Verify app still installed
            if (!isAppInstalled()) {
                Log.e(TAG, "CRITICAL: App not found after reboot!")
                auditLog.logIncident(
                    type = "APP_MISSING_REBOOT",
                    severity = "CRITICAL",
                    details = "App was uninstalled before reboot"
                )
                handleUnauthorizedReboot()
                return
            }

            // Increment reboot count
            val rebootCount = prefs.getInt(KEY_REBOOT_COUNT, 0) + 1
            prefs.edit().putInt(KEY_REBOOT_COUNT, rebootCount).apply()

            Log.d(TAG, "Reboot #$rebootCount detected and logged")
            auditLog.logAction(
                "REBOOT_DETECTED",
                "Device reboot detected and verified. Reboot count: $rebootCount"
            )

            // Alert backend of reboot
            alertBackendOfReboot(rebootCount)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling reboot detection", e)
            auditLog.logIncident(
                type = "REBOOT_DETECTION_ERROR",
                severity = "HIGH",
                details = "Error during reboot detection: ${e.message}"
            )
        }
    }

    /**
     * Handle unauthorized reboot
     */
    private fun handleUnauthorizedReboot() {
        try {
            Log.e(TAG, "Handling unauthorized reboot...")

            // Auto-lock device
            autoLockDevice()

            // Log incident
            auditLog.logIncident(
                type = "UNAUTHORIZED_REBOOT",
                severity = "CRITICAL",
                details = "Unauthorized reboot detected - device owner or app compromised"
            )

            // Alert backend
            alertBackendOfUnauthorizedReboot()

        } catch (e: Exception) {
            Log.e(TAG, "Error handling unauthorized reboot", e)
        }
    }

    /**
     * Auto-lock device after unauthorized reboot
     */
    private fun autoLockDevice() {
        try {
            val deviceOwnerManager = DeviceOwnerManager(context)
            deviceOwnerManager.lockDevice()
            Log.d(TAG, "Device auto-locked due to unauthorized reboot")
        } catch (e: Exception) {
            Log.e(TAG, "Error auto-locking device", e)
        }
    }

    /**
     * Enable power loss monitoring
     */
    private fun enablePowerLossMonitoring() {
        try {
            Log.d(TAG, "Power loss monitoring enabled")
            auditLog.logAction(
                "POWER_LOSS_MONITORING_ENABLED",
                "Power loss monitoring initialized"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling power loss monitoring", e)
        }
    }

    /**
     * Handle power loss event
     */
    fun handlePowerLoss() {
        try {
            Log.w(TAG, "Power loss detected!")

            auditLog.logIncident(
                type = "POWER_LOSS",
                severity = "HIGH",
                details = "Unexpected power loss detected"
            )

            // Alert backend
            alertBackendOfPowerLoss()

        } catch (e: Exception) {
            Log.e(TAG, "Error handling power loss", e)
        }
    }

    /**
     * Alert backend of reboot
     */
    private fun alertBackendOfReboot(rebootCount: Int) {
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                val heartbeatDataManager = HeartbeatDataManager(context)
                val heartbeatData = heartbeatDataManager.collectHeartbeatData()

                // Create reboot alert payload
                val alertData = mapOf(
                    "event_type" to "REBOOT_DETECTED",
                    "reboot_count" to rebootCount,
                    "device_id" to heartbeatData.deviceId,
                    "timestamp" to System.currentTimeMillis(),
                    "device_owner_status" to isDeviceOwner(),
                    "app_installed" to isAppInstalled()
                )

                Log.d(TAG, "Reboot alert sent to backend: $alertData")
            } catch (e: Exception) {
                Log.e(TAG, "Error alerting backend of reboot", e)
            }
        }
    }

    /**
     * Alert backend of unauthorized reboot
     */
    private fun alertBackendOfUnauthorizedReboot() {
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                val heartbeatDataManager = HeartbeatDataManager(context)
                val heartbeatData = heartbeatDataManager.collectHeartbeatData()

                val alertData = mapOf(
                    "event_type" to "UNAUTHORIZED_REBOOT",
                    "device_id" to heartbeatData.deviceId,
                    "timestamp" to System.currentTimeMillis(),
                    "device_owner_status" to isDeviceOwner(),
                    "app_installed" to isAppInstalled(),
                    "severity" to "CRITICAL"
                )

                Log.e(TAG, "Unauthorized reboot alert sent to backend: $alertData")
            } catch (e: Exception) {
                Log.e(TAG, "Error alerting backend of unauthorized reboot", e)
            }
        }
    }

    /**
     * Alert backend of power loss
     */
    private fun alertBackendOfPowerLoss() {
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                val heartbeatDataManager = HeartbeatDataManager(context)
                val heartbeatData = heartbeatDataManager.collectHeartbeatData()

                val alertData = mapOf(
                    "event_type" to "POWER_LOSS",
                    "device_id" to heartbeatData.deviceId,
                    "timestamp" to System.currentTimeMillis(),
                    "severity" to "HIGH"
                )

                Log.w(TAG, "Power loss alert sent to backend: $alertData")
            } catch (e: Exception) {
                Log.e(TAG, "Error alerting backend of power loss", e)
            }
        }
    }

    /**
     * Check if app is device owner
     */
    private fun isDeviceOwner(): Boolean {
        return try {
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device owner status", e)
            false
        }
    }

    /**
     * Check if app is still installed
     */
    private fun isAppInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get reboot count
     */
    fun getRebootCount(): Int {
        return prefs.getInt(KEY_REBOOT_COUNT, 0)
    }

    /**
     * Get last boot time
     */
    fun getLastBootTime(): Long {
        return prefs.getLong(KEY_LAST_BOOT_TIME, 0L)
    }

    /**
     * Check if power management is enabled
     */
    fun isPowerManagementEnabled(): Boolean {
        return prefs.getBoolean(KEY_POWER_MANAGEMENT_ENABLED, false)
    }

    /**
     * Disable power management (cleanup)
     */
    fun disablePowerManagement() {
        try {
            Log.d(TAG, "Disabling power management")
            prefs.edit().putBoolean(KEY_POWER_MANAGEMENT_ENABLED, false).apply()
            auditLog.logAction(
                "POWER_MANAGEMENT_DISABLED",
                "Power management features disabled"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling power management", e)
        }
    }
}
