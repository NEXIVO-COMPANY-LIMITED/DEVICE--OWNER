package com.example.deviceowner.data.api

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * OkHttp interceptor for adding security headers and request signing
 * 
 * Responsibilities:
 * - Add API version headers
 * - Add timestamp for request tracking
 * - Add nonce for replay protection
 * - Log request/response details
 */
class SecurityInterceptor : Interceptor {

    companion object {
        private const val TAG = "SecurityInterceptor"
        
        // Custom headers
        private const val HEADER_TIMESTAMP = "X-Timestamp"
        private const val HEADER_NONCE = "X-Nonce"
        private const val HEADER_API_VERSION = "X-API-Version"
        private const val HEADER_REQUEST_ID = "X-Request-ID"
        
        private const val API_VERSION = "1.0"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        
        try {
            // Add security headers
            request = addSecurityHeaders(request)
            
            Log.d(TAG, "→ Request: ${request.method} ${request.url}")
            Log.d(TAG, "→ Headers: ${request.headers}")
            
            // Proceed with request
            val response = chain.proceed(request)
            
            Log.d(TAG, "← Response: ${response.code} ${response.message}")
            
            return response
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error in security interceptor: ${e.message}", e)
            throw e
        }
    }

    /**
     * Add security headers to request
     */
    private fun addSecurityHeaders(request: Request): Request {
        return try {
            val builder = request.newBuilder()
            
            // Add API version
            builder.addHeader(HEADER_API_VERSION, API_VERSION)
            
            // Add timestamp
            val timestamp = System.currentTimeMillis()
            builder.addHeader(HEADER_TIMESTAMP, timestamp.toString())
            
            // Add nonce for replay protection
            val nonce = generateNonce()
            builder.addHeader(HEADER_NONCE, nonce)
            
            // Add request ID for tracking
            val requestId = generateRequestId()
            builder.addHeader(HEADER_REQUEST_ID, requestId)
            
            builder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding security headers: ${e.message}", e)
            request
        }
    }

    /**
     * Generate a unique nonce for replay protection
     */
    private fun generateNonce(): String {
        return java.util.UUID.randomUUID().toString()
    }

    /**
     * Generate a unique request ID for tracking
     */
    private fun generateRequestId(): String {
        return "${System.currentTimeMillis()}-${(Math.random() * 10000).toInt()}"
    }
}
