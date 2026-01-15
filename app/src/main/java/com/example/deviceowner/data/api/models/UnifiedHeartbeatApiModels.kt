package com.example.deviceowner.data.api

import com.example.deviceowner.data.models.UnifiedHeartbeatData
import com.google.gson.annotations.SerializedName

/**
 * API Models for Unified Heartbeat Communication
 * Used for both online registration and heartbeat verification
 * Ensures server and client use identical data format
 */

/**
 * Heartbeat data payload sent to server
 * ONLY includes data that:
 * 1. Can be reliably collected on Android 9+
 * 2. Is needed for tamper detection
 * 3. Changes infrequently or not at all
 * 
 * EXCLUDED (Android 9+ restrictions):
 * - device_imeis (cannot collect reliably)
 * - serial_number (cannot collect reliably)
 * - device_type, platform, os_edition (redundant - always same values)
 */
data class HeartbeatDataPayload(
    @SerializedName("device_id")
    val device_id: String,
    
    @SerializedName("manufacturer")
    val manufacturer: String,
    
    @SerializedName("model")
    val model: String,
    
    @SerializedName("os_version")
    val os_version: String,
    
    @SerializedName("sdk_version")
    val sdk_version: Int,
    
    @SerializedName("build_number")
    val build_number: Int,
    
    @SerializedName("security_patch_level")
    val security_patch_level: String,
    
    @SerializedName("processor")
    val processor: String,
    
    @SerializedName("android_id")
    val android_id: String,
    
    @SerializedName("device_fingerprint")
    val device_fingerprint: String,
    
    @SerializedName("bootloader")
    val bootloader: String,
    
    @SerializedName("installed_apps_hash")
    val installed_apps_hash: String,
    
    @SerializedName("system_properties_hash")
    val system_properties_hash: String,
    
    @SerializedName("is_device_rooted")
    val is_device_rooted: Boolean,
    
    @SerializedName("is_usb_debugging_enabled")
    val is_usb_debugging_enabled: Boolean,
    
    @SerializedName("is_developer_mode_enabled")
    val is_developer_mode_enabled: Boolean,
    
    @SerializedName("is_bootloader_unlocked")
    val is_bootloader_unlocked: Boolean,
    
    @SerializedName("is_custom_rom")
    val is_custom_rom: Boolean,
    
    @SerializedName("tamper_severity")
    val tamper_severity: String,
    
    @SerializedName("tamper_flags")
    val tamper_flags: List<String>,
    
    @SerializedName("is_trusted")
    val is_trusted: Boolean,
    
    @SerializedName("timestamp")
    val timestamp: Long
)

/**
 * Heartbeat verification response from server
 * Contains verified data in same format as UnifiedHeartbeatData
 */
data class HeartbeatVerificationResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("device_id")
    val device_id: String,
    
    @SerializedName("verified_data")
    val verified_data: UnifiedHeartbeatData,
    
    @SerializedName("is_tampered")
    val is_tampered: Boolean,
    
    @SerializedName("mismatches")
    val mismatches: List<HeartbeatMismatchResponse> = emptyList(),
    
    @SerializedName("severity")
    val severity: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("message")
    val message: String
)

/**
 * Mismatch response from server
 */
data class HeartbeatMismatchResponse(
    @SerializedName("field")
    val field: String,
    
    @SerializedName("expected_value")
    val expected_value: String,
    
    @SerializedName("actual_value")
    val actual_value: String,
    
    @SerializedName("severity")
    val severity: String,
    
    @SerializedName("field_type")
    val field_type: String
)

/**
 * Device registration payload
 * Sent during initial registration
 * INCLUDES ALL DATA for server baseline comparison
 */
data class DeviceRegistrationPayload(
    @SerializedName("device_id")
    val device_id: String,
    
    @SerializedName("serial_number")
    val serial_number: String,
    
    @SerializedName("device_type")
    val device_type: String,
    
    @SerializedName("manufacturer")
    val manufacturer: String,
    
    @SerializedName("system_type")
    val system_type: String,
    
    @SerializedName("model")
    val model: String,
    
    @SerializedName("platform")
    val platform: String,
    
    @SerializedName("os_version")
    val os_version: String,
    
    @SerializedName("os_edition")
    val os_edition: String,
    
    @SerializedName("processor")
    val processor: String,
    
    @SerializedName("installed_ram")
    val installed_ram: String,
    
    @SerializedName("total_storage")
    val total_storage: String,
    
    @SerializedName("build_number")
    val build_number: Int,
    
    @SerializedName("sdk_version")
    val sdk_version: Int,
    
    @SerializedName("device_imeis")
    val device_imeis: String,
    
    @SerializedName("loan_number")
    val loan_number: String,
    
    @SerializedName("machine_name")
    val machine_name: String,
    
    @SerializedName("android_id")
    val android_id: String,
    
    @SerializedName("device_fingerprint")
    val device_fingerprint: String,
    
    @SerializedName("bootloader")
    val bootloader: String,
    
    @SerializedName("security_patch_level")
    val security_patch_level: String,
    
    @SerializedName("system_uptime")
    val system_uptime: Long,
    
    @SerializedName("installed_apps_hash")
    val installed_apps_hash: String,
    
    @SerializedName("system_properties_hash")
    val system_properties_hash: String,
    
    @SerializedName("is_device_rooted")
    val is_device_rooted: Boolean,
    
    @SerializedName("is_usb_debugging_enabled")
    val is_usb_debugging_enabled: Boolean,
    
    @SerializedName("is_developer_mode_enabled")
    val is_developer_mode_enabled: Boolean,
    
    @SerializedName("is_bootloader_unlocked")
    val is_bootloader_unlocked: Boolean,
    
    @SerializedName("is_custom_rom")
    val is_custom_rom: Boolean,
    
    @SerializedName("latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    val longitude: Double,
    
    @SerializedName("tamper_severity")
    val tamper_severity: String,
    
    @SerializedName("tamper_flags")
    val tamper_flags: String,
    
    @SerializedName("battery_level")
    val battery_level: Int,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Device registration response
 */
data class DeviceRegistrationResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("device_id")
    val device_id: String,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Verification status response
 */
data class VerificationStatusResponse(
    @SerializedName("device_id")
    val device_id: String,
    
    @SerializedName("is_verified")
    val is_verified: Boolean,
    
    @SerializedName("last_verification_time")
    val last_verification_time: Long,
    
    @SerializedName("pending_commands")
    val pending_commands: List<BlockingCommand> = emptyList(),
    
    @SerializedName("data_status")
    val data_status: String,
    
    @SerializedName("message")
    val message: String
)

/**
 * Blocking command from server
 */
data class BlockingCommand(
    @SerializedName("command_id")
    val command_id: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("reason")
    val reason: String,
    
    @SerializedName("severity")
    val severity: String,
    
    @SerializedName("parameters")
    val parameters: Map<String, String> = emptyMap(),
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Command acknowledgment
 */
data class CommandAcknowledgment(
    @SerializedName("command_id")
    val command_id: String,
    
    @SerializedName("device_id")
    val device_id: String,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Acknowledgment response
 */
data class AcknowledgmentResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String
)

/**
 * Data change report
 */
data class DataChangeReport(
    @SerializedName("device_id")
    val device_id: String,
    
    @SerializedName("field")
    val field: String,
    
    @SerializedName("old_value")
    val old_value: String,
    
    @SerializedName("new_value")
    val new_value: String,
    
    @SerializedName("severity")
    val severity: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Change report response
 */
data class ChangeReportResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String
)

/**
 * Mismatch alert
 */
data class MismatchAlert(
    @SerializedName("device_id")
    val device_id: String,
    
    @SerializedName("alert_type")
    val alert_type: String,
    
    @SerializedName("mismatches")
    val mismatches: List<HeartbeatMismatchResponse>,
    
    @SerializedName("severity")
    val severity: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Mismatch alert response
 */
data class MismatchAlertResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("action")
    val action: String
)

/**
 * Device management command
 * Sent to backend when device needs to be locked/unlocked/wiped
 */
data class DeviceManagementCommand(
    @SerializedName("action")
    val action: String, // "lock", "unlock", "wipe"
    
    @SerializedName("reason")
    val reason: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Device management response from server
 */
data class DeviceManagementResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("device_id")
    val device_id: String,
    
    @SerializedName("action")
    val action: String,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
