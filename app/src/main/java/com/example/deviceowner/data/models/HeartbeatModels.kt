package com.example.deviceowner.data.models

import com.google.gson.annotations.SerializedName

/**
 * Heartbeat request data model for POST /devices/<device_id>/data/
 * Uses SAME fields as DeviceRegistrationRequest EXCEPT loan_number
 * 
 * The device_id is obtained from registration response and used in the URL path
 */
data class HeartbeatRequest(
    @SerializedName("device_id")
    val deviceId: String? = null,
    
    @SerializedName("android_id")
    val androidId: String? = null,
    
    val model: String? = null,
    val manufacturer: String? = null,
    val brand: String? = null,
    val product: String? = null,
    val device: String? = null,
    val board: String? = null,
    val hardware: String? = null,
    
    @SerializedName("build_id")
    val buildId: String? = null,
    
    @SerializedName("build_type")
    val buildType: String? = null,
    
    @SerializedName("build_tags")
    val buildTags: String? = null,
    
    @SerializedName("build_time")
    val buildTime: Long? = null,
    
    @SerializedName("build_user")
    val buildUser: String? = null,
    
    @SerializedName("build_host")
    val buildHost: String? = null,
    
    val fingerprint: String? = null,
    val bootloader: String? = null,
    
    @SerializedName("android_info")
    val androidInfo: Map<String, String>? = null,
    
    @SerializedName("imei_info")
    val imeiInfo: Map<String, String>? = null,
    
    @SerializedName("storage_info")
    val storageInfo: Map<String, String>? = null,
    
    @SerializedName("location_info")
    val locationInfo: Map<String, String>? = null,
    
    @SerializedName("app_info")
    val appInfo: Map<String, String>? = null,
    
    @SerializedName("security_info")
    val securityInfo: Map<String, String>? = null,
    
    @SerializedName("system_integrity")
    val systemIntegrity: Map<String, String>? = null
    
    // NOTE: loan_number is NOT included in heartbeat (only in registration)
)

/**
 * Heartbeat response data model
 */
data class HeartbeatResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("device") val device: DeviceSnapshot? = null,
    @SerializedName("management") val management: ManagementStatus? = null,
    @SerializedName("instructions") val instructions: List<String>? = null,
    @SerializedName("changes_detected") val changesDetected: Boolean? = null,
    @SerializedName("changed_fields") val changedFields: List<String>? = null,
    @SerializedName("tamper_indicators") val tamperIndicators: List<String>? = null,
    @SerializedName("security_status") val securityStatus: SecurityStatus? = null,
    @SerializedName("expected_device_data") val expectedDeviceData: ExpectedDeviceData? = null,
    @SerializedName("location") val location: LocationStatus? = null,
    @SerializedName("battery") val battery: BatteryStatus? = null
)

data class DeviceSnapshot(
    @SerializedName("id") val id: String,
    @SerializedName("serial_number") val serialNumber: String? = null,
    @SerializedName("manufacturer") val manufacturer: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("device_type") val deviceType: String? = null,
    @SerializedName("last_seen") val lastSeen: String? = null
)

data class ManagementStatus(
    @SerializedName("status") val status: String? = null,
    @SerializedName("is_locked") val isLocked: Boolean? = null,
    @SerializedName("block_reason") val blockReason: String? = null
)

data class SecurityStatus(
    @SerializedName("is_rooted") val isRooted: Boolean? = null,
    @SerializedName("has_custom_rom") val hasCustomRom: Boolean? = null,
    @SerializedName("is_debugging_enabled") val isDebuggingEnabled: Boolean? = null,
    @SerializedName("is_developer_mode_enabled") val isDeveloperModeEnabled: Boolean? = null,
    @SerializedName("has_unknown_sources") val hasUnknownSources: Boolean? = null,
    @SerializedName("bootloader_unlocked") val bootloaderUnlocked: Boolean? = null,
    @SerializedName("security_score") val securityScore: Int? = null,
    @SerializedName("tamper_level") val tamperLevel: String? = null,
    
    // Legacy fields for backward compatibility
    @SerializedName("rooted") val rooted: Boolean? = null,
    @SerializedName("usb_debug") val usbDebug: Boolean? = null,
    @SerializedName("developer_mode") val developerMode: Boolean? = null,
    @SerializedName("bootloader") val bootloader: Boolean? = null,
    @SerializedName("custom_rom") val customRom: Boolean? = null,
    @SerializedName("tamper") val tamper: String? = null
)

data class ExpectedDeviceData(
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("serial_number") val serialNumber: String? = null,
    @SerializedName("loan_number") val loanNumber: String? = null,
    @SerializedName("imei") val imei: String? = null,
    @SerializedName("android_id") val androidId: String? = null,
    @SerializedName("manufacturer") val manufacturer: String? = null,
    @SerializedName("model") val model: String? = null
)

data class LocationStatus(
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("has_location") val hasLocation: Boolean? = null
)

data class BatteryStatus(
    @SerializedName("level") val level: Int? = null,
    @SerializedName("has_battery_data") val hasBatteryData: Boolean? = null
)