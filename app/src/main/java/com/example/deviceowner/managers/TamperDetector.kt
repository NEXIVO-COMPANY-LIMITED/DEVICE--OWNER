package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest

/**
 * Consolidated tamper detection system
 * Detects various forms of device tampering and modifications:
 * 1. Device-level tampering (root, bootloader, custom ROM, etc.)
 * 2. Local data tampering (SharedPreferences, cache files)
 * 3. Backend data tampering (verification data mismatch)
 */
class TamperDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "TamperDetector"
        private const val PREFS_NAME = "tamper_detector_prefs"
        private const val KEY_DATA_INTEGRITY_HASH = "data_integrity_hash"
        private const val KEY_LAST_VERIFIED_HASH = "last_verified_hash"
        private const val KEY_STORAGE_INTEGRITY = "storage_integrity"
    }
    
    private val auditLog = IdentifierAuditLog(context)
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Protected cache directory for integrity checks
    private val protectedCacheDir: File by lazy {
        val cacheDir = File(context.cacheDir, "tamper_detection_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            cacheDir.setReadable(false, false)
            cacheDir.setReadable(true, true)
            cacheDir.setWritable(false, false)
            cacheDir.setWritable(true, true)
        }
        cacheDir
    }
    
    /**
     * Detect root access using multiple methods
     * ALIGNED WITH: HeartbeatDataManager.isDeviceRooted()
     */
    fun isRooted(): Boolean {
        return try {
            // Check for su binary in common locations (same as HeartbeatDataManager)
            val paths = arrayOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
            )
            
            val isRooted = paths.any { File(it).exists() }
            
            if (isRooted) {
                Log.w(TAG, "Root detected: su binary found")
                auditLog.logIncident(
                    type = "ROOT_DETECTED",
                    severity = "CRITICAL",
                    details = "Device root access detected via su binary"
                )
            }
            
            isRooted
        } catch (e: Exception) {
            Log.e(TAG, "Error checking root", e)
            false
        }
    }
    
    /**
     * Detect USB debugging enabled
     * ALIGNED WITH: HeartbeatDataManager.isUSBDebuggingEnabled()
     */
    fun isUSBDebuggingEnabled(): Boolean {
        return try {
            val isEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED,
                0
            ) == 1
            
            if (isEnabled) {
                Log.w(TAG, "USB debugging enabled")
                auditLog.logIncident(
                    type = "USB_DEBUG_ENABLED",
                    severity = "HIGH",
                    details = "USB debugging is enabled on device"
                )
            }
            
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking USB debugging", e)
            false
        }
    }
    
    /**
     * Detect developer mode enabled
     * ALIGNED WITH: HeartbeatDataManager.isDeveloperModeEnabled()
     */
    fun isDeveloperModeEnabled(): Boolean {
        return try {
            val isEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
            
            if (isEnabled) {
                Log.w(TAG, "Developer mode enabled")
                auditLog.logIncident(
                    type = "DEV_MODE_ENABLED",
                    severity = "HIGH",
                    details = "Developer mode is enabled on device"
                )
            }
            
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking developer mode", e)
            false
        }
    }
    
    /**
     * Detect bootloader unlock
     * NEW DETECTION: Not in HeartbeatDataManager
     */
    fun isBootloaderUnlocked(): Boolean {
        return try {
            val bootloader = Build.BOOTLOADER
            val fingerprint = Build.FINGERPRINT
            val tags = Build.TAGS
            
            // Check for bootloader unlock indicators
            val isUnlocked = bootloader.contains("UNLOCKED", ignoreCase = true) ||
                    bootloader.contains("OPEN", ignoreCase = true) ||
                    fingerprint.contains("test-keys") ||
                    (tags != null && tags.contains("test-keys"))
            
            if (isUnlocked) {
                Log.w(TAG, "Bootloader unlock detected")
                auditLog.logIncident(
                    type = "BOOTLOADER_UNLOCKED",
                    severity = "CRITICAL",
                    details = "Device bootloader is unlocked - Bootloader: $bootloader"
                )
            }
            
            isUnlocked
        } catch (e: Exception) {
            Log.e(TAG, "Error checking bootloader", e)
            false
        }
    }
    
    /**
     * Detect custom ROM installation
     * NEW DETECTION: Not in HeartbeatDataManager
     */
    fun isCustomROM(): Boolean {
        return try {
            val fingerprint = Build.FINGERPRINT
            val tags = Build.TAGS
            val buildType = Build.TYPE
            
            // Check for custom ROM indicators
            val isCustom = !fingerprint.contains("official") ||
                    (tags != null && tags.contains("test-keys")) ||
                    buildType == "userdebug"
            
            if (isCustom) {
                Log.w(TAG, "Custom ROM detected: $fingerprint")
                auditLog.logIncident(
                    type = "CUSTOM_ROM_DETECTED",
                    severity = "HIGH",
                    details = "Custom ROM detected - Fingerprint: $fingerprint, Type: $buildType"
                )
            }
            
            isCustom
        } catch (e: Exception) {
            Log.e(TAG, "Error checking ROM", e)
            false
        }
    }
    
    /**
     * Detect local data tampering
     * Checks if SharedPreferences or cache files have been modified
     */
    fun isLocalDataTampered(): Boolean {
        return try {
            // Check SharedPreferences integrity
            val sharedPrefsNames = listOf(
                "heartbeat_data",
                "identifier_audit_log",
                "jwt_manager",
                "replay_protection",
                "pin_verification",
                "device_registration"
            )
            
            var isTampered = false
            
            for (prefName in sharedPrefsNames) {
                try {
                    val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    val currentHash = calculatePreferencesHash(prefs)
                    val storedHash = prefs.getString("${prefName}_integrity_hash", "") ?: ""
                    
                    if (storedHash.isNotEmpty() && currentHash != storedHash) {
                        Log.w(TAG, "Local data tampering detected in $prefName")
                        auditLog.logIncident(
                            type = "LOCAL_DATA_TAMPERED",
                            severity = "HIGH",
                            details = "SharedPreferences $prefName integrity check failed"
                        )
                        isTampered = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking preferences $prefName", e)
                }
            }
            
            // Check cache file integrity
            if (isProtectedCacheTampered()) {
                Log.w(TAG, "Protected cache files tampered")
                auditLog.logIncident(
                    type = "CACHE_TAMPERED",
                    severity = "HIGH",
                    details = "Protected cache files have been modified or deleted"
                )
                isTampered = true
            }
            
            isTampered
        } catch (e: Exception) {
            Log.e(TAG, "Error checking local data tampering", e)
            false
        }
    }
    
    /**
     * Detect backend data tampering
     * Compares current device data with backend-verified data
     */
    fun isBackendDataTampered(): Boolean {
        return try {
            val heartbeatManager = HeartbeatDataManager(context)
            val currentData = null // Will be set in async context
            val verifiedData = heartbeatManager.getLastVerifiedData()
            
            if (verifiedData == null) {
                Log.d(TAG, "No verified data available for comparison")
                return false
            }
            
            // Check critical identifiers against backend data
            val criticalFields = listOf(
                "androidId" to verifiedData.androidId,
                "deviceFingerprint" to verifiedData.deviceFingerprint,
                "bootloader" to verifiedData.bootloader,
                "hardware" to verifiedData.hardware,
                "product" to verifiedData.product,
                "device" to verifiedData.device,
                "brand" to verifiedData.brand
            )
            
            var isTampered = false
            
            for ((fieldName, storedValue) in criticalFields) {
                val currentValue = when (fieldName) {
                    "androidId" -> getAndroidId()
                    "deviceFingerprint" -> Build.FINGERPRINT
                    "bootloader" -> Build.BOOTLOADER
                    "hardware" -> Build.HARDWARE
                    "product" -> Build.PRODUCT
                    "device" -> Build.DEVICE
                    "brand" -> Build.BRAND
                    else -> ""
                }
                
                if (currentValue != storedValue && storedValue.isNotEmpty()) {
                    Log.w(TAG, "Backend data mismatch: $fieldName")
                    auditLog.logIncident(
                        type = "BACKEND_DATA_MISMATCH",
                        severity = "CRITICAL",
                        details = "Field $fieldName changed: stored=$storedValue, current=$currentValue"
                    )
                    isTampered = true
                }
            }
            
            isTampered
        } catch (e: Exception) {
            Log.e(TAG, "Error checking backend data tampering", e)
            false
        }
    }
    
    /**
     * Check if protected cache files have been tampered with
     */
    private fun isProtectedCacheTampered(): Boolean {
        return try {
            val heartbeatCacheDir = File(context.cacheDir, "protected_heartbeat_cache")
            val updatesCacheDir = File(context.cacheDir, "protected_updates")
            
            // Check if cache directories exist and have expected files
            val expectedFiles = listOf(
                File(heartbeatCacheDir, "hb_data.cache"),
                File(heartbeatCacheDir, "hb_verified.cache"),
                File(heartbeatCacheDir, "hb_history.cache")
            )
            
            // If any expected file is missing, it might indicate tampering
            val missingFiles = expectedFiles.filter { !it.exists() }
            
            if (missingFiles.isNotEmpty()) {
                Log.w(TAG, "Missing cache files: ${missingFiles.map { it.name }}")
                return true
            }
            
            // Check file permissions
            for (file in expectedFiles) {
                if (file.exists()) {
                    val canRead = file.canRead()
                    val canWrite = file.canWrite()
                    
                    // Files should be readable and writable by app only
                    if (!canRead || !canWrite) {
                        Log.w(TAG, "Cache file permissions tampered: ${file.name}")
                        return true
                    }
                }
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking cache tampering", e)
            false
        }
    }
    
    /**
     * Calculate hash of SharedPreferences data for integrity checking
     */
    private fun calculatePreferencesHash(prefs: SharedPreferences): String {
        return try {
            val allPrefs = prefs.all
            // Exclude hash fields themselves and filter out nulls
            val dataToHash = allPrefs
                .filterKeys { !it.contains("_hash") }
                .filterValues { it != null }
                .toSortedMap()
                .toString()
            
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(dataToHash.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating preferences hash", e)
            ""
        }
    }
    
    /**
     * Store integrity hash for future comparison
     */
    fun storeIntegrityHash(prefName: String) {
        try {
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val hash = calculatePreferencesHash(prefs)
            prefs.edit().putString("${prefName}_integrity_hash", hash).apply()
            Log.d(TAG, "Integrity hash stored for $prefName")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing integrity hash", e)
        }
    }
    
    /**
     * Get Android ID
     */
    private fun getAndroidId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Get aggregate tamper status
     * Combines all tamper indicators with severity classification
     */
    fun getTamperStatus(): TamperStatus {
        val tamperFlags = mutableListOf<String>()
        var severity = TamperSeverity.NONE
        
        // Check device-level tampering
        if (isRooted()) {
            tamperFlags.add("ROOTED")
            severity = TamperSeverity.CRITICAL
        }
        
        if (isBootloaderUnlocked()) {
            tamperFlags.add("BOOTLOADER_UNLOCKED")
            if (severity != TamperSeverity.CRITICAL) {
                severity = TamperSeverity.CRITICAL
            }
        }
        
        if (isCustomROM()) {
            tamperFlags.add("CUSTOM_ROM")
            if (severity == TamperSeverity.NONE || severity == TamperSeverity.MEDIUM) {
                severity = TamperSeverity.HIGH
            }
        }
        
        if (isUSBDebuggingEnabled()) {
            tamperFlags.add("USB_DEBUG_ENABLED")
            if (severity == TamperSeverity.NONE || severity == TamperSeverity.MEDIUM) {
                severity = TamperSeverity.HIGH
            }
        }
        
        if (isDeveloperModeEnabled()) {
            tamperFlags.add("DEV_MODE_ENABLED")
            if (severity == TamperSeverity.NONE) {
                severity = TamperSeverity.HIGH
            }
        }
        
        // Check local data tampering
        if (isLocalDataTampered()) {
            tamperFlags.add("LOCAL_DATA_TAMPERED")
            if (severity != TamperSeverity.CRITICAL) {
                severity = TamperSeverity.HIGH
            }
        }
        
        // Check backend data tampering
        if (isBackendDataTampered()) {
            tamperFlags.add("BACKEND_DATA_MISMATCH")
            severity = TamperSeverity.CRITICAL
        }
        
        return TamperStatus(
            isTampered = tamperFlags.isNotEmpty(),
            tamperFlags = tamperFlags,
            severity = severity,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Get detailed tamper report
     */
    fun getTamperReport(): String {
        val status = getTamperStatus()
        return buildString {
            append("Tamper Status Report\n")
            append("====================\n")
            append("Tampered: ${status.isTampered}\n")
            append("Severity: ${status.severity}\n")
            append("Flags: ${status.tamperFlags.joinToString(", ")}\n")
            append("Timestamp: ${status.timestamp}\n")
        }
    }
    
    /**
     * Get detailed categorized tamper report
     */
    fun getDetailedTamperReport(): DetailedTamperReport {
        val status = getTamperStatus()
        
        val deviceTamperingFlags = mutableListOf<String>()
        val localDataTamperingFlags = mutableListOf<String>()
        val backendDataTamperingFlags = mutableListOf<String>()
        
        for (flag in status.tamperFlags) {
            when (flag) {
                "ROOTED", "BOOTLOADER_UNLOCKED", "CUSTOM_ROM", 
                "USB_DEBUG_ENABLED", "DEV_MODE_ENABLED" -> deviceTamperingFlags.add(flag)
                "LOCAL_DATA_TAMPERED", "CACHE_TAMPERED" -> localDataTamperingFlags.add(flag)
                "BACKEND_DATA_MISMATCH" -> backendDataTamperingFlags.add(flag)
            }
        }
        
        val recommendations = mutableListOf<String>()
        
        if (deviceTamperingFlags.isNotEmpty()) {
            recommendations.add("Device has been modified. Consider factory reset.")
        }
        if (localDataTamperingFlags.isNotEmpty()) {
            recommendations.add("Local data integrity compromised. Restore from backup.")
        }
        if (backendDataTamperingFlags.isNotEmpty()) {
            recommendations.add("Device identity mismatch detected. Contact support.")
        }
        
        return DetailedTamperReport(
            timestamp = status.timestamp,
            isTampered = status.isTampered,
            severity = status.severity,
            deviceTampering = deviceTamperingFlags,
            localDataTampering = localDataTamperingFlags,
            backendDataTampering = backendDataTamperingFlags,
            recommendations = recommendations
        )
    }

    /**
     * Detect SELinux modifications
     * SELinux should be in Enforcing mode for security
     */
    fun isSELinuxModified(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("getenforce")
            val selinuxStatus = process.inputStream.bufferedReader().readText().trim()
            
            val isModified = selinuxStatus != "Enforcing"
            
            if (isModified) {
                Log.w(TAG, "SELinux modified: $selinuxStatus")
                auditLog.logIncident(
                    type = "SELINUX_MODIFIED",
                    severity = "HIGH",
                    details = "SELinux status: $selinuxStatus (expected: Enforcing)"
                )
            }
            
            isModified
        } catch (e: Exception) {
            Log.d(TAG, "SELinux check not available: ${e.message}")
            false
        }
    }

    /**
     * Detect system file modifications
     * Critical system files should exist and be unmodified
     */
    fun areSystemFilesModified(): Boolean {
        return try {
            val criticalFiles = listOf(
                "/system/bin/app_process",
                "/system/lib/libc.so",
                "/system/lib64/libc.so",
                "/system/bin/linker",
                "/system/bin/linker64"
            )
            
            val missingFiles = criticalFiles.filter { !File(it).exists() }
            
            if (missingFiles.isNotEmpty()) {
                Log.w(TAG, "System files modified: ${missingFiles.size} files missing")
                auditLog.logIncident(
                    type = "SYSTEM_FILES_MODIFIED",
                    severity = "CRITICAL",
                    details = "Missing critical system files: ${missingFiles.joinToString(", ")}"
                )
                return true
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking system files", e)
            false
        }
    }

    /**
     * Detect suspicious packages
     * Check for known root/hacking tools
     */
    fun hasSuspiciousPackages(): Boolean {
        return try {
            val suspiciousPackages = listOf(
                "com.koushikdutta.superuser",
                "eu.chainfire.supersu",
                "com.noshufou.android.su",
                "com.thirdparty.superuser",
                "com.yellowes.su",
                "com.topjohnwu.magisk",
                "com.xposed.installer",
                "de.robv.android.xposed.installer",
                "com.android.vending.billing.InAppBillingService.LOCK",
                "com.chelpus.lackypatch"
            )
            
            val pm = context.packageManager
            val foundPackages = mutableListOf<String>()
            
            for (packageName in suspiciousPackages) {
                try {
                    pm.getPackageInfo(packageName, 0)
                    foundPackages.add(packageName)
                } catch (e: Exception) {
                    // Package not found, which is good
                }
            }
            
            if (foundPackages.isNotEmpty()) {
                Log.w(TAG, "Suspicious packages detected: ${foundPackages.size}")
                auditLog.logIncident(
                    type = "SUSPICIOUS_PACKAGES_DETECTED",
                    severity = "CRITICAL",
                    details = "Found suspicious packages: ${foundPackages.joinToString(", ")}"
                )
                return true
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking suspicious packages", e)
            false
        }
    }

    /**
     * Detect Xposed Framework
     * Xposed allows runtime modification of system behavior
     */
    fun isXposedInstalled(): Boolean {
        return try {
            // Check for Xposed installer
            val xposedInstallerExists = File("/data/data/de.robv.android.xposed.installer").exists()
            
            // Check for Xposed framework
            val xposedFrameworkExists = File("/system/framework/XposedBridge.jar").exists()
            
            // Check for Xposed modules
            val xposedModulesDir = File("/data/xposed_mod")
            val xposedModulesExist = xposedModulesDir.exists() && xposedModulesDir.isDirectory
            
            val isXposedInstalled = xposedInstallerExists || xposedFrameworkExists || xposedModulesExist
            
            if (isXposedInstalled) {
                Log.w(TAG, "Xposed Framework detected")
                auditLog.logIncident(
                    type = "XPOSED_DETECTED",
                    severity = "CRITICAL",
                    details = "Xposed Framework detected on device"
                )
            }
            
            isXposedInstalled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Xposed", e)
            false
        }
    }

    /**
     * Detect Magisk installation
     * Magisk allows systemless root and module installation
     */
    fun isMagiskInstalled(): Boolean {
        return try {
            val magiskPaths = listOf(
                "/data/adb/magisk",
                "/data/adb/magisk.db",
                "/data/adb/modules",
                "/sbin/.magisk"
            )
            
            val magiskFound = magiskPaths.any { File(it).exists() }
            
            if (magiskFound) {
                Log.w(TAG, "Magisk installation detected")
                auditLog.logIncident(
                    type = "MAGISK_DETECTED",
                    severity = "CRITICAL",
                    details = "Magisk installation detected on device"
                )
            }
            
            magiskFound
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Magisk", e)
            false
        }
    }

    /**
     * Detect emulator/simulator
     * Device should be a real physical device
     */
    fun isEmulator(): Boolean {
        return try {
            val isEmulator = (Build.FINGERPRINT.startsWith("generic") ||
                    Build.FINGERPRINT.startsWith("unknown") ||
                    Build.MODEL.contains("google_sdk") ||
                    Build.MODEL.contains("Emulator") ||
                    Build.MODEL.contains("Android SDK") ||
                    Build.MANUFACTURER.contains("Genymotion") ||
                    (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                    "Qemu" == Build.HARDWARE ||
                    Build.HARDWARE.contains("ranchu") ||
                    Build.PRODUCT.contains("sdk_google") ||
                    Build.PRODUCT.contains("google_sdk") ||
                    Build.PRODUCT.contains("sdk") ||
                    Build.PRODUCT.contains("emulator"))
            
            if (isEmulator) {
                Log.w(TAG, "Emulator detected")
                auditLog.logIncident(
                    type = "EMULATOR_DETECTED",
                    severity = "HIGH",
                    details = "Device appears to be an emulator or simulator"
                )
            }
            
            isEmulator
        } catch (e: Exception) {
            Log.e(TAG, "Error checking emulator", e)
            false
        }
    }

    /**
     * Detect debuggable app
     * App should not be debuggable in production
     */
    fun isAppDebuggable(): Boolean {
        return try {
            val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            
            if (isDebuggable) {
                Log.w(TAG, "App is debuggable")
                auditLog.logIncident(
                    type = "APP_DEBUGGABLE",
                    severity = "HIGH",
                    details = "Application is running in debuggable mode"
                )
            }
            
            isDebuggable
        } catch (e: Exception) {
            Log.e(TAG, "Error checking app debuggable", e)
            false
        }
    }

    /**
     * Comprehensive advanced tampering check
     * Combines all advanced detection methods
     */
    fun performAdvancedTamperCheck(): AdvancedTamperReport {
        val detections = mutableListOf<String>()
        var severity = TamperSeverity.NONE
        
        // Check advanced indicators
        if (isSELinuxModified()) {
            detections.add("SELINUX_MODIFIED")
            severity = TamperSeverity.HIGH
        }
        
        if (areSystemFilesModified()) {
            detections.add("SYSTEM_FILES_MODIFIED")
            severity = TamperSeverity.CRITICAL
        }
        
        if (hasSuspiciousPackages()) {
            detections.add("SUSPICIOUS_PACKAGES")
            severity = TamperSeverity.CRITICAL
        }
        
        if (isXposedInstalled()) {
            detections.add("XPOSED_INSTALLED")
            severity = TamperSeverity.CRITICAL
        }
        
        if (isMagiskInstalled()) {
            detections.add("MAGISK_INSTALLED")
            severity = TamperSeverity.CRITICAL
        }
        
        if (isEmulator()) {
            detections.add("EMULATOR_DETECTED")
            if (severity != TamperSeverity.CRITICAL) {
                severity = TamperSeverity.HIGH
            }
        }
        
        if (isAppDebuggable()) {
            detections.add("APP_DEBUGGABLE")
            if (severity == TamperSeverity.NONE) {
                severity = TamperSeverity.HIGH
            }
        }
        
        return AdvancedTamperReport(
            timestamp = System.currentTimeMillis(),
            detections = detections,
            severity = severity,
            isTampered = detections.isNotEmpty()
        )
    }
}

/**
 * Tamper status data class
 * Includes device-level, local data, and backend data tampering
 */
data class TamperStatus(
    val isTampered: Boolean,
    val tamperFlags: List<String>,
    val severity: TamperSeverity,
    val timestamp: Long,
    val deviceTampering: Boolean = false,
    val localDataTampering: Boolean = false,
    val backendDataTampering: Boolean = false
)

/**
 * Tamper severity levels
 */
enum class TamperSeverity {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Detailed tamper report with categorization
 */
data class DetailedTamperReport(
    val timestamp: Long,
    val isTampered: Boolean,
    val severity: TamperSeverity,
    val deviceTampering: List<String>,
    val localDataTampering: List<String>,
    val backendDataTampering: List<String>,
    val recommendations: List<String>
)

/**
 * Advanced tamper report with sophisticated detection results
 */
data class AdvancedTamperReport(
    val timestamp: Long,
    val detections: List<String>,
    val severity: TamperSeverity,
    val isTampered: Boolean
)
