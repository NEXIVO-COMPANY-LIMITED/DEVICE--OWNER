package com.deviceowner.utils

import android.content.Context
import android.widget.Toast
import java.io.IOException

/**
 * Error handling utilities for the Device Owner application.
 * Manages error types, messages, and recovery strategies.
 */
object ErrorHandler {

    // Error types
    enum class ErrorType {
        VALIDATION_ERROR,
        PERMISSION_ERROR,
        NETWORK_ERROR,
        DATABASE_ERROR,
        DEVICE_ERROR,
        RUNTIME_ERROR,
        UNKNOWN_ERROR
    }

    // Error messages
    private val errorMessages = mapOf(
        "INVALID_LOAN_ID" to "Loan ID must be in format LN-YYYYMMDD-XXXXX (e.g., LN-20260107-00001)",
        "INVALID_LOAN_ID_PREFIX" to "Loan ID must start with LN-",
        "INVALID_USER_ID" to "User ID must be in format U#YYYY-XXXXXX (e.g., U#2026-486272)",
        "INVALID_USER_ID_PREFIX" to "User ID must start with U#",
        "INVALID_SHOP_ID" to "Shop ID must be in format S#YYYY-XXXXXX (e.g., S#2026-31944)",
        "INVALID_SHOP_ID_PREFIX" to "Shop ID must start with S#",
        "INVALID_IMEI" to "IMEI must be 14-16 digits",
        "INVALID_SERIAL" to "Serial number cannot be empty",
        "EMPTY_FIELD" to "This field cannot be empty",
        "EMPTY_SCAN" to "Scanned value is empty. Please try again.",
        "INVALID_SCAN_TYPE" to "Invalid scan type. Please try again.",
        "NETWORK_ERROR" to "Network connection failed. Please try again.",
        "PERMISSION_DENIED" to "Permission denied. Please enable it in settings.",
        "CAMERA_PERMISSION_DENIED" to "Camera permission is required to use the scanner. Please enable it in app settings.",
        "SCANNER_PERMISSION_ERROR" to "Cannot access camera. Please check permissions and try again.",
        "DEVICE_ERROR" to "Device error occurred. Please restart the app.",
        "DATABASE_ERROR" to "Database error occurred. Please try again.",
        "UNKNOWN_ERROR" to "An unknown error occurred. Please try again."
    )

    /**
     * Get error message by error code
     */
    fun getErrorMessage(errorCode: String): String {
        return errorMessages[errorCode] ?: errorMessages["UNKNOWN_ERROR"] ?: "Unknown error"
    }

    /**
     * Show error toast with custom message
     */
    fun showErrorToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Show error toast by error code
     */
    fun showErrorToastByCode(context: Context, errorCode: String) {
        val message = getErrorMessage(errorCode)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Handle validation error
     */
    fun handleValidationError(
        context: Context,
        fieldName: String,
        errorCode: String,
        onError: (String) -> Unit
    ) {
        val message = getErrorMessage(errorCode)
        showErrorToast(context, message)
        onError(message)
    }

    /**
     * Handle permission error
     */
    fun handlePermissionError(
        context: Context,
        permission: String,
        onError: (String) -> Unit
    ) {
        val message = "Permission required: $permission"
        showErrorToast(context, message)
        onError(message)
    }

    /**
     * Handle scanner permission error
     */
    fun handleScannerPermissionError(
        context: Context,
        onError: (String) -> Unit
    ) {
        val message = getErrorMessage("CAMERA_PERMISSION_DENIED")
        showErrorToast(context, message)
        onError(message)
    }

    /**
     * Handle scanner access error
     */
    fun handleScannerAccessError(
        context: Context,
        exception: Exception? = null,
        onError: (String) -> Unit
    ) {
        val message = getErrorMessage("SCANNER_PERMISSION_ERROR")
        showErrorToast(context, message)
        if (exception != null) {
            logError("ScannerError", message, exception)
        }
        onError(message)
    }

    /**
     * Handle scanned value validation error
     */
    fun handleScanValidationError(
        context: Context,
        fieldName: String,
        errorCode: String,
        onError: (String) -> Unit
    ) {
        val message = getErrorMessage(errorCode)
        showErrorToast(context, "Invalid $fieldName: $message")
        onError(message)
    }

    /**
     * Handle network error
     */
    fun handleNetworkError(
        context: Context,
        exception: Exception,
        onError: (String) -> Unit
    ) {
        val message = when (exception) {
            is IOException -> "Network connection failed"
            else -> "Network error occurred"
        }
        showErrorToast(context, message)
        onError(message)
    }

    /**
     * Handle null pointer exception
     */
    fun handleNullPointerException(
        context: Context,
        fieldName: String,
        onError: (String) -> Unit
    ) {
        val message = "$fieldName is null or empty"
        showErrorToast(context, message)
        onError(message)
    }

    /**
     * Handle device error
     */
    fun handleDeviceError(
        context: Context,
        errorMessage: String,
        onError: (String) -> Unit
    ) {
        showErrorToast(context, errorMessage)
        onError(errorMessage)
    }

    /**
     * Handle runtime exception
     */
    fun handleRuntimeException(
        context: Context,
        exception: Exception,
        onError: (String) -> Unit
    ) {
        val message = exception.message ?: "Runtime error occurred"
        showErrorToast(context, message)
        onError(message)
    }

    /**
     * Log error for debugging
     */
    fun logError(tag: String, message: String, exception: Exception? = null) {
        android.util.Log.e(tag, message, exception)
    }

    /**
     * Get error type from exception
     */
    fun getErrorType(exception: Exception): ErrorType {
        return when (exception) {
            is IOException -> ErrorType.NETWORK_ERROR
            is NullPointerException -> ErrorType.RUNTIME_ERROR
            is SecurityException -> ErrorType.PERMISSION_ERROR
            else -> ErrorType.UNKNOWN_ERROR
        }
    }
}
