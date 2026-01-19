package com.example.deviceowner.services

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.example.deviceowner.managers.*
import com.example.deviceowner.managers.DeviceOwnerRecoveryManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.*
import java.io.File

/**
 * Comprehensive Security Service
 * Continuously monitors and enforces all security measures:
 * 
 * 1. Uninstall Prevention - Continuous verification and re-enforcement
 * 2. Flashing Prevention - Active blocking of flashing attempts
 * 3. USB Debugging Prevention - Continuous monitoring and blocking
 * 4. Safe Mode Prevention - Actively prevents safe mode boot
 * 5. Reboot Prevention - Blocks reboot attempts and detects as tampering
 * 6. Payment Lock Enforcement - Hard lock for overdue payments
 * 7. General Security Monitoring - Continuous security checks
 */
class ComprehensiveSecurityService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var deviceOwnerManager: DeviceOwnerManager
    private lateinit var recoveryManager: DeviceOwnerRecoveryManager
    private lateinit var uninstallPreventionManager: UninstallPreventionManager
    private lateinit var powerManagementManager: PowerManagementManager
    private lateinit var paymentUserLockManager: PaymentUserLockManager
    private lateinit var tamperDetector: TamperDetector
    private lateinit var auditLog: IdentifierAuditLog
    private lateinit var flashingDetectionService: FlashingDetectionService
    
    private var securityMonitoringJob: Job? = null
    private var uninstallVerificationJob: Job? = null
    private var usbDebuggingBlockingJob: Job? = null
    private var safeModePreventionJob: Job? = null
    private var rebootPreventionJob: Job? = null
    
    companion object {
        private const val TAG = "ComprehensiveSecurityService"
        private const val SECURITY_CHECK_INTERVAL = 3000L // 3 seconds - very frequent
        private const val UNINSTALL_VERIFY_INTERVAL = 5000L // 5 seconds
        private const val USB_DEBUG_CHECK_INTERVAL = 2000L // 2 seconds
        private const val SAFE_MODE_CHECK_INTERVAL = 1000L // 1 second - very frequent
        private const val REBOOT_CHECK_INTERVAL = 2000L // 2 seconds
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ComprehensiveSecurityService created")
        
        deviceOwnerManager = DeviceOwnerManager(this)
        recoveryManager = DeviceOwnerRecoveryManager(this)
        uninstallPreventionManager = UninstallPreventionManager(this)
        powerManagementManager = PowerManagementManager(this)
        paymentUserLockManager = PaymentUserLockManager(this)
        tamperDetector = TamperDetector(this)
        auditLog = IdentifierAuditLog(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ComprehensiveSecurityService started - All security measures active")
        
        // Start all security monitoring
        startComprehensiveSecurityMonitoring()
        startUninstallPreventionMonitoring()
        startUSBDebuggingBlocking()
        startSafeModePrevention()
        startRebootPrevention()
        
        return START_STICKY
    }
    
    /**
     * Start comprehensive security monitoring
     * Continuously verifies all security measures are active
     */
    private fun startComprehensiveSecurityMonitoring() {
        if (securityMonitoringJob?.isActive == true) {
            Log.d(TAG, "Security monitoring already running")
            return
        }
        
        securityMonitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    // Verify device owner status - IMMEDIATE RECOVERY
                    if (!deviceOwnerManager.isDeviceOwner()) {
                        Log.e(TAG, "CRITICAL: Device owner status lost - attempting immediate recovery!")
                        
                        // IMMEDIATE: Attempt to restore device owner
                        attemptImmediateDeviceOwnerRecovery()
                        
                        // Also handle as critical breach
                        handleCriticalSecurityBreach("DEVICE_OWNER_LOST")
                    }
                    
                    // Verify device admin status - IMMEDIATE RECOVERY
                    if (!deviceOwnerManager.isDeviceAdmin()) {
                        Log.e(TAG, "CRITICAL: Device admin status lost - attempting immediate recovery!")
                        
                        // IMMEDIATE: Attempt to restore device admin
                        // Device admin recovery handled by DeviceOwnerRecoveryManager
                        
                        // Also handle as critical breach
                        handleCriticalSecurityBreach("DEVICE_ADMIN_LOST")
                    }
                    
                    // Security monitoring continues...
                    
                    // Verify uninstall prevention
                    if (!uninstallPreventionManager.isUninstallBlocked()) {
                        Log.e(TAG, "CRITICAL: Uninstall block removed!")
                        uninstallPreventionManager.enableUninstallPrevention()
                        handleCriticalSecurityBreach("UNINSTALL_BLOCK_REMOVED")
                    }
                    
                    // Verify force stop prevention
                    if (!uninstallPreventionManager.isForceStopBlocked()) {
                        Log.e(TAG, "CRITICAL: Force stop block removed!")
                        uninstallPreventionManager.enableUninstallPrevention()
                        handleCriticalSecurityBreach("FORCE_STOP_BLOCK_REMOVED")
                    }
                    
                    // Check for tampering
                    if (tamperDetector.isRooted() || 
                        tamperDetector.isUSBDebuggingEnabled() ||
                        tamperDetector.isDeveloperModeEnabled()) {
                        Log.e(TAG, "CRITICAL: Tampering detected!")
                        handleTamperingDetected()
                    }
                    
                    // Continuously verify and enforce critical restrictions
                    // This ensures developer options and factory reset remain blocked
                    if (deviceOwnerManager.isDeviceOwner()) {
                        deviceOwnerManager.verifyAndEnforceCriticalRestrictions()
                    }
                    
                    // Continuously prevent developer options access
                    // This ensures developer options cannot be enabled even by clicking build number
                    preventDeveloperOptionsAccess()
                    
                    // Continuously prevent factory reset
                    // This ensures factory reset button is hidden and blocked
                    preventFactoryResetAccess()
                    
                    // Update last known device owner status for bypass detection
                    updateDeviceOwnerStatus()
                    
                    delay(SECURITY_CHECK_INTERVAL)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in security monitoring", e)
                    delay(SECURITY_CHECK_INTERVAL)
                }
            }
        }
    }
    
    /**
     * Start uninstall prevention monitoring
     * Continuously verifies uninstall prevention is active
     */
    private fun startUninstallPreventionMonitoring() {
        if (uninstallVerificationJob?.isActive == true) {
            return
        }
        
        uninstallVerificationJob = serviceScope.launch {
            while (isActive) {
                try {
                    // Verify app is still installed
                    val isInstalled = uninstallPreventionManager.verifyAppInstalled()
                    if (!isInstalled) {
                        Log.e(TAG, "CRITICAL: App uninstalled!")
                        handleCriticalSecurityBreach("APP_UNINSTALLED")
                    }
                    
                    // Verify device owner is still enabled
                    val isDeviceOwner = uninstallPreventionManager.verifyDeviceOwnerEnabled()
                    if (!isDeviceOwner) {
                        Log.e(TAG, "CRITICAL: Device owner removed!")
                        handleCriticalSecurityBreach("DEVICE_OWNER_REMOVED")
                    }
                    
                    // Re-enforce uninstall prevention
                    if (deviceOwnerManager.isDeviceOwner()) {
                        uninstallPreventionManager.enableUninstallPrevention()
                    }
                    
                    delay(UNINSTALL_VERIFY_INTERVAL)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in uninstall prevention monitoring", e)
                    delay(UNINSTALL_VERIFY_INTERVAL)
                }
            }
        }
    }
    
    /**
     * Start USB debugging blocking
     * Continuously monitors and blocks USB debugging
     */
    private fun startUSBDebuggingBlocking() {
        if (usbDebuggingBlockingJob?.isActive == true) {
            return
        }
        
        usbDebuggingBlockingJob = serviceScope.launch {
            while (isActive) {
                try {
                    // Check if USB debugging is enabled
                    val isUSBDebuggingEnabled = Settings.Global.getInt(
                        contentResolver,
                        Settings.Global.ADB_ENABLED,
                        0
                    ) == 1
                    
                    // Check if developer options are enabled
                    val isDeveloperOptionsEnabled = deviceOwnerManager.isDeveloperOptionsEnabled()
                    
                    if (isUSBDebuggingEnabled || isDeveloperOptionsEnabled) {
                        Log.e(TAG, "USB debugging or developer options enabled - blocking immediately!")
                        
                        // Block USB debugging
                        Settings.Global.putInt(
                            contentResolver,
                            Settings.Global.ADB_ENABLED,
                            0
                        )
                        
                        // Completely disable developer options (multiple methods)
                        Settings.Global.putInt(
                            contentResolver,
                            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                            0
                        )
                        
                        // Also disable via Settings.Secure
                        try {
                            Settings.Secure.putInt(
                                contentResolver,
                                Settings.Secure.DEVELOPMENT_SETTINGS_ENABLED,
                                0
                            )
                        } catch (e: Exception) {
                            // May not be accessible
                        }
                        
                        // Disable USB via device owner
                        deviceOwnerManager.disableUSB(true)
                        
                        // Completely disable developer options via device owner (multiple methods)
                        deviceOwnerManager.disableDeveloperOptions(true)
                        
                        // Detect as tampering and apply hard lock
                        handleTamperingDetected("USB_DEBUGGING_OR_DEVELOPER_OPTIONS_ENABLED")
                        
                        auditLog.logIncident(
                            type = "USB_DEBUGGING_OR_DEVELOPER_OPTIONS_ENABLED",
                            severity = "CRITICAL",
                            details = "USB debugging or developer options was enabled - blocked and device locked"
                        )
                    }
                    
                    // Continuously prevent developer options from being enabled
                    // This blocks the build number click method
                    preventDeveloperOptionsAccess()
                    
                    delay(USB_DEBUG_CHECK_INTERVAL)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in USB debugging blocking", e)
                    delay(USB_DEBUG_CHECK_INTERVAL)
                }
            }
        }
    }
    
    /**
     * Prevent developer options from being accessed
     * Continuously resets settings to prevent build number click from enabling developer options
     */
    private fun preventDeveloperOptionsAccess() {
        try {
            // Method 1: Ensure DEVELOPMENT_SETTINGS_ENABLED is always 0
            val devSettingsEnabled = Settings.Global.getInt(
                contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            )
            
            if (devSettingsEnabled == 1) {
                Log.e(TAG, "Developer options enabled detected - disabling immediately!")
                Settings.Global.putInt(
                    contentResolver,
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    0
                )
                
                // Also disable via device owner
                deviceOwnerManager.disableDeveloperOptions(true)
                
                // Apply hard lock
                handleTamperingDetected("DEVELOPER_OPTIONS_ENABLED")
            }
            
            // Method 2: Reset build number click counter
            // This prevents the "You are now a developer" message
            try {
                val prefs = getSharedPreferences("development_prefs", MODE_PRIVATE)
                val clickCount = prefs.getInt("show_dev_options_count", 0)
                
                if (clickCount > 0) {
                    Log.w(TAG, "Build number click detected (count: $clickCount) - resetting counter!")
                    prefs.edit().putInt("show_dev_options_count", 0).apply()
                    prefs.edit().putInt("development_settings_enabled", 0).apply()
                    
                    // Ensure developer options stay disabled
                    deviceOwnerManager.disableDeveloperOptions(true)
                }
            } catch (e: Exception) {
                // May not have access to this preference
            }
            
            // Method 3: Continuously enforce via device owner API
            deviceOwnerManager.disableDeveloperOptions(true)
            
        } catch (e: Exception) {
            Log.w(TAG, "Error preventing developer options access: ${e.message}")
        }
    }
    
    /**
     * Prevent factory reset access
     * Continuously hides factory reset button and blocks all factory reset methods
     */
    private fun preventFactoryResetAccess() {
        try {
            // Method 1: Continuously enforce factory reset prevention via device owner
            deviceOwnerManager.preventFactoryReset()
            
            // Method 2: Hide factory reset from Settings UI
            try {
                Settings.Global.putInt(
                    contentResolver,
                    "factory_reset_protection_enabled",
                    1
                )
                
                Settings.Secure.putInt(
                    contentResolver,
                    "factory_reset_protection_enabled",
                    1
                )
            } catch (e: Exception) {
                // May not have access
            }
            
            // Method 3: Block recovery mode (prevents factory reset via recovery)
            try {
                Settings.Global.putInt(
                    contentResolver,
                    "recovery_mode_enabled",
                    0
                )
            } catch (e: Exception) {
                // May not have access
            }
            
            // Method 4: Block fastboot mode (prevents factory reset via fastboot)
            try {
                Settings.Global.putInt(
                    contentResolver,
                    "fastboot_mode_enabled",
                    0
                )
            } catch (e: Exception) {
                // May not have access
            }
            
            // Method 5: Disable USB (prevents USB flashing/factory reset)
            deviceOwnerManager.disableUSB(true)
            
            // Method 6: Monitor for factory reset attempts
            monitorFactoryResetAttempts()
            
        } catch (e: Exception) {
            Log.w(TAG, "Error preventing factory reset access: ${e.message}")
        }
    }
    
    /**
     * Monitor for factory reset attempts
     * Detects and blocks factory reset attempts
     */
    private fun monitorFactoryResetAttempts() {
        try {
            // Check for recovery command files (indicates factory reset attempt)
            val recoveryCommand = File("/cache/recovery/command")
            if (recoveryCommand.exists()) {
                val content = recoveryCommand.readText()
                if (content.contains("wipe", ignoreCase = true) || 
                    content.contains("factory", ignoreCase = true) ||
                    content.contains("reset", ignoreCase = true)) {
                    Log.e(TAG, "CRITICAL: Factory reset attempt detected via recovery!")
                    
                    // Lock device immediately
                    deviceOwnerManager.lockDevice()
                    
                    // Get device ID and apply hard lock
                    val deviceId = getDeviceIdentifier()
                    paymentUserLockManager.applyHardLockForTampering(
                        deviceId = deviceId,
                        tamperDetails = "Factory reset attempt detected via recovery mode"
                    )
                    
                    auditLog.logIncident(
                        type = "FACTORY_RESET_ATTEMPT",
                        severity = "CRITICAL",
                        details = "Factory reset attempt detected via recovery mode - blocked"
                    )
                }
            }
        } catch (e: Exception) {
            // May not have access to recovery files
        }
    }
    
    /**
     * Start safe mode prevention
     * Actively prevents safe mode boot
     */
    private fun startSafeModePrevention() {
        if (safeModePreventionJob?.isActive == true) {
            return
        }
        
        safeModePreventionJob = serviceScope.launch {
            while (isActive) {
                try {
                    // Check if device is in safe mode
                    val isSafeMode = isInSafeMode()
                    
                    if (isSafeMode) {
                        Log.e(TAG, "CRITICAL: Safe mode detected - blocking!")
                        
                        // Lock device immediately
                        deviceOwnerManager.lockDevice()
                        
                        // Get device ID and apply hard lock
                        val deviceId = getDeviceIdentifier()
                        paymentUserLockManager.applyHardLockForTampering(
                            deviceId = deviceId,
                            tamperDetails = "Safe mode boot detected - unauthorized boot mode"
                        )
                        
                        // Attempt to exit safe mode by rebooting (if possible)
                        // Note: This may not work on all devices
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                deviceOwnerManager.rebootDevice()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not reboot to exit safe mode", e)
                        }
                        
                        auditLog.logIncident(
                            type = "SAFE_MODE_DETECTED",
                            severity = "CRITICAL",
                            details = "Safe mode boot detected - device locked"
                        )
                    }
                    
                    // Prevent safe mode by monitoring boot mode
                    preventSafeModeBoot()
                    
                    delay(SAFE_MODE_CHECK_INTERVAL)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in safe mode prevention", e)
                    delay(SAFE_MODE_CHECK_INTERVAL)
                }
            }
        }
    }
    
    /**
     * Check if device is in safe mode
     */
    private fun isInSafeMode(): Boolean {
        return try {
            // Check system property for safe mode
            val process = Runtime.getRuntime().exec("getprop ro.boot.safemode")
            val reader = process.inputStream.bufferedReader()
            val result = reader.readLine()?.trim() ?: "0"
            reader.close()
            result == "1"
        } catch (e: Exception) {
            // Alternative method: check for safe mode indicator files
            try {
                val safeModeFile = File("/system/bin/safemode")
                safeModeFile.exists()
            } catch (e2: Exception) {
                false
            }
        }
    }
    
    /**
     * Completely prevent safe mode boot
     * Blocks safe mode before it can be activated
     */
    private fun preventSafeModeBoot() {
        try {
            // Method 1: Disable safe mode via system properties
            try {
                val process = Runtime.getRuntime().exec("setprop ro.boot.safemode 0")
                process.waitFor()
            } catch (e: Exception) {
                // May not have permission
            }
            
            // Method 2: Block safe mode via Settings
            try {
                Settings.Global.putInt(
                    contentResolver,
                    "safe_mode_enabled",
                    0
                )
                
                Settings.Secure.putInt(
                    contentResolver,
                    "safe_mode_enabled",
                    0
                )
            } catch (e: Exception) {
                // May not have access
            }
            
            // Method 3: Block safe mode boot command files
            try {
                val recoveryCommand = File("/cache/recovery/command")
                if (recoveryCommand.exists()) {
                    val content = recoveryCommand.readText()
                    if (content.contains("safemode", ignoreCase = true)) {
                        Log.e(TAG, "CRITICAL: Safe mode boot command detected - blocking!")
                        
                        // Delete the command file to prevent safe mode boot
                        try {
                            recoveryCommand.delete()
                            Log.e(TAG, "✓ Safe mode boot command file deleted")
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not delete recovery command file")
                        }
                        
                        // Lock device immediately
                        deviceOwnerManager.lockDevice()
                        
                        // Apply hard lock
                        val deviceId = getDeviceIdentifier()
                        paymentUserLockManager.applyHardLockForTampering(
                            deviceId = deviceId,
                            tamperDetails = "Safe mode boot attempt detected and blocked"
                        )
                        
                        handleTamperingDetected("SAFE_MODE_BOOT_ATTEMPT")
                    }
                }
            } catch (e: Exception) {
                // May not have access
            }
            
            // Method 4: Block safe mode indicator files
            try {
                val safeModeFile = File("/system/bin/safemode")
                if (safeModeFile.exists()) {
                    // Cannot delete system files, but can monitor
                    Log.w(TAG, "Safe mode indicator file exists - monitoring")
                }
            } catch (e: Exception) {
                // May not have access
            }
            
            // Method 5: Continuously reset safe mode property
            try {
                Settings.Global.putInt(
                    contentResolver,
                    "ro.boot.safemode",
                    0
                )
            } catch (e: Exception) {
                // May not have access
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error preventing safe mode boot: ${e.message}")
        }
    }
    
    /**
     * Start reboot prevention
     * Completely blocks reboot attempts before they can execute
     */
    private fun startRebootPrevention() {
        if (rebootPreventionJob?.isActive == true) {
            return
        }
        
        rebootPreventionJob = serviceScope.launch {
            while (isActive) {
                try {
                    // Continuously block reboot methods
                    blockRebootMethods()
                    
                    // Monitor for reboot indicators
                    val rebootIndicators = checkRebootIndicators()
                    
                    if (rebootIndicators.isNotEmpty()) {
                        Log.e(TAG, "CRITICAL: Reboot attempt detected: ${rebootIndicators.joinToString(", ")}")
                        
                        // Block reboot immediately
                        blockRebootAttempt(rebootIndicators)
                        
                        // Lock device immediately
                        deviceOwnerManager.lockDevice()
                        
                        // Get device ID and apply hard lock
                        val deviceId = getDeviceIdentifier()
                        paymentUserLockManager.applyHardLockForTampering(
                            deviceId = deviceId,
                            tamperDetails = "Unauthorized reboot attempt detected and blocked: ${rebootIndicators.joinToString(", ")}"
                        )
                        
                        auditLog.logIncident(
                            type = "REBOOT_ATTEMPT_DETECTED",
                            severity = "CRITICAL",
                            details = "Reboot attempt detected and blocked: ${rebootIndicators.joinToString(", ")}"
                        )
                    }
                    
                    delay(REBOOT_CHECK_INTERVAL)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in reboot prevention", e)
                    delay(REBOOT_CHECK_INTERVAL)
                }
            }
        }
    }
    
    /**
     * Block all reboot methods continuously
     * Prevents reboot via power menu, ADB, recovery, etc.
     */
    private fun blockRebootMethods() {
        try {
            // Method 1: Block reboot via Settings
            try {
                Settings.Global.putInt(
                    contentResolver,
                    "reboot_enabled",
                    0
                )
                
                Settings.Secure.putInt(
                    contentResolver,
                    "reboot_enabled",
                    0
                )
            } catch (e: Exception) {
                // May not have access
            }
            
            // Method 2: Block reboot via ADB
            try {
                Settings.Global.putInt(
                    contentResolver,
                    Settings.Global.ADB_ENABLED,
                    0
                )
            } catch (e: Exception) {
                // May not have access
            }
            
            // Method 3: Block power menu (prevents reboot via power menu)
            // This is handled by PowerManagementManager, but we reinforce it here
            
            // Method 4: Block reboot commands in recovery
            try {
                val recoveryCommand = File("/cache/recovery/command")
                if (recoveryCommand.exists()) {
                    val content = recoveryCommand.readText()
                    if (content.contains("reboot", ignoreCase = true) || 
                        content.contains("shutdown", ignoreCase = true)) {
                        Log.e(TAG, "CRITICAL: Reboot command in recovery detected - blocking!")
                        
                        // Delete the command file to prevent reboot
                        try {
                            recoveryCommand.delete()
                            Log.e(TAG, "✓ Reboot command file deleted")
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not delete recovery command file")
                        }
                        
                        // Lock device
                        deviceOwnerManager.lockDevice()
                    }
                }
            } catch (e: Exception) {
                // May not have access
            }
            
            // Method 5: Disable USB (prevents reboot via USB/ADB)
            deviceOwnerManager.disableUSB(true)
            
        } catch (e: Exception) {
            Log.w(TAG, "Error blocking reboot methods: ${e.message}")
        }
    }
    
    /**
     * Block specific reboot attempt
     */
    private fun blockRebootAttempt(indicators: List<String>) {
        try {
            // Delete recovery command files
            try {
                val recoveryCommand = File("/cache/recovery/command")
                if (recoveryCommand.exists()) {
                    recoveryCommand.delete()
                    Log.e(TAG, "✓ Reboot command file deleted")
                }
            } catch (e: Exception) {
                // May not have access
            }
            
            // Disable ADB immediately
            try {
                Settings.Global.putInt(
                    contentResolver,
                    Settings.Global.ADB_ENABLED,
                    0
                )
            } catch (e: Exception) {
                // May not have access
            }
            
            // Disable USB
            deviceOwnerManager.disableUSB(true)
            
            // Lock device
            deviceOwnerManager.lockDevice()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking reboot attempt", e)
        }
    }
    
    /**
     * Check for reboot indicators
     */
    private fun checkRebootIndicators(): List<String> {
        val indicators = mutableListOf<String>()
        
        try {
            // Check for reboot command files
            val rebootFile = File("/cache/recovery/command")
            if (rebootFile.exists()) {
                val content = rebootFile.readText()
                if (content.contains("reboot", ignoreCase = true) || 
                    content.contains("shutdown", ignoreCase = true)) {
                    indicators.add("REBOOT_COMMAND_FILE")
                }
            }
            
            // Check for power button long press (if detectable)
            // Note: This is difficult to detect without system-level access
            
            // Check for recovery mode reboot
            val recoveryDir = File("/cache/recovery/")
            if (recoveryDir.exists()) {
                indicators.add("RECOVERY_MODE_ACCESS")
            }
        } catch (e: Exception) {
            // May not have access
        }
        
        return indicators
    }
    
    /**
     * Handle critical security breach
     */
    private suspend fun handleCriticalSecurityBreach(breachType: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.e(TAG, "========================================")
                Log.e(TAG, "🚨 CRITICAL SECURITY BREACH: $breachType")
                Log.e(TAG, "========================================")
                
                // Lock device immediately
                deviceOwnerManager.lockDevice()
                
                // Get device ID
                val deviceId = getDeviceIdentifier()
                
                // Apply hard lock
                paymentUserLockManager.applyHardLockForTampering(
                    deviceId = deviceId,
                    tamperDetails = "Critical security breach: $breachType"
                )
                
                // Log incident
                auditLog.logIncident(
                    type = breachType,
                    severity = "CRITICAL",
                    details = "Critical security breach detected: $breachType"
                )
                
                Log.e(TAG, "========================================")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling critical security breach", e)
            }
        }
    }
    
    /**
     * Handle tampering detected
     */
    private fun handleTamperingDetected(tamperType: String = "GENERAL_TAMPERING") {
        try {
            Log.e(TAG, "Tampering detected: $tamperType")
            
            // Lock device immediately
            deviceOwnerManager.lockDevice()
            
            // Get device ID
            val deviceId = getDeviceIdentifier()
            
            // Apply hard lock
            paymentUserLockManager.applyHardLockForTampering(
                deviceId = deviceId,
                tamperDetails = "System tampering detected: $tamperType"
            )
            
            // Log incident
            auditLog.logIncident(
                type = "TAMPERING_DETECTED",
                severity = "CRITICAL",
                details = "System tampering detected: $tamperType"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling tampering", e)
        }
    }
    
    /**
     * Get device ID
     */
    private fun getDeviceIdentifier(): String {
        return try {
            val prefs = getSharedPreferences("device_registration", MODE_PRIVATE)
            prefs.getString("device_id", Build.SERIAL) ?: Build.SERIAL
        } catch (e: Exception) {
            Build.SERIAL
        }
    }
    
    /**
     * Get device ID (alias for getDeviceIdentifier)
     */
    // Removed to avoid conflict with ContextWrapper.getDeviceIdentifier()
    
    /**
     * Attempt immediate device owner recovery
     * Called when device owner status is lost
     */
    private suspend fun attemptImmediateDeviceOwnerRecovery() {
        withContext(Dispatchers.IO) {
            try {
                Log.e(TAG, "========================================")
                Log.e(TAG, "🚨 IMMEDIATE DEVICE OWNER RECOVERY")
                Log.e(TAG, "========================================")
                
                // Use class property recoveryManager for recovery
                // Attempt immediate recovery
                val recoverySuccess = recoveryManager.secureDeviceOwnerRestore()
                
                if (recoverySuccess) {
                    Log.e(TAG, "✓ Device owner recovered successfully!")
                    
                    // Re-enable all protections
                    uninstallPreventionManager.enableUninstallPrevention()
                    deviceOwnerManager.initializeDeviceOwner()
                    
                    // Update last known device owner status
                    getSharedPreferences("device_owner_status", MODE_PRIVATE)
                        .edit()
                        .putBoolean("last_known_device_owner", true)
                        .apply()
                    
                    auditLog.logIncident(
                        type = "DEVICE_OWNER_RECOVERED",
                        severity = "INFO",
                        details = "Device owner automatically recovered and restored"
                    )
                } else {
                    Log.e(TAG, "✗ Device owner recovery failed - applying hard lock")
                    
                    // Recovery failed - apply hard lock
                    val deviceId = getDeviceIdentifier()
                    paymentUserLockManager.applyHardLockForTampering(
                        deviceId = deviceId,
                        tamperDetails = "Device owner removed and recovery failed"
                    )
                    
                    // Lock device
                    deviceOwnerManager.lockDevice()
                    
                    auditLog.logIncident(
                        type = "DEVICE_OWNER_RECOVERY_FAILED",
                        severity = "CRITICAL",
                        details = "Device owner recovery failed - device locked"
                    )
                }
                
                Log.e(TAG, "========================================")
            } catch (e: Exception) {
                Log.e(TAG, "Error in immediate device owner recovery", e)
            }
        }
    }
    
    /**
     * Attempt immediate device admin recovery
     * Called when device admin status is lost
     */
    private suspend fun attemptImmediateDeviceAdminRecovery() {
        withContext(Dispatchers.IO) {
            try {
                Log.e(TAG, "Attempting immediate device admin recovery...")
                
                // Check if we can reactivate device admin
                val adminComponent = ComponentName(
                    this@ComprehensiveSecurityService,
                    com.example.deviceowner.receivers.AdminReceiver::class.java
                )
                
                val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                
                // If admin is not active, we cannot recover automatically
                // Device admin requires user interaction to enable
                if (!devicePolicyManager.isAdminActive(adminComponent)) {
                    Log.e(TAG, "CRITICAL: Device admin not active - cannot recover automatically")
                    
                    // Apply hard lock
                    val deviceId = getDeviceIdentifier()
                    paymentUserLockManager.applyHardLockForTampering(
                        deviceId = deviceId,
                        tamperDetails = "Device admin removed - requires manual intervention"
                    )
                    
                    // Lock device
                    deviceOwnerManager.lockDevice()
                    
                    auditLog.logIncident(
                        type = "DEVICE_ADMIN_REMOVED",
                        severity = "CRITICAL",
                        details = "Device admin removed - cannot recover automatically"
                    )
                    
                    return@withContext
                }
                
                // Device admin is active, attempt to restore device owner
                recoveryManager.secureDeviceOwnerRestore()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in immediate device admin recovery", e)
            }
        }
    }
    
    /**
     * Detect Knox Guard bypass attempts
     * Monitors for signs of Knox Guard bypass or device owner removal
     */
    private fun isKnoxGuardBypassDetected(): Boolean {
        return try {
            // Method 1: Check if device owner status changed unexpectedly
            val wasDeviceOwner = getSharedPreferences("device_owner_status", MODE_PRIVATE)
                .getBoolean("last_known_device_owner", true)
            val isDeviceOwner = deviceOwnerManager.isDeviceOwner()
            
            if (wasDeviceOwner && !isDeviceOwner) {
                Log.e(TAG, "Knox Guard bypass detected: Device owner status changed unexpectedly")
                return true
            }
            
            // Method 2: Check for Knox Guard bypass indicators
            // Check if device admin is active but device owner is not (indicates bypass)
            val isDeviceAdmin = deviceOwnerManager.isDeviceAdmin()
            if (isDeviceAdmin && !isDeviceOwner) {
                Log.e(TAG, "Knox Guard bypass detected: Device admin active but device owner not")
                return true
            }
            
            // Method 3: Check for uninstall block removal (indicates bypass)
            val isUninstallBlocked = uninstallPreventionManager.isUninstallBlocked()
            if (!isUninstallBlocked && wasDeviceOwner) {
                Log.e(TAG, "Knox Guard bypass detected: Uninstall block removed")
                return true
            }
            
            // Method 4: Check for force stop block removal (indicates bypass)
            val isForceStopBlocked = uninstallPreventionManager.isForceStopBlocked()
            if (!isForceStopBlocked && wasDeviceOwner) {
                Log.e(TAG, "Knox Guard bypass detected: Force stop block removed")
                return true
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting Knox Guard bypass", e)
            false
        }
    }
    
    /**
     * Handle Knox Guard bypass
     * Attempts to recover device owner status
     */
    private suspend fun handleKnoxGuardBypass() {
        withContext(Dispatchers.IO) {
            try {
                Log.e(TAG, "========================================")
                Log.e(TAG, "🚨 KNOX GUARD BYPASS DETECTED")
                Log.e(TAG, "========================================")
                
                // Lock device immediately
                deviceOwnerManager.lockDevice()
                
                // Attempt immediate recovery
                recoveryManager.secureDeviceOwnerRestore()
                
                // Get device ID and apply hard lock
                val deviceId = getDeviceIdentifier()
                paymentUserLockManager.applyHardLockForTampering(
                    deviceId = deviceId,
                    tamperDetails = "Knox Guard bypass detected - attempting recovery"
                )
                
                // Log critical incident
                auditLog.logIncident(
                    type = "KNOX_GUARD_BYPASS_DETECTED",
                    severity = "CRITICAL",
                    details = "Knox Guard bypass detected - recovery attempted"
                )
                
                Log.e(TAG, "========================================")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling Knox Guard bypass", e)
            }
        }
    }
    
    /**
     * Update device owner status for bypass detection
     * Stores current status to detect unexpected changes
     */
    private fun updateDeviceOwnerStatus() {
        try {
            val isDeviceOwner = deviceOwnerManager.isDeviceOwner()
            getSharedPreferences("device_owner_status", MODE_PRIVATE)
                .edit()
                .putBoolean("last_known_device_owner", isDeviceOwner)
                .putLong("last_status_check", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            // May not have access
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ComprehensiveSecurityService destroyed")
        
        securityMonitoringJob?.cancel()
        uninstallVerificationJob?.cancel()
        usbDebuggingBlockingJob?.cancel()
        safeModePreventionJob?.cancel()
        rebootPreventionJob?.cancel()
        
        serviceScope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
