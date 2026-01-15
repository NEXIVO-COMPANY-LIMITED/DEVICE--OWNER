package com.example.deviceowner.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Device Management API Service
 * 
 * Uses unified endpoint for all lock/unlock operations
 * NO separate GET endpoint for lock status
 * Lock status comes in heartbeat response
 */
interface DeviceManagementService {
    
    /**
     * Unified device management endpoint
     * Handles both lock and unlock operations
     * 
     * Request: {"action": "lock", "reason": "string"}
     * Response: {"success": true, "message": "...", "timestamp": 123456}
     * 
     * Backend stores lock status in database
     * NO need for separate GET endpoint
     */
    @POST("api/devices/{device_id}/manage/")
    suspend fun manageDevice(
        @Path("device_id") deviceId: String,
        @Body request: ManageRequest
    ): Response<ManageResponse>
}

/**
 * Manage Request
 * Used for both lock and unlock operations
 */
data class ManageRequest(
    val action: String,      // "lock" or "unlock"
    val reason: String       // Reason for the action
)

/**
 * Manage Response
 */
data class ManageResponse(
    val success: Boolean,
    val message: String,
    val timestamp: Long
)
