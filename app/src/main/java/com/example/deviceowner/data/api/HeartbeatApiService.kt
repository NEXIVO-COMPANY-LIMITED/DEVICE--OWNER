package com.example.deviceowner.data.api

import com.example.deviceowner.managers.HeartbeatData
import retrofit2.Response
import retrofit2.http.*

/**
 * API service for device heartbeat verification
 * Handles communication with backend for data verification and command reception
 */
interface HeartbeatApiService {
    
    /**
     * Register device with all comparison data
     * POST /api/devices/register
     * 
     * Backend will:
     * 1. Store all device data for future comparison
     * 2. Create device profile in database
     * 3. Return verification status
     */
    @POST("api/devices/register/")
    suspend fun registerDevice(
        @Body registrationData: DeviceRegistrationPayload
    ): Response<DeviceRegistrationResponse>
    
    /**
     * Send device heartbeat data to backend for verification
     * POST /api/devices/{device_id}/data/
     * 
     * Backend will:
     * 1. Verify the data against stored records
     * 2. Detect any changes
     * 3. Send blocking command if data mismatch detected
     */
    @POST("api/devices/{device_id}/data/")
    suspend fun sendHeartbeatData(
        @Path("device_id") deviceId: String,
        @Body heartbeatData: HeartbeatDataPayload
    ): Response<HeartbeatVerificationResponse>
    
    /**
     * Get verification status and any pending commands
     * GET /api/devices/{device_id}/verification-status
     */
    @GET("api/devices/{device_id}/verification-status/")
    suspend fun getVerificationStatus(
        @Path("device_id") deviceId: String
    ): Response<VerificationStatusResponse>
    
    /**
     * Acknowledge received blocking command
     * POST /api/devices/{device_id}/command-ack
     */
    @POST("api/devices/{device_id}/command-ack/")
    suspend fun acknowledgeCommand(
        @Path("device_id") deviceId: String,
        @Body ackData: CommandAcknowledgment
    ): Response<AcknowledgmentResponse>
    
    /**
     * Report data change detected locally
     * POST /api/devices/{device_id}/data-change
     */
    @POST("api/devices/{device_id}/data-change/")
    suspend fun reportDataChange(
        @Path("device_id") deviceId: String,
        @Body changeReport: DataChangeReport
    ): Response<ChangeReportResponse>
    
    /**
     * Report device identifier mismatch to backend
     * POST /api/devices/{device_id}/mismatch-alert
     */
    @POST("api/devices/{device_id}/mismatch-alert/")
    suspend fun reportMismatch(
        @Path("device_id") deviceId: String,
        @Body alert: MismatchAlert
    ): Response<MismatchAlertResponse>
}

/**
 * Response from heartbeat verification
 */
data class HeartbeatVerificationResponse(
    val success: Boolean,
    val message: String,
    val verified: Boolean,
    val dataMatches: Boolean,
    val command: BlockingCommand? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Blocking command from backend
 */
data class BlockingCommand(
    val commandId: String,
    val type: String, // LOCK_DEVICE, DISABLE_FEATURES, WIPE_DATA, etc.
    val reason: String,
    val severity: String, // CRITICAL, HIGH, MEDIUM
    val parameters: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Verification status response
 */
data class VerificationStatusResponse(
    val deviceId: String,
    val isVerified: Boolean,
    val lastVerificationTime: Long,
    val pendingCommands: List<BlockingCommand> = emptyList(),
    val dataStatus: String, // VERIFIED, MISMATCH, PENDING
    val message: String
)

/**
 * Command acknowledgment
 */
data class CommandAcknowledgment(
    val commandId: String,
    val status: String, // RECEIVED, EXECUTING, COMPLETED, FAILED
    val executionTime: Long = System.currentTimeMillis(),
    val details: String = ""
)

/**
 * Acknowledgment response
 */
data class AcknowledgmentResponse(
    val success: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Data change report
 */
data class DataChangeReport(
    val field: String,
    val previousValue: String,
    val currentValue: String,
    val severity: String,
    val detectedAt: Long = System.currentTimeMillis(),
    val details: String = ""
)

/**
 * Change report response
 */
data class ChangeReportResponse(
    val success: Boolean,
    val message: String,
    val action: String, // BLOCK, WARN, INVESTIGATE, NONE
    val command: BlockingCommand? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Device registration data payload
 * Sent during device registration to backend
 * Contains all data needed for future tamper comparison
 */
data class DeviceRegistrationPayload(
    val loan_number: String,
    val device_id: String,
    val device_type: String = "phone",
    val manufacturer: String,
    val model: String,
    val platform: String = "Android",
    val os_version: String,
    val processor: String,
    val installed_ram: String,
    val total_storage: String,
    val build_number: Int,
    val sdk_version: Int,
    val machine_name: String,
    // Additional device info
    val system_type: String = "Mobile",
    val os_edition: String = "Mobile",
    // Comparison data - collected at registration
    val android_id: String,
    val device_fingerprint: String,
    val bootloader: String,
    val hardware: String,
    val product: String,
    val device: String,
    val brand: String,
    val security_patch_level: String,
    val system_uptime: Long,
    val battery_level: Int,
    val installed_apps_hash: String,
    val system_properties_hash: String,
    // Location data
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    // Tamper status at registration
    val is_device_rooted: Boolean = false,
    val is_usb_debugging_enabled: Boolean = false,
    val is_developer_mode_enabled: Boolean = false,
    val is_bootloader_unlocked: Boolean = false,
    val is_custom_rom: Boolean = false,
    val registration_timestamp: Long = System.currentTimeMillis()
)

/**
 * Device registration response from backend
 */
data class DeviceRegistrationResponse(
    val success: Boolean,
    val message: String,
    val device_id: String?,
    val registered_at: Long,
    val verification_status: String, // VERIFIED, PENDING, FAILED
    val stored_data_hash: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Heartbeat data payload sent to backend
 * POST /api/devices/{device_id}/data/
 * Contains essential fields needed for device verification
 */
data class HeartbeatDataPayload(
    val device_id: String,
    val device_type: String = "phone",
    val manufacturer: String,
    val model: String,
    val platform: String = "Android",
    val os_version: String,
    val processor: String,
    val installed_ram: String,
    val total_storage: String,
    val machine_name: String,
    val build_number: Int,
    val sdk_version: Int,
    val android_id: String,
    val device_fingerprint: String,
    val bootloader: String,
    val security_patch_level: String,
    val system_uptime: Long,
    val battery_level: Int,
    val installed_apps_hash: String,
    val system_properties_hash: String,
    val is_device_rooted: Boolean = false,
    val is_usb_debugging_enabled: Boolean = false,
    val is_developer_mode_enabled: Boolean = false,
    val is_bootloader_unlocked: Boolean = false,
    val is_custom_rom: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val tamper_severity: String = "NONE",
    val tamper_flags: List<String> = emptyList(),
    val loan_status: String = "loaned",
    val is_online: Boolean = true,
    val is_trusted: Boolean = true,
    val is_locked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Mismatch alert sent to backend when device identifiers change
 */
data class MismatchAlert(
    val deviceId: String,
    val mismatchType: String, // FINGERPRINT_MISMATCH, DEVICE_SWAP, DEVICE_CLONE, etc.
    val description: String,
    val severity: String, // CRITICAL, HIGH, MEDIUM
    val storedValue: String,
    val currentValue: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceProfile: Map<String, String>? = null,
    val loanNumber: String? = null
)

/**
 * Response from mismatch alert
 */
data class MismatchAlertResponse(
    val success: Boolean,
    val message: String,
    val action: String? = null, // LOCK, WIPE, ALERT, INVESTIGATE, etc.
    val commandId: String? = null,
    val command: BlockingCommand? = null,
    val timestamp: Long = System.currentTimeMillis()
)
