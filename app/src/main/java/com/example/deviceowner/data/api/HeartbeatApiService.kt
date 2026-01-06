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
    @GET("api/devices/{device_id}/verification-status")
    suspend fun getVerificationStatus(
        @Path("device_id") deviceId: String
    ): Response<VerificationStatusResponse>
    
    /**
     * Acknowledge received blocking command
     * POST /api/devices/{device_id}/command-ack
     */
    @POST("api/devices/{device_id}/command-ack")
    suspend fun acknowledgeCommand(
        @Path("device_id") deviceId: String,
        @Body ackData: CommandAcknowledgment
    ): Response<AcknowledgmentResponse>
    
    /**
     * Report data change detected locally
     * POST /api/devices/{device_id}/data-change
     */
    @POST("api/devices/{device_id}/data-change")
    suspend fun reportDataChange(
        @Path("device_id") deviceId: String,
        @Body changeReport: DataChangeReport
    ): Response<ChangeReportResponse>
    
    /**
     * Report device identifier mismatch to backend
     * POST /api/devices/{device_id}/mismatch-alert
     */
    @POST("api/devices/{device_id}/mismatch-alert")
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
 * Heartbeat data payload sent to backend
 * Includes device verification data with loan ID as primary identifier
 */
data class HeartbeatDataPayload(
    val loan_id: String,
    val device_id: String,
    val serial_number: String,
    val device_type: String = "phone",
    val manufacturer: String,
    val system_type: String = "Mobile",
    val model: String,
    val platform: String = "Android",
    val os_version: String,
    val os_edition: String = "Mobile",
    val processor: String,
    val installed_ram: String,
    val total_storage: String,
    val build_number: Int,
    val sdk_version: Int,
    val device_imeis: List<String>,
    val machine_name: String,
    // Location data
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location_accuracy: Float? = null,
    val location_altitude: Double? = null,
    val location_bearing: Float? = null,
    val location_speed: Float? = null,
    val location_timestamp: Long? = null,
    val location_provider: String? = null
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
    val loanId: String? = null
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
