package com.example.deviceowner.api

import android.content.Context
import android.util.Log
import com.example.deviceowner.config.ApiConfig
import com.example.deviceowner.data.api.RetryInterceptor
import com.example.deviceowner.data.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * Unified API Manager - Single File for All API Calls
 * 
 * This is the ONLY file that should make API calls.
 * All other files should call methods from this class.
 * 
 * Features:
 * - Single Retrofit instance
 * - Centralized error handling
 * - Retry logic
 * - HTTP support (no HTTPS required)
 * - Easy to use from any file
 */
class UnifiedApiManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "UnifiedApiManager"
        
        @Volatile
        private var INSTANCE: UnifiedApiManager? = null
        
        /**
         * Get singleton instance
         * Use this from any file to access API calls
         */
        fun getInstance(context: Context): UnifiedApiManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UnifiedApiManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Single Retrofit instance for all API calls
    private val retrofit: Retrofit by lazy {
        val baseUrl = ApiConfig.getBaseUrl(context)
        Log.d(TAG, "Initializing Retrofit with base URL: $baseUrl")
        
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(createHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    // Single API service instance
    private val apiService: UnifiedApiService by lazy {
        retrofit.create(UnifiedApiService::class.java)
    }
    
    /**
     * Create HTTP client with retry and logging
     */
    private fun createHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
        
        // Add logging if enabled
        if (ApiConfig.isLoggingEnabled()) {
            val logging = HttpLoggingInterceptor { message ->
                Log.d(TAG, message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }
        
        // Add retry interceptor
        builder.addInterceptor(RetryInterceptor())
        
        // Configure timeouts
        builder
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        
        return builder.build()
    }
    
    // ==================== DEVICE REGISTRATION ====================
    
    /**
     * Register device with backend
     * Use this from any file: UnifiedApiManager.getInstance(context).registerDevice(...)
     */
    suspend fun registerDevice(
        registrationData: DeviceRegistrationPayload
    ): Result<DeviceRegistrationResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Registering device: ${registrationData.device_id}")
            val response = apiService.registerDevice(registrationData)
            
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "✓ Device registered successfully")
                Result.success(response.body()!!)
            } else {
                val error = "Registration failed: ${response.code()} - ${response.message()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering device", e)
            Result.failure(e)
        }
    }
    
    // ==================== HEARTBEAT ====================
    
    /**
     * Send heartbeat data to backend
     */
    suspend fun sendHeartbeatData(
        deviceId: String,
        heartbeatData: HeartbeatDataPayload
    ): Result<HeartbeatVerificationResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sending heartbeat for device: $deviceId")
            val response = apiService.sendHeartbeatData(deviceId, heartbeatData)
            
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "✓ Heartbeat sent successfully")
                Result.success(response.body()!!)
            } else {
                val error = "Heartbeat failed: ${response.code()} - ${response.message()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending heartbeat", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get verification status
     */
    suspend fun getVerificationStatus(deviceId: String): Result<VerificationStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getVerificationStatus(deviceId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Verification status failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting verification status", e)
            Result.failure(e)
        }
    }
    
    // ==================== DEVICE MANAGEMENT ====================
    
    /**
     * Send device management command (lock/unlock)
     */
    suspend fun manageDevice(
        deviceId: String,
        action: String,
        reason: String
    ): Result<DeviceManagementResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Managing device: $deviceId, action: $action")
            val command = DeviceManagementCommand(
                action = action,
                reason = reason,
                timestamp = System.currentTimeMillis()
            )
            val response = apiService.sendDeviceManagementCommand(deviceId, command)
            
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "✓ Device management command sent successfully")
                Result.success(response.body()!!)
            } else {
                val error = "Device management failed: ${response.code()} - ${response.message()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error managing device", e)
            Result.failure(e)
        }
    }
    
    /**
     * Lock device
     */
    suspend fun lockDevice(deviceId: String, reason: String): Result<DeviceManagementResponse> {
        return manageDevice(deviceId, "lock", reason)
    }
    
    /**
     * Unlock device
     */
    suspend fun unlockDevice(deviceId: String, reason: String): Result<DeviceManagementResponse> {
        return manageDevice(deviceId, "unlock", reason)
    }
    
    // ==================== MISMATCH ALERTS ====================
    
    /**
     * Report mismatch alert to backend
     */
    suspend fun reportMismatch(
        deviceId: String,
        alert: MismatchAlert
    ): Result<MismatchAlertResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Reporting mismatch for device: $deviceId")
            val response = apiService.reportMismatch(deviceId, alert)
            
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "✓ Mismatch alert sent successfully")
                Result.success(response.body()!!)
            } else {
                val error = "Mismatch alert failed: ${response.code()} - ${response.message()}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reporting mismatch", e)
            Result.failure(e)
        }
    }
    
    // ==================== DATA CHANGE ====================
    
    /**
     * Report data change
     */
    suspend fun reportDataChange(
        deviceId: String,
        changeReport: DataChangeReport
    ): Result<ChangeReportResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.reportDataChange(deviceId, changeReport)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Data change report failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reporting data change", e)
            Result.failure(e)
        }
    }
    
    // ==================== COMMAND ACKNOWLEDGMENT ====================
    
    /**
     * Acknowledge command
     */
    suspend fun acknowledgeCommand(
        deviceId: String,
        ackData: CommandAcknowledgment
    ): Result<AcknowledgmentResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.acknowledgeCommand(deviceId, ackData)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Command acknowledgment failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acknowledging command", e)
            Result.failure(e)
        }
    }
}

/**
 * Unified API Service Interface
 * 
 * ESSENTIAL ENDPOINTS (3):
 * 1. registerDevice() - Register device once
 * 2. sendHeartbeatData() - Send heartbeat, get lock/unlock command from backend
 * 3. sendDeviceManagementCommand() - Admin lock/unlock (optional)
 * 
 * OPTIONAL/LEGACY ENDPOINTS (4):
 * - getVerificationStatus() - Not needed if backend responds in heartbeat
 * - reportMismatch() - Not needed if backend detects in heartbeat
 * - reportDataChange() - Not needed if backend detects in heartbeat
 * - acknowledgeCommand() - Not needed, device locks/unlocks automatically
 * 
 * NOTE: Backend should handle mismatch detection in heartbeat response.
 * Device sends heartbeat data → Backend compares → Backend responds with lock_status
 */
interface UnifiedApiService {
    
    // ==================== ESSENTIAL ENDPOINTS ====================
    
    /**
     * 1. Register device (once during setup)
     * POST /api/devices/register/
     */
    @POST("api/devices/register/")
    suspend fun registerDevice(
        @Body registrationData: DeviceRegistrationPayload
    ): Response<DeviceRegistrationResponse>
    
    /**
     * 2. Send heartbeat data (every 60 seconds)
     * POST /api/devices/{device_id}/data/
     * 
     * Backend should:
     * - Compare heartbeat data with stored baseline
     * - Detect mismatches/tampering
     * - Return lock_status in response
     * - Device automatically locks/unlocks based on response
     */
    @POST("api/devices/{device_id}/data/")
    suspend fun sendHeartbeatData(
        @Path("device_id") deviceId: String,
        @Body heartbeatData: HeartbeatDataPayload
    ): Response<HeartbeatVerificationResponse>
    
    /**
     * 3. Device management (admin lock/unlock)
     * POST /api/devices/{device_id}/manage/
     * 
     * Note: Lock status is also returned in heartbeat response,
     * so device will auto-lock/unlock on next heartbeat
     */
    @POST("api/devices/{device_id}/manage/")
    suspend fun sendDeviceManagementCommand(
        @Path("device_id") deviceId: String,
        @Body command: DeviceManagementCommand
    ): Response<DeviceManagementResponse>
    
    // ==================== OPTIONAL/LEGACY ENDPOINTS ====================
    // These are kept for backward compatibility but may not be needed
    // if backend handles everything in heartbeat response
    
    /**
     * Optional: Get verification status
     * GET /api/devices/{device_id}/verification-status/
     * 
     * NOTE: Usually not needed - backend should return status in heartbeat response
     */
    @GET("api/devices/{device_id}/verification-status/")
    suspend fun getVerificationStatus(
        @Path("device_id") deviceId: String
    ): Response<VerificationStatusResponse>
    
    /**
     * Optional: Report mismatch alert
     * POST /api/devices/{device_id}/mismatch-alert/
     * 
     * NOTE: Usually not needed - backend should detect mismatch from heartbeat data
     */
    @POST("api/devices/{device_id}/mismatch-alert/")
    suspend fun reportMismatch(
        @Path("device_id") deviceId: String,
        @Body alert: MismatchAlert
    ): Response<MismatchAlertResponse>
    
    /**
     * Optional: Report data change
     * POST /api/devices/{device_id}/data-change/
     * 
     * NOTE: Usually not needed - backend should detect changes from heartbeat data
     */
    @POST("api/devices/{device_id}/data-change/")
    suspend fun reportDataChange(
        @Path("device_id") deviceId: String,
        @Body changeReport: DataChangeReport
    ): Response<ChangeReportResponse>
    
    /**
     * Optional: Acknowledge command
     * POST /api/devices/{device_id}/command-ack/
     * 
     * NOTE: Usually not needed - device locks/unlocks automatically from heartbeat response
     */
    @POST("api/devices/{device_id}/command-ack/")
    suspend fun acknowledgeCommand(
        @Path("device_id") deviceId: String,
        @Body ackData: CommandAcknowledgment
    ): Response<AcknowledgmentResponse>
}
