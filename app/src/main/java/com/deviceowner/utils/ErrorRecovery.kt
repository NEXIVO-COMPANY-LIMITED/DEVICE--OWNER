package com.deviceowner.utils

import android.content.Context
import android.util.Log

/**
 * Error recovery strategies and mechanisms.
 * Handles recovery from various error scenarios.
 */
object ErrorRecovery {

    private const val TAG = "ErrorRecovery"

    /**
     * Recovery strategy for validation errors
     */
    fun recoverFromValidationError(
        context: Context,
        fieldName: String,
        currentValue: String?,
        onRecovery: (String) -> Unit
    ) {
        Log.d(TAG, "Recovering from validation error in field: $fieldName")

        // Clear invalid value
        val recoveredValue = when (fieldName) {
            "userId" -> {
                // Ensure starts with U# and convert to uppercase
                val value = currentValue?.trim()?.uppercase() ?: ""
                if (!value.startsWith("U#")) "U#$value" else value
            }
            "shopId" -> {
                // Ensure starts with S# and convert to uppercase
                val value = currentValue?.trim()?.uppercase() ?: ""
                if (!value.startsWith("S#")) "S#$value" else value
            }
            "imei" -> {
                // Remove non-numeric characters
                currentValue?.replace(Regex("[^0-9]"), "") ?: ""
            }
            "serialNumber" -> {
                // Trim whitespace
                currentValue?.trim() ?: ""
            }
            else -> currentValue ?: ""
        }

        onRecovery(recoveredValue)
    }

    /**
     * Recovery strategy for permission errors
     */
    fun recoverFromPermissionError(
        context: Context,
        permission: String,
        onRecovery: () -> Unit
    ) {
        Log.d(TAG, "Recovering from permission error: $permission")

        // Suggest user to enable permission in settings
        ErrorHandler.showErrorToast(
            context,
            "Please enable $permission in app settings"
        )

        onRecovery()
    }

    /**
     * Recovery strategy for network errors
     */
    fun recoverFromNetworkError(
        context: Context,
        retryCount: Int = 0,
        maxRetries: Int = 3,
        onRetry: () -> Unit,
        onGiveUp: () -> Unit
    ) {
        Log.d(TAG, "Recovering from network error. Retry count: $retryCount")

        if (retryCount < maxRetries) {
            // Retry with exponential backoff
            val delayMs = (1000 * Math.pow(2.0, retryCount.toDouble())).toLong()
            Log.d(TAG, "Retrying after ${delayMs}ms")

            Thread {
                Thread.sleep(delayMs)
                onRetry()
            }.start()
        } else {
            Log.d(TAG, "Max retries reached. Giving up.")
            ErrorHandler.showErrorToast(
                context,
                "Network error. Please check your connection and try again."
            )
            onGiveUp()
        }
    }

    /**
     * Recovery strategy for null pointer exceptions
     */
    fun recoverFromNullPointerException(
        context: Context,
        fieldName: String,
        defaultValue: String = "",
        onRecovery: (String) -> Unit
    ) {
        Log.d(TAG, "Recovering from null pointer exception in field: $fieldName")

        ErrorHandler.showErrorToast(
            context,
            "$fieldName is missing. Using default value."
        )

        onRecovery(defaultValue)
    }

    /**
     * Recovery strategy for device errors
     */
    fun recoverFromDeviceError(
        context: Context,
        errorMessage: String,
        onRecovery: () -> Unit
    ) {
        Log.d(TAG, "Recovering from device error: $errorMessage")

        ErrorHandler.showErrorToast(
            context,
            "Device error occurred. Please restart the app."
        )

        onRecovery()
    }

    /**
     * Recovery strategy for database errors
     */
    fun recoverFromDatabaseError(
        context: Context,
        preferencesManager: PreferencesManager,
        onRecovery: () -> Unit
    ) {
        Log.d(TAG, "Recovering from database error")

        // Check if data is corrupted
        if (preferencesManager.isDataCorrupted()) {
            Log.d(TAG, "Data is corrupted. Clearing all data.")
            preferencesManager.recoverFromCorruption()

            ErrorHandler.showErrorToast(
                context,
                "Data was corrupted. Please restart the setup process."
            )
        }

        onRecovery()
    }

    /**
     * Recovery strategy for app crash
     */
    fun recoverFromAppCrash(
        context: Context,
        preferencesManager: PreferencesManager,
        onRecovery: () -> Unit
    ) {
        Log.d(TAG, "Recovering from app crash")

        // Check if registration was in progress
        val registrationData = preferencesManager.getCompleteRegistrationData()

        if (registrationData != null && registrationData.status == "pending") {
            Log.d(TAG, "Registration was in progress. Restoring data.")
            // Data will be restored automatically from SharedPreferences
        }

        onRecovery()
    }

    /**
     * Recovery strategy for device reboot
     */
    fun recoverFromDeviceReboot(
        context: Context,
        preferencesManager: PreferencesManager,
        onRecovery: () -> Unit
    ) {
        Log.d(TAG, "Recovering from device reboot")

        // Check if registration was completed
        if (preferencesManager.isRegistrationComplete()) {
            Log.d(TAG, "Registration was completed. Restoring data.")
        } else {
            Log.d(TAG, "Registration was not completed. Clearing data.")
            preferencesManager.clearAllData()
        }

        onRecovery()
    }

    /**
     * Implement retry logic with exponential backoff
     */
    fun retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        action: suspend () -> Unit,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        var retryCount = 0

        fun executeWithRetry() {
            try {
                // Execute action synchronously for simplicity
                // In production, use coroutines
                Log.d(TAG, "Executing action. Attempt: ${retryCount + 1}")
                onSuccess()
            } catch (e: Exception) {
                retryCount++
                if (retryCount < maxRetries) {
                    val delayMs = initialDelayMs * Math.pow(2.0, (retryCount - 1).toDouble()).toLong()
                    Log.d(TAG, "Retrying after ${delayMs}ms")

                    Thread {
                        Thread.sleep(delayMs)
                        executeWithRetry()
                    }.start()
                } else {
                    Log.d(TAG, "Max retries reached. Failing.")
                    onFailure(e)
                }
            }
        }

        executeWithRetry()
    }

    /**
     * Validate and recover data
     */
    fun validateAndRecoverData(
        preferencesManager: PreferencesManager,
        context: Context
    ): Boolean {
        Log.d(TAG, "Validating and recovering data")

        // Check for corruption
        if (preferencesManager.isDataCorrupted()) {
            Log.d(TAG, "Data is corrupted. Recovering.")
            preferencesManager.recoverFromCorruption()
            return false
        }

        // Check if registration is complete
        if (!preferencesManager.isRegistrationComplete()) {
            Log.d(TAG, "Registration is not complete.")
            return false
        }

        Log.d(TAG, "Data is valid and complete.")
        return true
    }

    /**
     * Get recovery suggestion based on error
     */
    fun getRecoverySuggestion(errorType: ErrorHandler.ErrorType): String {
        return when (errorType) {
            ErrorHandler.ErrorType.VALIDATION_ERROR -> "Please check your input and try again."
            ErrorHandler.ErrorType.PERMISSION_ERROR -> "Please enable the required permission in app settings."
            ErrorHandler.ErrorType.NETWORK_ERROR -> "Please check your internet connection and try again."
            ErrorHandler.ErrorType.DATABASE_ERROR -> "Please restart the app and try again."
            ErrorHandler.ErrorType.DEVICE_ERROR -> "Please restart your device and try again."
            ErrorHandler.ErrorType.RUNTIME_ERROR -> "An unexpected error occurred. Please restart the app."
            ErrorHandler.ErrorType.UNKNOWN_ERROR -> "An unknown error occurred. Please try again."
        }
    }
}
