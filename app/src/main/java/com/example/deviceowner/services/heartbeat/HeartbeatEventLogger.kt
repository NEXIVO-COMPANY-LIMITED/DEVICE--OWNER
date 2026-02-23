package com.example.deviceowner.services.heartbeat

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.DeviceIdProvider
import com.example.deviceowner.data.models.heartbeat.HeartbeatLogRequest
import com.example.deviceowner.data.remote.ApiService
import com.example.deviceowner.data.remote.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * HeartbeatEventLogger - PERFECT IMPLEMENTATION v2.0
 */
class HeartbeatEventLogger(private val context: Context) {
    
    private val TAG = "HeartbeatEventLogger"
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    private val apiService: ApiService by lazy {
        RetrofitClient.instance.create(ApiService::class.java)
    }
    
    private val loggerScope = CoroutineScope(Dispatchers.IO)
    
    fun logSuccess(
        heartbeatNumber: Int,
        responseTime: Long,
        message: String,
        isLocked: Boolean = false
    ) {
        // Success logs are not logged
    }
    
    fun logFailure(
        heartbeatNumber: Int,
        responseTime: Long,
        reason: String,
        consecutiveFailures: Int = 0,
        errorType: String? = null
    ) {
        val severity = if (consecutiveFailures >= 5) "CRITICAL" else "WARNING"
        Log.w(TAG, "❌ FAILURE [$errorType] $reason (${responseTime}ms)")
        
        sendLogToApi(
            eventType = "HEARTBEAT_FAILURE",
            status = "FAILED",
            message = reason,
            heartbeatNumber = heartbeatNumber,
            responseTime = responseTime,
            errorType = errorType,
            consecutiveFailures = consecutiveFailures,
            severity = severity
        )
    }
    
    fun logNetworkError(
        heartbeatNumber: Int,
        responseTime: Long,
        errorType: String,
        errorMessage: String,
        consecutiveFailures: Int = 0
    ) {
        Log.e(TAG, "❌ NETWORK_ERROR [$errorType] $errorMessage (${responseTime}ms)")
        
        sendLogToApi(
            eventType = "NETWORK_ERROR",
            status = "ERROR",
            message = "Network error: $errorMessage",
            heartbeatNumber = heartbeatNumber,
            responseTime = responseTime,
            errorType = errorType,
            errorDetails = errorMessage,
            consecutiveFailures = consecutiveFailures,
            severity = "ERROR"
        )
    }
    
    fun logServerError(
        heartbeatNumber: Int,
        responseTime: Long,
        httpCode: Int,
        errorMessage: String,
        consecutiveFailures: Int = 0
    ) {
        Log.e(TAG, "❌ SERVER_ERROR [HTTP_$httpCode] $errorMessage (${responseTime}ms)")
        
        sendLogToApi(
            eventType = "SERVER_ERROR",
            status = "ERROR",
            message = "Server error HTTP $httpCode: $errorMessage",
            heartbeatNumber = heartbeatNumber,
            responseTime = responseTime,
            errorType = "HTTP_$httpCode",
            errorDetails = errorMessage,
            consecutiveFailures = consecutiveFailures,
            severity = if (httpCode >= 500) "CRITICAL" else "ERROR"
        )
    }
    
    fun logException(
        heartbeatNumber: Int,
        responseTime: Long,
        exception: Exception,
        consecutiveFailures: Int = 0
    ) {
        Log.e(TAG, "❌ EXCEPTION [${exception.javaClass.simpleName}] ${exception.message} (${responseTime}ms)")
        
        sendLogToApi(
            eventType = "EXCEPTION",
            status = "ERROR",
            message = "Exception occurred: ${exception.message}",
            heartbeatNumber = heartbeatNumber,
            responseTime = responseTime,
            errorType = exception.javaClass.simpleName,
            errorDetails = exception.stackTraceToString().take(500),
            consecutiveFailures = consecutiveFailures,
            severity = "ERROR"
        )
    }
    
    fun logMaxFailuresReached(
        heartbeatNumber: Int,
        consecutiveFailures: Int,
        maxFailures: Int
    ) {
        Log.e(TAG, "🔴 CRITICAL [MAX_FAILURES] $consecutiveFailures/$maxFailures consecutive failures")
        
        sendLogToApi(
            eventType = "MAX_FAILURES_REACHED",
            status = "CRITICAL",
            message = "Maximum consecutive failures ($consecutiveFailures/$maxFailures) reached",
            heartbeatNumber = heartbeatNumber,
            consecutiveFailures = consecutiveFailures,
            severity = "CRITICAL"
        )
    }
    
    fun logServiceEvent(
        eventType: String,
        message: String,
        severity: String = "INFO"
    ) {
        when (severity) {
            "INFO" -> Log.i(TAG, "ℹ️ $eventType: $message")
            "WARNING" -> Log.w(TAG, "⚠️ $eventType: $message")
            "ERROR" -> Log.e(TAG, "❌ $eventType: $message")
            else -> Log.d(TAG, "📝 $eventType: $message")
        }
    }
    
    private fun sendLogToApi(
        eventType: String,
        status: String,
        message: String,
        heartbeatNumber: Int = 0,
        responseTime: Long = 0L,
        errorType: String? = null,
        errorDetails: String? = null,
        consecutiveFailures: Int = 0,
        severity: String = "INFO"
    ) {
        loggerScope.launch {
            try {
                val deviceId = DeviceIdProvider.getDeviceId(context)
                if (deviceId.isNullOrBlank()) {
                    Log.w(TAG, "⚠️ Cannot send log: Device ID not found")
                    return@launch
                }
                
                val logRequest = HeartbeatLogRequest(
                    deviceId = deviceId,
                    heartbeatNumber = heartbeatNumber,
                    eventType = eventType,
                    status = status,
                    severity = severity,
                    message = message.take(500),
                    responseTimeMs = responseTime,
                    errorType = errorType,
                    errorDetails = errorDetails?.take(500),
                    consecutiveFailures = consecutiveFailures,
                    timestamp = isoDateFormat.format(Date()),
                    isDeviceLocked = false,
                    additionalData = null,
                    logs = null
                )
                
                apiService.postHeartbeatLog(deviceId, logRequest)
            } catch (_: Exception) {}
        }
    }
}
