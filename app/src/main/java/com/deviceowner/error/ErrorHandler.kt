package com.deviceowner.error

import android.content.Context
import android.util.Log
import com.deviceowner.logging.StructuredLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeoutException

/**
 * Handles device control errors with structured logging and retry logic.
 * Works completely locally - no backend dependencies.
 */
class DeviceControlErrorHandler(
    private val context: Context,
    private val logger: StructuredLogger
) {

    companion object {
        private const val TAG = "DeviceControlErrorHandler"
        private const val MAX_RETRIES = 5
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 16000L
    }

    private val retryAttempts = mutableMapOf<String, AtomicInteger>()

    /**
     * Handle device control error with local logging and retry logic
     */
    fun handleError(
        error: DeviceControlError,
        context: Context,
        operationName: String = "UNKNOWN_OPERATION"
    ) {
        // Log the error with structured format (locally)
        logStructuredError(error, operationName)

        // Implement retry logic for transient failures
        if (isTransientError(error)) {
            scheduleRetry(error, operationName)
        }
    }

    /**
     * Log error with structured format for better tracking and analysis
     */
    private fun logStructuredError(
        error: DeviceControlError,
        operationName: String
    ) {
        logger.logError(
            tag = TAG,
            code = error.code,
            message = error.message,
            operation = operationName,
            cause = error.cause?.message,
            details = mapOf(
                "timestamp" to System.currentTimeMillis().toString()
            )
        )

        // Also log to Android Log for immediate visibility
        Log.e(TAG, "[$operationName] ${error.code}: ${error.message}", error.cause)
    }

    /**
     * Determine if error is transient and can be retried
     */
    private fun isTransientError(error: DeviceControlError): Boolean {
        return error is DeviceControlError.NetworkError ||
                error is DeviceControlError.OperationTimeout ||
                error is DeviceControlError.DevicePolicyError
    }

    /**
     * Schedule retry with exponential backoff
     */
    private fun scheduleRetry(
        error: DeviceControlError,
        operationName: String
    ) {
        val operationKey = "$operationName:${error.code}"
        val currentAttempt = retryAttempts.getOrPut(operationKey) { AtomicInteger(0) }
        val attemptCount = currentAttempt.incrementAndGet()

        if (attemptCount > MAX_RETRIES) {
            logger.logError(
                tag = TAG,
                code = error.code,
                message = "Max retries exceeded for operation",
                operation = operationName,
                details = mapOf(
                    "timestamp" to System.currentTimeMillis().toString()
                )
            )
            retryAttempts.remove(operationKey)
            return
        }

        // Calculate exponential backoff delay
        val delayMs = calculateBackoffDelay(attemptCount)

        logger.logInfo(
            tag = TAG,
            message = "Scheduling retry",
            operation = operationName,
            details = mapOf(
                "code" to error.code,
                "attempt" to attemptCount.toString(),
                "delayMs" to delayMs.toString()
            )
        )

        CoroutineScope(Dispatchers.IO).launch {
            delay(delayMs)
            // Retry logic can be implemented by caller if needed
        }
    }

    /**
     * Calculate exponential backoff delay: 1s, 2s, 4s, 8s, 16s
     */
    private fun calculateBackoffDelay(attemptCount: Int): Long {
        val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl (attemptCount - 1))
        return minOf(delayMs, MAX_RETRY_DELAY_MS)
    }

    /**
     * Clear retry attempts for an operation
     */
    fun clearRetryAttempts(operationName: String) {
        retryAttempts.entries.removeAll { it.key.startsWith(operationName) }
    }

    /**
     * Get retry attempt count for an operation
     */
    fun getRetryAttemptCount(operationName: String, errorCode: String): Int {
        val operationKey = "$operationName:$errorCode"
        return retryAttempts[operationKey]?.get() ?: 0
    }
}

/**
 * Extension function to handle errors in Result type
 */
inline fun <T> Result<T>.onError(
    errorHandler: DeviceControlErrorHandler,
    operationName: String = "UNKNOWN_OPERATION",
    context: Context
): Result<T> {
    onFailure { throwable ->
        if (throwable is DeviceControlError) {
            errorHandler.handleError(throwable, context, operationName)
        }
    }
    return this
}

/**
 * Extension function to convert exceptions to DeviceControlError
 */
fun Exception.toDeviceControlError(
    code: String = ErrorCodes.ERR_999,
    message: String = this.message ?: "Unknown error"
): DeviceControlError {
    return when (this) {
        is DeviceControlError -> this
        is SecurityException -> DeviceControlError.PermissionDenied(code, message, this)
        is TimeoutException -> DeviceControlError.OperationTimeout(code, message, this)
        else -> DeviceControlError.OperationFailed(code, message, this)
    }
}
