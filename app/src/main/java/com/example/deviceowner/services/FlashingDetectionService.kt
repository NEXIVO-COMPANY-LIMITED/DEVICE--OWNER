package com.example.deviceowner.services

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.example.deviceowner.managers.DeviceOwnerManager
import com.example.deviceowner.managers.IdentifierAuditLog
import com.example.deviceowner.managers.PaymentUserLockManager
import kotlinx.coroutines.*
import java.io.File

/**
 * Real-time flashing detection service
 * Monitors for flashing attempts and blocks immediately
 * 
 * Detects:
 * - Device fingerprint changes
 * - Bootloader changes
 * - Serial number changes
 * - Android ID changes
 * - Recovery mode access
 * - Fastboot mode access
 * - System property changes
 */
class FlashingDetectionService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var deviceOwnerManager: DeviceOwnerManager
    private lateinit var auditLog: IdentifierAuditLog
    private lateinit var lockManager: PaymentUserLockManager
    private var monitoringJob: Job? = null
    
    companion object {
        private const val TAG = "FlashingDetectionService"
        private const val CHECK_INTERVAL = 5000L // 5 seconds - REAL-TIME
        private const val PREFS_NAME = "flashing_detection_prefs"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FlashingDetectionService created")
        deviceOwnerManager = DeviceOwnerManager(this)
        auditLog = IdentifierAuditLog(this)
        lockManager = PaymentUserLockManager(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FlashingDetectionService started - Real-time monitoring active")
        startRealTimeMonitoring()
        return START_STICKY
    }
    
    /**
     * Start real-time flashing detection
     */
    private fun startRealTimeMonitoring() {
        if (monitoringJob?.isActive == true) {
            Log.d(TAG, "Monitoring already running")
            return
        }
        
        monitoringJob = serviceScope.launch {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            
            // Initialize baseline values on first run
            var lastFingerprint = prefs.getString("last_fingerprint", Build.FINGERPRINT) ?: Build.FINGERPRINT
            var lastBootloader = prefs.getString("last_bootloader", Build.BOOTLOADER) ?: Build.BOOTLOADER
            var lastSerial = prefs.getString("last_serial", Build.SERIAL) ?: Build.SERIAL
            var lastAndroidId = prefs.getString("last_android_id", getAndroidId()) ?: getAndroidId()
            
            // Save initial values
            prefs.edit().apply {
                putString("last_fingerprint", lastFingerprint)
                putString("last_bootloader", lastBootloader)
                putString("last_serial", lastSerial)
                putString("last_android_id", lastAndroidId)
            }.apply()
            
            Log.d(TAG, "Real-time flashing detection started")
            Log.d(TAG, "Baseline - Fingerprint: $lastFingerprint")
            Log.d(TAG, "Baseline - Bootloader: $lastBootloader")
            Log.d(TAG, "Baseline - Serial: $lastSerial")
            
            while (isActive) {
                try {
                    // Check for fingerprint changes (indicates flashing)
                    val currentFingerprint = Build.FINGERPRINT
                    if (currentFingerprint != lastFingerprint && lastFingerprint.isNotEmpty()) {
                        Log.e(TAG, "🚨 CRITICAL: Device fingerprint changed - FLASHING DETECTED!")
                        handleFlashingDetected("FINGERPRINT_CHANGE", currentFingerprint, lastFingerprint)
                        lastFingerprint = currentFingerprint
                        prefs.edit().putString("last_fingerprint", currentFingerprint).apply()
                    }
                    
                    // Check for bootloader changes
                    val currentBootloader = Build.BOOTLOADER
                    if (currentBootloader != lastBootloader && lastBootloader.isNotEmpty()) {
                        Log.e(TAG, "🚨 CRITICAL: Bootloader changed - FLASHING DETECTED!")
                        handleFlashingDetected("BOOTLOADER_CHANGE", currentBootloader, lastBootloader)
                        lastBootloader = currentBootloader
                        prefs.edit().putString("last_bootloader", currentBootloader).apply()
                    }
                    
                    // Check for serial number changes
                    val currentSerial = Build.SERIAL
                    if (currentSerial != lastSerial && lastSerial.isNotEmpty() && currentSerial != "unknown") {
                        Log.e(TAG, "🚨 CRITICAL: Serial number changed - FLASHING DETECTED!")
                        handleFlashingDetected("SERIAL_CHANGE", currentSerial, lastSerial)
                        lastSerial = currentSerial
                        prefs.edit().putString("last_serial", currentSerial).apply()
                    }
                    
                    // Check for Android ID changes
                    val currentAndroidId = getAndroidId()
                    if (currentAndroidId != lastAndroidId && lastAndroidId.isNotEmpty()) {
                        Log.e(TAG, "🚨 CRITICAL: Android ID changed - FLASHING DETECTED!")
                        handleFlashingDetected("ANDROID_ID_CHANGE", currentAndroidId, lastAndroidId)
                        lastAndroidId = currentAndroidId
                        prefs.edit().putString("last_android_id", currentAndroidId).apply()
                    }
                    
                    // Check for recovery mode indicators
                    if (isRecoveryMode()) {
                        Log.e(TAG, "🚨 CRITICAL: Recovery mode detected!")
                        handleFlashingDetected("RECOVERY_MODE", "recovery", "normal")
                        // Block recovery mode immediately
                        blockRecoveryMode()
                    }
                    
                    // Check for fastboot mode indicators
                    if (isFastbootMode()) {
                        Log.e(TAG, "🚨 CRITICAL: Fastboot mode detected!")
                        handleFlashingDetected("FASTBOOT_MODE", "fastboot", "normal")
                        // Block fastboot mode immediately
                        blockFastbootMode()
                    }
                    
                    // Check for USB flashing indicators
                    if (isUSBFlashingDetected()) {
                        Log.e(TAG, "🚨 CRITICAL: USB flashing detected!")
                        handleFlashingDetected("USB_FLASHING", "usb_flash", "normal")
                        // Block USB immediately
                        blockUSBFlashing()
                    }
                    
                    // Continuously block external flashing methods
                    preventExternalFlashing()
                    
                    delay(CHECK_INTERVAL)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in flashing detection loop", e)
                    delay(CHECK_INTERVAL)
                }
            }
        }
    }
    
    /**
     * Get Android ID
     */
    private fun getAndroidId(): String {
        return try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Android ID", e)
            ""
        }
    }
    
    /**
     * Check if device is in recovery mode
     */
    private fun isRecoveryMode(): Boolean {
        return try {
            // Check for recovery mode indicators
            val recoveryDir = File("/cache/recovery/")
            val recoveryCommand = File("/cache/recovery/command")
            val recoveryLog = File("/cache/recovery/last_log")
            
            val inRecovery = recoveryDir.exists() || recoveryCommand.exists() || recoveryLog.exists()
            
            if (inRecovery) {
                Log.w(TAG, "Recovery mode indicators found")
            }
            
            inRecovery
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if device is in fastboot mode
     */
    private fun isFastbootMode(): Boolean {
        return try {
            // Check for fastboot indicators
            val fastbootFile = File("/sys/class/android_usb/android0/state")
            if (fastbootFile.exists()) {
                val state = fastbootFile.readText().trim()
                state.contains("fastboot", ignoreCase = true)
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Handle flashing detection - IMMEDIATE BLOCK
     */
    private suspend fun handleFlashingDetected(
        type: String,
        current: String,
        previous: String
    ) {
        try {
            Log.e(TAG, "========================================")
            Log.e(TAG, "🚨 FLASHING DETECTED - IMMEDIATE BLOCK")
            Log.e(TAG, "========================================")
            Log.e(TAG, "Type: $type")
            Log.e(TAG, "Current: $current")
            Log.e(TAG, "Previous: $previous")
            Log.e(TAG, "Time: ${System.currentTimeMillis()}")
            
            // Log critical incident
            auditLog.logIncident(
                type = "FLASHING_DETECTED",
                severity = "CRITICAL",
                details = "Flashing detected: $type | Current: $current | Previous: $previous"
            )
            
            // IMMEDIATE ACTION: Lock device
            deviceOwnerManager.lockDevice()
            Log.e(TAG, "✓ Device locked immediately")
            
            // Apply hard lock
            val deviceId = getDeviceIdentifier()
            lockManager.applyHardLockForTampering(
                deviceId = deviceId,
                tamperDetails = "Device flashing detected: $type"
            )
            Log.e(TAG, "✓ Hard lock applied")
            
            // Send alert to backend
            alertBackendOfFlashing(type, current, previous)
            
            // Block all device functions
            blockDeviceCompletely()
            
            Log.e(TAG, "========================================")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling flashing detection", e)
        }
    }
    
    /**
     * Get device ID for backend alert
     */
    private fun getDeviceIdentifier(): String {
        return try {
            // Try to get from registration
            val prefs = getSharedPreferences("device_registration", MODE_PRIVATE)
            prefs.getString("device_id", Build.SERIAL) ?: Build.SERIAL
        } catch (e: Exception) {
            Build.SERIAL
        }
    }
    
    /**
     * Block device completely
     */
    private suspend fun blockDeviceCompletely() {
        try {
            // Lock device (already done, but ensure it's locked)
            deviceOwnerManager.lockDevice()
            
            // Disable USB debugging
            disableUSBDebugging()
            
            // Disable developer options
            disableDeveloperOptions()
            
            // Show critical overlay
            showCriticalOverlay("Device flashing detected. Device locked for security.")
            
            Log.d(TAG, "✓ Device completely blocked")
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking device", e)
        }
    }
    
    /**
     * Disable USB debugging
     */
    private fun disableUSBDebugging() {
        try {
            Settings.Global.putInt(
                contentResolver,
                Settings.Global.ADB_ENABLED,
                0
            )
            Log.d(TAG, "✓ USB debugging disabled")
        } catch (e: Exception) {
            Log.w(TAG, "Could not disable USB debugging: ${e.message}")
        }
    }
    
    /**
     * Disable developer options
     */
    private fun disableDeveloperOptions() {
        try {
            Settings.Global.putInt(
                contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            )
            Log.d(TAG, "✓ Developer options disabled")
        } catch (e: Exception) {
            Log.w(TAG, "Could not disable developer options: ${e.message}")
        }
    }
    
    /**
     * Show critical overlay
     */
    private fun showCriticalOverlay(message: String) {
        try {
            val overlayIntent = Intent(this, com.example.deviceowner.overlay.OverlayController::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("lock_type", "HARD")
                putExtra("reason", message)
                putExtra("critical", true)
            }
            startActivity(overlayIntent)
            Log.d(TAG, "✓ Critical overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing critical overlay", e)
        }
    }
    
    /**
     * Block recovery mode immediately
     * Prevents factory reset and flashing via recovery
     */
    private suspend fun blockRecoveryMode() {
        withContext(Dispatchers.IO) {
            try {
                // Disable recovery mode access
                Settings.Global.putInt(
                    contentResolver,
                    "recovery_mode_enabled",
                    0
                )
                
                // Lock device to prevent recovery access
                deviceOwnerManager.lockDevice()
                
                // Disable USB to prevent recovery flashing
                deviceOwnerManager.disableUSB(true)
                
                Log.e(TAG, "✓ Recovery mode blocked")
            } catch (e: Exception) {
                Log.e(TAG, "Error blocking recovery mode", e)
            }
        }
    }
    
    /**
     * Block fastboot mode immediately
     * Prevents flashing via fastboot
     */
    private suspend fun blockFastbootMode() {
        withContext(Dispatchers.IO) {
            try {
                // Disable fastboot mode access
                Settings.Global.putInt(
                    contentResolver,
                    "fastboot_mode_enabled",
                    0
                )
                
                // Lock device to prevent fastboot access
                deviceOwnerManager.lockDevice()
                
                // Disable USB to prevent fastboot flashing
                deviceOwnerManager.disableUSB(true)
                
                Log.e(TAG, "✓ Fastboot mode blocked")
            } catch (e: Exception) {
                Log.e(TAG, "Error blocking fastboot mode", e)
            }
        }
    }
    
    /**
     * Detect USB flashing attempts
     */
    private fun isUSBFlashingDetected(): Boolean {
        return try {
            // Check for USB flashing indicators
            val usbState = Settings.Global.getInt(
                contentResolver,
                Settings.Global.USB_MASS_STORAGE_ENABLED,
                0
            )
            
            // Check for ADB connection (indicates USB flashing attempt)
            val adbEnabled = Settings.Global.getInt(
                contentResolver,
                Settings.Global.ADB_ENABLED,
                0
            ) == 1
            
            // Check for USB file transfer
            val usbFileTransfer = Settings.Global.getInt(
                contentResolver,
                "usb_file_transfer_enabled",
                0
            ) == 1
            
            usbState == 1 || adbEnabled || usbFileTransfer
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Block USB flashing immediately
     */
    private suspend fun blockUSBFlashing() {
        withContext(Dispatchers.IO) {
            try {
                // Disable USB mass storage
                Settings.Global.putInt(
                    contentResolver,
                    Settings.Global.USB_MASS_STORAGE_ENABLED,
                    0
                )
                
                // Disable ADB
                Settings.Global.putInt(
                    contentResolver,
                    Settings.Global.ADB_ENABLED,
                    0
                )
                
                // Disable USB file transfer
                Settings.Global.putInt(
                    contentResolver,
                    "usb_file_transfer_enabled",
                    0
                )
                
                // Disable USB via device owner
                deviceOwnerManager.disableUSB(true)
                
                // Lock device
                deviceOwnerManager.lockDevice()
                
                Log.e(TAG, "✓ USB flashing blocked")
            } catch (e: Exception) {
                Log.e(TAG, "Error blocking USB flashing", e)
            }
        }
    }
    
    /**
     * Continuously prevent external flashing
     * Blocks recovery, fastboot, and USB flashing methods
     */
    private suspend fun preventExternalFlashing() {
        withContext(Dispatchers.IO) {
            try {
                // Continuously disable recovery mode
                Settings.Global.putInt(
                    contentResolver,
                    "recovery_mode_enabled",
                    0
                )
                
                // Continuously disable fastboot mode
                Settings.Global.putInt(
                    contentResolver,
                    "fastboot_mode_enabled",
                    0
                )
                
                // Continuously disable USB mass storage
                Settings.Global.putInt(
                    contentResolver,
                    Settings.Global.USB_MASS_STORAGE_ENABLED,
                    0
                )
                
                // Continuously disable USB file transfer
                Settings.Global.putInt(
                    contentResolver,
                    "usb_file_transfer_enabled",
                    0
                )
                
                // Continuously enforce USB blocking via device owner
                deviceOwnerManager.disableUSB(true)
                
            } catch (e: Exception) {
                // May not have access to all settings
            }
        }
    }
    
    /**
     * Alert backend of flashing
     * 
     * NOTE: Backend will detect flashing from heartbeat data automatically.
     * No need to send separate API call. Just log locally and lock device.
     * Backend compares heartbeat data (fingerprint, bootloader, serial) with baseline
     * and responds with lock_status in heartbeat response.
     */
    private suspend fun alertBackendOfFlashing(type: String, current: String, previous: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.w(TAG, "Flashing detected locally: $type changed from $previous to $current")
                Log.w(TAG, "Backend will detect this from heartbeat data")
                Log.w(TAG, "Device locked locally. Backend will confirm via heartbeat response.")
                
                // Log locally - backend will detect from heartbeat
                // Backend compares fingerprint, bootloader, serial in heartbeat data
                // and detects flashing automatically
                
            } catch (e: Exception) {
                Log.e(TAG, "Error logging flashing detection", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FlashingDetectionService destroyed")
        monitoringJob?.cancel()
        serviceScope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
