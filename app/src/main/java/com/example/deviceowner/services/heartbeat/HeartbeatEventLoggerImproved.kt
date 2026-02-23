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
 * HeartbeatEventLogger - IMPROVED VERSION
 */
class HeartbeatEventLoggerImproved(private val context: Context) {
    
    private val TAG = "HeartbeatEventLogger"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    private val apiService: ApiService by lazy {
        RetrofitClient.instance.create(ApiService::class.java)
    }
    
    private val loggerScope = CoroutineScope(Dispatchers.IO)
    
    fun logEvent(
        eventType: String,
        status: String,
        message: String,
        heartbeatNumber: Int = 0,
        responseTime: Long = 0L,
        errorType: String? = null,
        errorDetails: String? = null,
        consecutiveFailures: Int = 0,
        severity: String = "INFO",
        isLocked: Boolean = false,
        additionalData: Map<String, Any>? = null
    ) {
        val timestamp = dateFormat.format(Date())
        val logEntry = buildLogEntry(
            timestamp = timestamp,
            eventType = eventType,
            status = status,
            message = message,
            heartbeatNumber = heartbeatNumber,
            responseTime = responseTime,
            errorType = errorType,
            errorDetails = errorDetails,
            consecutiveFailures = consecutiveFailures,
            severity = severity,
            isLocked = isLocked,
            additionalData = additionalData
        )
        
        Log.d(TAG, logEntry)
        
        sendLogToApi(
            eventType = eventType,
            status = status,
            message = message,
            heartbeatNumber = heartbeatNumber,
            responseTime = responseTime,
            errorType = errorType,
            errorDetails = errorDetails,
            consecutiveFailures = consecutiveFailures,
            severity = severity,
            isLocked = isLocked,
            additionalData = additionalData
        )
    }
    
    private fun buildLogEntry(
        timestamp: String,
        eventType: String,
        status: String,
        message: String,
        heartbeatNumber: Int = 0,
        responseTime: Long = 0L,
        errorType: String? = null,
        errorDetails: String? = null,
        consecutiveFailures: Int = 0,
        severity: String = "INFO",
        isLocked: Boolean = false,
        additionalData: Map<String, Any>? = null
    ): String {
        return "[$timestamp] [$severity] [$eventType] [$status] - $message"
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
        severity: String = "INFO",
        isLocked: Boolean = false,
        additionalData: Map<String, Any>? = null
    ) {
        loggerScope.launch {
            try {
                val deviceId = DeviceIdProvider.getDeviceId(context)
                if (deviceId.isNullOrBlank()) return@launch
                
                val logRequest = HeartbeatLogRequest(
                    deviceId = deviceId,
                    heartbeatNumber = heartbeatNumber,
                    eventType = eventType,
                    status = status,
                    severity = severity,
                    message = message,
                    responseTimeMs = responseTime,
                    errorType = errorType,
                    errorDetails = errorDetails,
                    consecutiveFailures = consecutiveFailures,
                    timestamp = isoDateFormat.format(Date()),
                    isDeviceLocked = isLocked,
                    additionalData = additionalData,
                    logs = null
                )
                
                apiService.postHeartbeatLog(deviceId, logRequest)
            } catch (_: Exception) {}
        }
    }
}
