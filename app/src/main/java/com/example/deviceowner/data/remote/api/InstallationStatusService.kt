package com.example.deviceowner.data.remote.api

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.models.installation.InstallationStatusInner
import com.example.deviceowner.data.models.installation.InstallationStatusRequest
import com.example.deviceowner.data.remote.ApiClient
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service for sending installation status to backend
 * Handles retries and error handling for installation status reporting
 * 
 * API Endpoint: POST /api/devices/mobile/{device_id}/installation-status/
 * 
 * Request Body:
 * {
 *   "completed": true,
 *   "reason": "Device Owner activated successfully",
 *   "timestamp": "2024-01-15T10:30:00Z"
 * }
 * 
 * Response:
 * {
 *   "success": true,
 *   "message": "Installation status recorded",
 *   "device_id": "ANDROID-ABC123",
 *   "installation_status": "completed",
 *   "completed_at": "2024-01-15T10:30:00Z"
 * }
 */
class InstallationStatusService(private val context: Context) {

    companion object {
        private const val TAG = "InstallationStatusService"
        private const val RETRY_DELAY_MS = 2000L
        private const val MAX_RETRIES = 3
    }

    private val apiClient = ApiClient()

    /**
     * Send installation status with retry logic
     * 
     * @param deviceId The device ID
     * @param completed Whether installation is complete
     * @param reason Reason for the status
     * @param maxRetries Maximum number of retries
     * @return true if successful, false otherwise
     */
    suspend fun sendInstallationStatusWithRetry(
        deviceId: String,
        completed: Boolean,
        reason: String,
        maxRetries: Int = MAX_RETRIES
    ): Boolean {
        Log.i(TAG, "🔍 ═══════════════════════════════════════════════════════════")
        Log.i(TAG, "🔍 Starting installation status send with retry logic")
        Log.i(TAG, "🔍 Device ID: $deviceId")
        Log.i(TAG, "🔍 Completed: $completed")
        Log.i(TAG, "🔍 Reason: $reason")
        Log.i(TAG, "🔍 Max Retries: $maxRetries")
        Log.i(TAG, "🔍 ═══════════════════════════════════════════════════════════")
        
        var lastException: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                Log.d(TAG, "📤 Attempt $attempt/$maxRetries: Sending installation status...")
                
                // Create request with ISO8601 timestamp
                val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())
                val request = InstallationStatusRequest(
                    installationStatus = InstallationStatusInner(completed = completed, reason = reason),
                    completed = completed,
                    reason = reason,
                    timestamp = timestamp
                )
                
                Log.d(TAG, "   Request timestamp: $timestamp")
                Log.d(TAG, "   Request body: completed=$completed, reason=$reason")
                
                // Send to backend
                val response = apiClient.sendInstallationStatus(deviceId, request)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    Log.i(TAG, "✅ ═══════════════════════════════════════════════════════════")
                    Log.i(TAG, "✅ Installation status sent successfully on attempt $attempt")
                    Log.i(TAG, "✅ HTTP ${response.code()}")
                    Log.i(TAG, "✅ Response success: ${body?.success}")
                    Log.i(TAG, "✅ Response message: ${body?.message}")
                    Log.i(TAG, "✅ Installation status: ${body?.installationStatus}")
                    Log.i(TAG, "✅ Completed at: ${body?.completedAt}")
                    Log.i(TAG, "✅ ═══════════════════════════════════════════════════════════")
                    return true
                } else {
                    // HTTP error (4xx, 5xx)
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    Log.e(TAG, "❌ HTTP ${response.code()}: $errorBody")
                    
                    // Don't retry on 4xx errors (client errors)
                    if (response.code() in 400..499) {
                        Log.e(TAG, "❌ Client error (${response.code()}) - not retrying")
                        return false
                    }
                    
                    // Retry on 5xx errors (server errors)
                    if (attempt < maxRetries) {
                        Log.d(TAG, "⏳ Server error - retrying in ${RETRY_DELAY_MS}ms...")
                        delay(RETRY_DELAY_MS)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception on attempt $attempt: ${e.message}", e)
                lastException = e
                
                if (attempt < maxRetries) {
                    Log.d(TAG, "⏳ Retrying in ${RETRY_DELAY_MS}ms...")
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        
        Log.e(TAG, "❌ ═══════════════════════════════════════════════════════════")
        Log.e(TAG, "❌ Failed to send installation status after $maxRetries attempts")
        if (lastException != null) {
            Log.e(TAG, "❌ Last exception: ${lastException.message}")
        }
        Log.e(TAG, "❌ ═══════════════════════════════════════════════════════════")
        return false
    }
}

