package com.example.deviceowner.data.api

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
    
    /**
     * Send device management command to backend
     * POST /api/devices/{device_id}/manage/
     * 
     * Used for:
     * - Lock device (tampering detected)
     * - Unlock device (admin override)
     * - Wipe device (security breach)
     */
    @POST("api/devices/{device_id}/manage/")
    suspend fun sendDeviceManagementCommand(
        @Path("device_id") deviceId: String,
        @Body command: DeviceManagementCommand
    ): Response<DeviceManagementResponse>
}

/**
 * Immutable device fields for tampering detection
 * These fields CANNOT change from registration to heartbeat
 * Used to detect device swap, tampering, or modifications
 * NOTE: Serial Number excluded - cannot be reliably collected on Android 9+
 */
data class ImmutableDeviceFields(
    val manufacturer: String,
    val model: String,
    val android_id: String,
    val device_fingerprint: String,
    val bootloader: String,
    val processor: String
)


