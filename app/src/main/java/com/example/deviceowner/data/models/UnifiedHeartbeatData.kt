package com.example.deviceowner.data.models

import com.google.gson.annotations.SerializedName

/**
 * Unified Heartbeat Data Model
 * Used for BOTH online (server) and offline (local cache) storage
 * Ensures consistent data format throughout the system
 * 
 * This is the single source of truth for heartbeat data structure
 * Server stores this exact format
 * Local cache stores this exact format
 * Comparison logic uses this exact format
 * 
 * OFFLINE MODE: Stores compact data for local tamper detection without server
 * ONLINE MODE: Full data sent to server for verification
 */
data class UnifiedHeartbeatData(
    // Device Identification (Immutable - set at registration)
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("serial_number")
    val serialNumber: String,
    
    @SerializedName("android_id")
    val androidId: String,
    
    @SerializedName("device_fingerprint")
    val deviceFingerprint: String,
    
    @SerializedName("manufacturer")
    val manufacturer: String,
    
    @SerializedName("model")
    val model: String,
    
    @SerializedName("bootloader")
    val bootloader: String,
    
    @SerializedName("processor")
    val processor: String,
    
    @SerializedName("device_imeis")
    val deviceImeis: List<String>,
    
    // Device Information (Semi-mutable)
    @SerializedName("platform")
    val platform: String = "Android",
    
    @SerializedName("os_version")
    val osVersion: String,
    
    @SerializedName("sdk_version")
    val sdkVersion: Int,
    
    @SerializedName("build_number")
    val buildNumber: Int,
    
    @SerializedName("security_patch_level")
    val securityPatchLevel: String,
    
    // Device Resources
    @SerializedName("installed_ram")
    val installedRam: String,
    
    @SerializedName("total_storage")
    val totalStorage: String,
    
    @SerializedName("machine_name")
    val machineName: String,
    
    // Tamper Detection Flags (Critical for detection)
    @SerializedName("is_device_rooted")
    val isDeviceRooted: Boolean,
    
    @SerializedName("is_bootloader_unlocked")
    val isBootloaderUnlocked: Boolean,
    
    @SerializedName("is_custom_rom")
    val isCustomRom: Boolean,
    
    @SerializedName("is_usb_debugging_enabled")
    val isUsbDebuggingEnabled: Boolean,
    
    @SerializedName("is_developer_mode_enabled")
    val isDeveloperModeEnabled: Boolean,
    
    // Integrity Hashes (Critical for detection)
    @SerializedName("installed_apps_hash")
    val installedAppsHash: String,
    
    @SerializedName("system_properties_hash")
    val systemPropertiesHash: String,
    
    // Tamper Status
    @SerializedName("tamper_severity")
    val tamperSeverity: String,
    
    @SerializedName("tamper_flags")
    val tamperFlags: List<String>,
    
    // Metadata
    @SerializedName("timestamp")
    val timestamp: Long,
    
    @SerializedName("loan_number")
    val loanNumber: String,
    
    @SerializedName("battery_level")
    val batteryLevel: Int,
    
    @SerializedName("system_uptime")
    val systemUptime: Long,
    
    @SerializedName("latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    val longitude: Double,
    
    @SerializedName("loan_status")
    val loanStatus: String,
    
    @SerializedName("is_online")
    val isOnline: Boolean,
    
    @SerializedName("is_trusted")
    val isTrusted: Boolean,
    
    @SerializedName("is_locked")
    val isLocked: Boolean,
    
    @SerializedName("sync_status")
    val syncStatus: String = "PENDING", // PENDING, SYNCED, FAILED
    
    // Offline Detection Mode
    @SerializedName("detection_mode")
    val detectionMode: String = "ONLINE", // ONLINE, OFFLINE
    
    @SerializedName("is_offline_baseline")
    val isOfflineBaseline: Boolean = false // True if this is baseline for offline comparison
) {
    
    /**
     * Convert to JSON string for storage
     */
    fun toJson(): String {
        return com.google.gson.Gson().toJson(this)
    }
    
    /**
     * Get compact representation for offline storage
     * Only includes critical fields needed for offline tamper detection
     */
    fun toCompactOfflineData(): OfflineHeartbeatData {
        return OfflineHeartbeatData(
            deviceId = deviceId,
            deviceFingerprint = deviceFingerprint,
            isDeviceRooted = isDeviceRooted,
            isBootloaderUnlocked = isBootloaderUnlocked,
            isCustomRom = isCustomRom,
            isUsbDebuggingEnabled = isUsbDebuggingEnabled,
            isDeveloperModeEnabled = isDeveloperModeEnabled,
            installedAppsHash = installedAppsHash,
            systemPropertiesHash = systemPropertiesHash,
            timestamp = timestamp,
            tamperSeverity = tamperSeverity,
            tamperFlags = tamperFlags
        )
    }
    
    companion object {
        /**
         * Create from JSON string
         */
        fun fromJson(json: String): UnifiedHeartbeatData? {
            return try {
                com.google.gson.Gson().fromJson(json, UnifiedHeartbeatData::class.java)
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * Get list of immutable fields (cannot change from registration)
         */
        fun getImmutableFields(): List<String> {
            return listOf(
                "device_id",
                "serial_number",
                "android_id",
                "device_fingerprint",
                "manufacturer",
                "model",
                "bootloader",
                "processor"
            )
        }
        
        /**
         * Get list of critical tamper detection fields
         */
        fun getCriticalTamperFields(): List<String> {
            return listOf(
                "is_device_rooted",
                "is_bootloader_unlocked",
                "is_custom_rom",
                "installed_apps_hash",
                "system_properties_hash"
            )
        }
        
        /**
         * Get list of security fields
         */
        fun getSecurityFields(): List<String> {
            return listOf(
                "is_usb_debugging_enabled",
                "is_developer_mode_enabled"
            )
        }
    }
}

/**
 * Heartbeat comparison result
 * Used for both online and offline comparison
 */
data class HeartbeatComparisonResult(
    val isTampered: Boolean,
    val mismatches: List<HeartbeatMismatch>,
    val severity: String, // NONE, LOW, MEDIUM, HIGH, CRITICAL
    val detectionMode: String, // ONLINE, OFFLINE
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Individual mismatch detected
 */
data class HeartbeatMismatch(
    val field: String,
    val expectedValue: String,
    val actualValue: String,
    val severity: String, // LOW, MEDIUM, HIGH, CRITICAL
    val fieldType: String // IMMUTABLE, CRITICAL_TAMPER, SECURITY, INTEGRITY
)

/**
 * Heartbeat verification request (sent to server)
 */
data class HeartbeatVerificationRequest(
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("heartbeat_data")
    val heartbeatData: UnifiedHeartbeatData,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("request_id")
    val requestId: String = java.util.UUID.randomUUID().toString()
)

/**
 * Heartbeat verification response (from server)
 */
data class HeartbeatVerificationResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("verified_data")
    val verifiedData: UnifiedHeartbeatData,
    
    @SerializedName("is_tampered")
    val isTampered: Boolean,
    
    @SerializedName("mismatches")
    val mismatches: List<HeartbeatMismatch>,
    
    @SerializedName("severity")
    val severity: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("message")
    val message: String
)

/**
 * Compact Offline Heartbeat Data
 * Minimal data structure for offline tamper detection
 * Stores only critical fields needed for comparison
 * Reduces storage footprint and improves offline detection speed
 */
data class OfflineHeartbeatData(
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("device_fingerprint")
    val deviceFingerprint: String,
    
    // Critical tamper flags
    @SerializedName("is_device_rooted")
    val isDeviceRooted: Boolean,
    
    @SerializedName("is_bootloader_unlocked")
    val isBootloaderUnlocked: Boolean,
    
    @SerializedName("is_custom_rom")
    val isCustomRom: Boolean,
    
    @SerializedName("is_usb_debugging_enabled")
    val isUsbDebuggingEnabled: Boolean,
    
    @SerializedName("is_developer_mode_enabled")
    val isDeveloperModeEnabled: Boolean,
    
    // Integrity hashes
    @SerializedName("installed_apps_hash")
    val installedAppsHash: String,
    
    @SerializedName("system_properties_hash")
    val systemPropertiesHash: String,
    
    // Status
    @SerializedName("timestamp")
    val timestamp: Long,
    
    @SerializedName("tamper_severity")
    val tamperSeverity: String,
    
    @SerializedName("tamper_flags")
    val tamperFlags: List<String>
) {
    
    /**
     * Convert to JSON for storage
     */
    fun toJson(): String {
        return com.google.gson.Gson().toJson(this)
    }
    
    companion object {
        /**
         * Create from JSON
         */
        fun fromJson(json: String): OfflineHeartbeatData? {
            return try {
                com.google.gson.Gson().fromJson(json, OfflineHeartbeatData::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Offline Heartbeat Comparison Result
 * Result of comparing current device state against offline baseline
 */
data class OfflineHeartbeatComparisonResult(
    val isTampered: Boolean,
    val mismatches: List<HeartbeatMismatch>,
    val severity: String, // NONE, LOW, MEDIUM, HIGH, CRITICAL
    val detectionMode: String = "OFFLINE",
    val timestamp: Long = System.currentTimeMillis(),
    val baselineTimestamp: Long,
    val timeSinceBaseline: Long // milliseconds
)
