package com.example.deviceowner.data

import android.util.Log

/**
 * Device ID validation utility.
 * Provides comprehensive validation for device IDs across the application.
 */
object DeviceIdValidator {
    private const val TAG = "DeviceIdValidator"
    
    /**
     * Validation result with detailed error information.
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }
    
    /**
     * Validate device ID with detailed error reporting.
     */
    fun validate(deviceId: String?): ValidationResult {
        return when {
            deviceId == null -> {
                ValidationResult.Invalid("Device ID is null")
            }
            deviceId.isBlank() -> {
                ValidationResult.Invalid("Device ID is blank")
            }
            deviceId.equals("unknown", ignoreCase = true) -> {
                ValidationResult.Invalid("Device ID is 'unknown' (locally generated)")
            }
            deviceId.startsWith("ANDROID-", ignoreCase = true) -> {
                ValidationResult.Invalid("Device ID appears to be locally generated: $deviceId")
            }
            !deviceId.matches(Regex("^[a-zA-Z0-9\\-]{8,}$")) -> {
                ValidationResult.Invalid("Device ID format invalid (must be 8+ alphanumeric/dash): $deviceId")
            }
            else -> ValidationResult.Valid
        }
    }
    
    /**
     * Check if device ID is valid (convenience method).
     */
    fun isValid(deviceId: String?): Boolean {
        return validate(deviceId) is ValidationResult.Valid
    }
    
    /**
     * Get validation error message (or null if valid).
     */
    fun getErrorMessage(deviceId: String?): String? {
        return when (val result = validate(deviceId)) {
            is ValidationResult.Valid -> null
            is ValidationResult.Invalid -> result.reason
        }
    }
    
    /**
     * Validate and log result.
     */
    fun validateAndLog(deviceId: String?, context: String = ""): Boolean {
        val result = validate(deviceId)
        return when (result) {
            is ValidationResult.Valid -> {
                Log.d(TAG, "✅ Device ID valid $context: ${deviceId?.take(8)}...")
                true
            }
            is ValidationResult.Invalid -> {
                Log.w(TAG, "❌ Device ID invalid $context: ${result.reason}")
                false
            }
        }
    }
    
    /**
     * Validate multiple device IDs (e.g., from different sources).
     * Returns the first valid one or null.
     */
    fun findFirstValid(vararg deviceIds: String?): String? {
        for (deviceId in deviceIds) {
            if (isValid(deviceId)) {
                Log.d(TAG, "✅ Found valid device ID: ${deviceId?.take(8)}...")
                return deviceId
            }
        }
        Log.w(TAG, "❌ No valid device ID found among ${deviceIds.size} candidates")
        return null
    }
}
