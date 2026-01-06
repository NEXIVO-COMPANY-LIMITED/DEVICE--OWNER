package com.example.deviceowner.data.api

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retry interceptor with exponential backoff for failed requests
 */
class RetryInterceptor : Interceptor {
    private val TAG = "RetryInterceptor"
    private val maxRetry = 3
    private val initialDelayMs = 1000L
    
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var response: Response? = null
        var exception: IOException? = null
        
        for (attempt in 1..maxRetry) {
            try {
                response = chain.proceed(request)
                
                // Retry on specific status codes
                if (response.code in listOf(408, 429, 500, 502, 503, 504)) {
                    Log.w(TAG, "Attempt $attempt: Received status ${response.code}, retrying...")
                    response.close()
                    
                    if (attempt < maxRetry) {
                        val delayMs = initialDelayMs * (1L shl (attempt - 1)) // Exponential backoff
                        Thread.sleep(delayMs)
                        continue
                    }
                }
                
                return response
            } catch (e: IOException) {
                exception = e
                Log.w(TAG, "Attempt $attempt: Network error - ${e.message}, retrying...")
                
                if (attempt < maxRetry) {
                    val delayMs = initialDelayMs * (1L shl (attempt - 1))
                    Thread.sleep(delayMs)
                } else {
                    throw e
                }
            }
        }
        
        return response ?: throw exception ?: IOException("Max retries exceeded")
    }
}
