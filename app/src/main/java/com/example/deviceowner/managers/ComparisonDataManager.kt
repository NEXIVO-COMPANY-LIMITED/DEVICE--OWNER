package com.example.deviceowner.managers

import android.util.Log

/**
 * Manages comparison of current device data with registration data
 * Uses both local database and backend data for verification
 */
class ComparisonDataManager(
    private val registrationDataManager: RegistrationDataManager,
    private val heartbeatDataManager: HeartbeatDataManager
) {
    
    companion object {
        private const val TAG = "ComparisonDataManager"
    }
    
    /**
     * Compare current heartbeat data with registration data
     * Returns list of mismatches detected
     */
    suspend fun compareWithRegistration(): List<ComparisonMismatch> {
        val currentData = heartbeatDataManager.collectHeartbeatData()
        val registrationData = registrationDataManager.getRegistrationData() ?: return emptyList()
        
        val mismatches = mutableListOf<ComparisonMismatch>()
        
        // Compare critical identifiers
        if (currentData.androidId != registrationData.android_id) {
            mismatches.add(ComparisonMismatch(
                field = "Android ID",
                registeredValue = registrationData.android_id,
                currentValue = currentData.androidId,
                severity = "CRITICAL",
                type = "DEVICE_SWAP"
            ))
        }
        
        if (currentData.deviceFingerprint != registrationData.device_fingerprint) {
            mismatches.add(ComparisonMismatch(
                field = "Device Fingerprint",
                registeredValue = registrationData.device_fingerprint,
                currentValue = currentData.deviceFingerprint,
                severity = "CRITICAL",
                type = "DEVICE_SWAP"
            ))
        }
        
        // Compare hardware
        if (currentData.bootloader != registrationData.bootloader) {
            mismatches.add(ComparisonMismatch(
                field = "Bootloader",
                registeredValue = registrationData.bootloader,
                currentValue = currentData.bootloader,
                severity = "CRITICAL",
                type = "BOOTLOADER_CHANGE"
            ))
        }
        
        if (currentData.hardware != registrationData.hardware) {
            mismatches.add(ComparisonMismatch(
                field = "Hardware",
                registeredValue = registrationData.hardware,
                currentValue = currentData.hardware,
                severity = "HIGH",
                type = "HARDWARE_CHANGE"
            ))
        }
        
        if (currentData.product != registrationData.product) {
            mismatches.add(ComparisonMismatch(
                field = "Product",
                registeredValue = registrationData.product,
                currentValue = currentData.product,
                severity = "HIGH",
                type = "DEVICE_CHANGE"
            ))
        }
        
        // Compare tamper status
        if (currentData.isDeviceRooted != registrationData.is_device_rooted) {
            mismatches.add(ComparisonMismatch(
                field = "Root Status",
                registeredValue = registrationData.is_device_rooted.toString(),
                currentValue = currentData.isDeviceRooted.toString(),
                severity = "CRITICAL",
                type = "ROOT_DETECTED"
            ))
        }
        
        if (currentData.isBootloaderUnlocked != registrationData.is_bootloader_unlocked) {
            mismatches.add(ComparisonMismatch(
                field = "Bootloader Unlock",
                registeredValue = registrationData.is_bootloader_unlocked.toString(),
                currentValue = currentData.isBootloaderUnlocked.toString(),
                severity = "CRITICAL",
                type = "BOOTLOADER_UNLOCKED"
            ))
        }
        
        if (currentData.isCustomROM != registrationData.is_custom_rom) {
            mismatches.add(ComparisonMismatch(
                field = "Custom ROM",
                registeredValue = registrationData.is_custom_rom.toString(),
                currentValue = currentData.isCustomROM.toString(),
                severity = "CRITICAL",
                type = "CUSTOM_ROM_DETECTED"
            ))
        }
        
        if (currentData.isUSBDebuggingEnabled != registrationData.is_usb_debugging_enabled) {
            mismatches.add(ComparisonMismatch(
                field = "USB Debugging",
                registeredValue = registrationData.is_usb_debugging_enabled.toString(),
                currentValue = currentData.isUSBDebuggingEnabled.toString(),
                severity = "HIGH",
                type = "USB_DEBUG_ENABLED"
            ))
        }
        
        if (currentData.isDeveloperModeEnabled != registrationData.is_developer_mode_enabled) {
            mismatches.add(ComparisonMismatch(
                field = "Developer Mode",
                registeredValue = registrationData.is_developer_mode_enabled.toString(),
                currentValue = currentData.isDeveloperModeEnabled.toString(),
                severity = "HIGH",
                type = "DEV_MODE_ENABLED"
            ))
        }
        
        // Compare app integrity
        if (currentData.installedAppsHash != registrationData.installed_apps_hash) {
            mismatches.add(ComparisonMismatch(
                field = "Installed Apps",
                registeredValue = registrationData.installed_apps_hash,
                currentValue = currentData.installedAppsHash,
                severity = "MEDIUM",
                type = "APPS_MODIFIED"
            ))
        }
        
        // Compare system properties
        if (currentData.systemPropertiesHash != registrationData.system_properties_hash) {
            mismatches.add(ComparisonMismatch(
                field = "System Properties",
                registeredValue = registrationData.system_properties_hash,
                currentValue = currentData.systemPropertiesHash,
                severity = "MEDIUM",
                type = "SYSTEM_MODIFIED"
            ))
        }
        
        if (mismatches.isNotEmpty()) {
            Log.w(TAG, "Found ${mismatches.size} mismatches during comparison")
        }
        
        return mismatches
    }
    
    /**
     * Get comparison summary for display
     */
    suspend fun getComparisonSummary(): ComparisonSummary {
        val mismatches = compareWithRegistration()
        val registrationData = registrationDataManager.getRegistrationData()
        
        return ComparisonSummary(
            deviceId = registrationData?.device_id ?: "Unknown",
            manufacturer = registrationData?.manufacturer ?: "Unknown",
            model = registrationData?.model ?: "Unknown",
            osVersion = registrationData?.os_version ?: "Unknown",
            registrationTime = registrationData?.registration_timestamp ?: 0L,
            totalMismatches = mismatches.size,
            criticalMismatches = mismatches.count { it.severity == "CRITICAL" },
            highMismatches = mismatches.count { it.severity == "HIGH" },
            mediumMismatches = mismatches.count { it.severity == "MEDIUM" },
            isTrusted = mismatches.isEmpty(),
            mismatches = mismatches
        )
    }
}

/**
 * Data class for comparison mismatch
 */
data class ComparisonMismatch(
    val field: String,
    val registeredValue: String,
    val currentValue: String,
    val severity: String, // CRITICAL, HIGH, MEDIUM
    val type: String, // DEVICE_SWAP, ROOT_DETECTED, etc.
    val detectedAt: Long = System.currentTimeMillis()
)

/**
 * Data class for comparison summary
 * Used for display purposes - shows only essential information
 */
data class ComparisonSummary(
    val deviceId: String,
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val registrationTime: Long,
    val totalMismatches: Int,
    val criticalMismatches: Int,
    val highMismatches: Int,
    val mediumMismatches: Int,
    val isTrusted: Boolean,
    val mismatches: List<ComparisonMismatch> = emptyList()
)
