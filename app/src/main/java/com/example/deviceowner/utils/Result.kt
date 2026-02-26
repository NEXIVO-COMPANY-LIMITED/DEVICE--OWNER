package com.microspace.payo.utils

/**
 * Sealed Result type for consistent error handling across the application.
 * Replaces try-catch and Result.retry() with type-safe error handling.
 *
 * Usage:
 * ```
 * when (val result = someOperation()) {
 *     is Result.Success -> handleSuccess(result.data)
 *     is Result.Error -> handleError(result.exception)
 *     is Result.Retry -> retryOperation()
 * }
 * ```
 */
sealed class Result<out T> {
    /**
     * Operation succeeded with data.
     */
    data class Success<T>(val data: T) : Result<T>()
    
    /**
     * Operation failed with exception.
     */
    data class Error<T>(
        val exception: Exception,
        val message: String = exception.message ?: "Unknown error",
        val code: Int = 0
    ) : Result<T>()
    
    /**
     * Operation should be retried.
     * Includes retry count and backoff delay.
     */
    data class Retry<T>(
        val reason: String,
        val retryCount: Int = 0,
        val backoffMs: Long = 0,
        val maxRetries: Int = 3
    ) : Result<T>()
    
    /**
     * Operation was cancelled.
     */
    data class Cancelled<T>(val reason: String = "Operation cancelled") : Result<T>()
    
    /**
     * Map success value to another type.
     */
    inline fun <R> map(transform: (T) -> R): Result<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Error -> Error(exception, message, code)
            is Retry -> Retry(reason, retryCount, backoffMs, maxRetries)
            is Cancelled -> Cancelled(reason)
        }
    }
    
    /**
     * Flat map for chaining operations.
     */
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> {
        return when (this) {
            is Success -> transform(data)
            is Error -> Error(exception, message, code)
            is Retry -> Retry(reason, retryCount, backoffMs, maxRetries)
            is Cancelled -> Cancelled(reason)
        }
    }
    
    /**
     * Get data or null.
     */
    fun getOrNull(): T? = (this as? Success)?.data
    
    /**
     * Get exception or null.
     */
    fun exceptionOrNull(): Exception? = (this as? Error)?.exception
    
    /**
     * Check if result is success.
     */
    fun isSuccess(): Boolean = this is Success
    
    /**
     * Check if result is error.
     */
    fun isError(): Boolean = this is Error
    
    /**
     * Check if result should be retried.
     */
    fun shouldRetry(): Boolean = this is Retry && retryCount < maxRetries
    
    /**
     * Execute block if success.
     */
    inline fun onSuccess(block: (T) -> Unit): Result<T> {
        if (this is Success) block(data)
        return this
    }
    
    /**
     * Execute block if error.
     */
    inline fun onError(block: (Exception) -> Unit): Result<T> {
        if (this is Error) block(exception)
        return this
    }
    
    /**
     * Execute block if should retry.
     */
    inline fun onRetry(block: (String) -> Unit): Result<T> {
        if (this is Retry) block(reason)
        return this
    }
    
    /**
     * Get data or throw exception.
     */
    fun getOrThrow(): T {
        return when (this) {
            is Success -> data
            is Error -> throw exception
            is Retry -> throw IllegalStateException("Operation should be retried: $reason")
            is Cancelled -> throw IllegalStateException(reason)
        }
    }
    
    /**
     * Get data or default value.
     */
    fun getOrDefault(default: @UnsafeVariance T): T {
        return (this as? Success)?.data ?: default
    }
    
    companion object {
        /**
         * Create success result.
         */
        fun <T> success(data: T): Result<T> = Success(data)
        
        /**
         * Create error result.
         */
        fun <T> error(exception: Exception, code: Int = 0): Result<T> {
            return Error(exception, exception.message ?: "Unknown error", code)
        }
        
        /**
         * Create error result from message.
         */
        fun <T> error(message: String, code: Int = 0): Result<T> {
            return Error(Exception(message), message, code)
        }
        
        /**
         * Create retry result.
         */
        fun <T> retry(
            reason: String,
            retryCount: Int = 0,
            backoffMs: Long = 0,
            maxRetries: Int = 3
        ): Result<T> = Retry(reason, retryCount, backoffMs, maxRetries)
        
        /**
         * Create cancelled result.
         */
        fun <T> cancelled(reason: String = "Operation cancelled"): Result<T> = Cancelled(reason)
        
        /**
         * Execute block and wrap result.
         */
        inline fun <T> of(block: () -> T): Result<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Error(e)
            }
        }
    }
}

/**
 * Extension function for Result<Unit>.
 */
fun Result<Unit>.isSuccessful(): Boolean = this is Result.Success
