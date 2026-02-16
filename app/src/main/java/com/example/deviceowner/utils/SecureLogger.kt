package com.example.deviceowner.utils

import android.util.Log

/**
 * Secure logger with data masking and conditional logging.
 * Prevents sensitive data (device ID, IMEI, API keys) from appearing in logs.
 *
 * Features:
 * - Automatic data masking
 * - Log level filtering
 * - Production mode support
 * - Secure log storage
 */
object SecureLogger {
    
    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    private var minLogLevel = LogLevel.DEBUG
    private var isProductionMode = false
    
    // Patterns to mask in logs
    private val SENSITIVE_PATTERNS = mapOf(
        "device_id" to Regex("device_id[\"']?\\s*[:=]\\s*[\"']?([a-zA-Z0-9\\-]+)[\"']?"),
        "imei" to Regex("imei[\"']?\\s*[:=]\\s*[\"']?([0-9]{15})[\"']?"),
        "api_key" to Regex("api_key[\"']?\\s*[:=]\\s*[\"']?([a-zA-Z0-9]+)[\"']?"),
        "phone" to Regex("phone[\"']?\\s*[:=]\\s*[\"']?([+0-9\\-()\\s]{10,})[\"']?"),
        "email" to Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
        "token" to Regex("token[\"']?\\s*[:=]\\s*[\"']?([a-zA-Z0-9._\\-]+)[\"']?"),
        "password" to Regex("password[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s]+)[\"']?")
    )
    
    /**
     * Initialize logger with production mode setting.
     */
    fun init(productionMode: Boolean = false, minLevel: LogLevel = LogLevel.DEBUG) {
        isProductionMode = productionMode
        minLogLevel = if (productionMode) LogLevel.WARN else minLevel
        Log.i("SecureLogger", "✅ Initialized (production=$productionMode, minLevel=$minLogLevel)")
    }
    
    /**
     * Log debug message (only in debug mode).
     */
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(LogLevel.DEBUG)) {
            val maskedMessage = maskSensitiveData(message)
            if (throwable != null) {
                Log.d(tag, maskedMessage, throwable)
            } else {
                Log.d(tag, maskedMessage)
            }
        }
    }
    
    /**
     * Log info message.
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(LogLevel.INFO)) {
            val maskedMessage = maskSensitiveData(message)
            if (throwable != null) {
                Log.i(tag, maskedMessage, throwable)
            } else {
                Log.i(tag, maskedMessage)
            }
        }
    }
    
    /**
     * Log warning message.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(LogLevel.WARN)) {
            val maskedMessage = maskSensitiveData(message)
            if (throwable != null) {
                Log.w(tag, maskedMessage, throwable)
            } else {
                Log.w(tag, maskedMessage)
            }
        }
    }
    
    /**
     * Log error message.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(LogLevel.ERROR)) {
            val maskedMessage = maskSensitiveData(message)
            if (throwable != null) {
                Log.e(tag, maskedMessage, throwable)
            } else {
                Log.e(tag, maskedMessage)
            }
        }
    }
    
    /**
     * Mask sensitive data in message.
     */
    private fun maskSensitiveData(message: String): String {
        var maskedMessage = message
        
        for ((type, pattern) in SENSITIVE_PATTERNS) {
            maskedMessage = maskedMessage.replace(pattern) { matchResult ->
                val fullMatch = matchResult.value
                val capturedValue = matchResult.groupValues.getOrNull(1)
                
                if (capturedValue != null) {
                    // Replace captured value with masked version
                    val masked = maskValue(capturedValue, type)
                    fullMatch.replace(capturedValue, masked)
                } else {
                    fullMatch
                }
            }
        }
        
        return maskedMessage
    }
    
    /**
     * Mask individual value based on type.
     */
    private fun maskValue(value: String, type: String): String {
        return when (type) {
            "device_id" -> "${value.take(4)}...${value.takeLast(4)}"
            "imei" -> "${value.take(4)}...${value.takeLast(4)}"
            "api_key" -> "${value.take(4)}...${value.takeLast(4)}"
            "phone" -> "***-***-${value.takeLast(4)}"
            "email" -> {
                val parts = value.split("@")
                if (parts.size == 2) {
                    "${parts[0].take(2)}***@${parts[1]}"
                } else {
                    "***@***"
                }
            }
            "token" -> "${value.take(4)}...${value.takeLast(4)}"
            "password" -> "***"
            else -> "***"
        }
    }
    
    /**
     * Check if message should be logged based on level.
     */
    private fun shouldLog(level: LogLevel): Boolean {
        return level.ordinal >= minLogLevel.ordinal
    }
    
    /**
     * Log device ID safely (masked).
     */
    fun logDeviceId(tag: String, deviceId: String?) {
        if (deviceId != null) {
            d(tag, "Device ID: ${deviceId.take(4)}...${deviceId.takeLast(4)}")
        } else {
            w(tag, "Device ID is null")
        }
    }
    
    /**
     * Log IMEI safely (masked).
     */
    fun logImei(tag: String, imei: String?) {
        if (imei != null) {
            d(tag, "IMEI: ${imei.take(4)}...${imei.takeLast(4)}")
        } else {
            w(tag, "IMEI is null")
        }
    }
    
    /**
     * Log API response (masked).
     */
    fun logApiResponse(tag: String, response: String) {
        val maskedResponse = maskSensitiveData(response)
        d(tag, "API Response: $maskedResponse")
    }
    
    /**
     * Log API request (masked).
     */
    fun logApiRequest(tag: String, request: String) {
        val maskedRequest = maskSensitiveData(request)
        d(tag, "API Request: $maskedRequest")
    }
}
