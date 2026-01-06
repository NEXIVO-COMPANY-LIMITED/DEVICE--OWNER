package com.deviceowner.error

/**
 * Sealed class representing different types of device control errors.
 * Each error type has a specific error code and message for tracking and debugging.
 */
sealed class DeviceControlError(
    val code: String,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Operation failed due to an unexpected error
     */
    class OperationFailed(
        code: String,
        message: String,
        cause: Throwable? = null
    ) : DeviceControlError(code, message, cause)

    /**
     * Operation failed due to missing permissions
     */
    class PermissionDenied(
        code: String,
        message: String,
        cause: Throwable? = null
    ) : DeviceControlError(code, message, cause)

    /**
     * App is not device owner
     */
    class DeviceOwnerNotActive(
        code: String,
        message: String,
        cause: Throwable? = null
    ) : DeviceControlError(code, message, cause)

    /**
     * Operation timed out
     */
    class OperationTimeout(
        code: String,
        message: String,
        cause: Throwable? = null
    ) : DeviceControlError(code, message, cause)

    /**
     * Network error occurred
     */
    class NetworkError(
        code: String,
        message: String,
        cause: Throwable? = null
    ) : DeviceControlError(code, message, cause)

    /**
     * Device policy manager error
     */
    class DevicePolicyError(
        code: String,
        message: String,
        cause: Throwable? = null
    ) : DeviceControlError(code, message, cause)

    /**
     * Invalid device state
     */
    class InvalidDeviceState(
        code: String,
        message: String,
        cause: Throwable? = null
    ) : DeviceControlError(code, message, cause)

    /**
     * Configuration error
     */
    class ConfigurationError(
        code: String,
        message: String,
        cause: Throwable? = null
    ) : DeviceControlError(code, message, cause)
}

/**
 * Error codes for device control operations
 */
object ErrorCodes {
    // Device Owner errors
    const val ERR_001 = "ERR_001" // App is not device owner
    const val ERR_002 = "ERR_002" // Failed to lock device
    const val ERR_003 = "ERR_003" // Failed to unlock device
    const val ERR_004 = "ERR_004" // Failed to set password
    const val ERR_005 = "ERR_005" // Failed to disable camera
    const val ERR_006 = "ERR_006" // Failed to disable USB
    const val ERR_007 = "ERR_007" // Failed to disable developer options
    const val ERR_008 = "ERR_008" // Failed to wipe device
    const val ERR_009 = "ERR_009" // Failed to reboot device

    // Permission errors
    const val ERR_010 = "ERR_010" // Permission denied
    const val ERR_011 = "ERR_011" // Missing required permission

    // Network errors
    const val ERR_020 = "ERR_020" // Network error
    const val ERR_021 = "ERR_021" // Backend communication failed
    const val ERR_022 = "ERR_022" // Timeout waiting for response

    // Device identification errors
    const val ERR_030 = "ERR_030" // Failed to collect device identifiers
    const val ERR_031 = "ERR_031" // Device fingerprint mismatch
    const val ERR_032 = "ERR_032" // Failed to verify fingerprint

    // Configuration errors
    const val ERR_040 = "ERR_040" // Invalid configuration
    const val ERR_041 = "ERR_041" // Missing configuration

    // Unknown errors
    const val ERR_999 = "ERR_999" // Unknown error
}
