package com.example.deviceowner.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Centralized API Configuration
 * Manages backend URL and API settings
 * 
 * Supports:
 * - BuildConfig-based configuration (compile-time)
 * - Runtime configuration (for testing/environment switching)
 * - Environment-specific settings
 */
object ApiConfig {
    
    private const val TAG = "ApiConfig"
    private const val PREFS_NAME = "api_config"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_VERSION = "api_version"
    
    /**
     * Get base URL for API calls
     * Priority: Runtime config > BuildConfig > Default
     */
    fun getBaseUrl(context: Context? = null): String {
        // Try runtime configuration first (for testing)
        context?.let {
            val prefs = it.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedUrl = prefs.getString(KEY_BASE_URL, null)
            if (savedUrl != null && savedUrl.isNotEmpty()) {
                Log.d(TAG, "Using runtime-configured base URL: $savedUrl")
                return savedUrl
            }
        }
        
        // Use BuildConfig (compile-time configuration)
        return try {
            // Use reflection to access BuildConfig to avoid compile-time dependency
            val buildConfigClass = Class.forName("com.example.deviceowner.BuildConfig")
            val baseUrlField = buildConfigClass.getField("BASE_URL")
            val buildConfigUrl = baseUrlField.get(null) as? String
            
            if (buildConfigUrl != null && buildConfigUrl.isNotEmpty()) {
                Log.d(TAG, "Using BuildConfig base URL: $buildConfigUrl")
                buildConfigUrl
            } else {
                getDefaultBaseUrl()
            }
        } catch (e: Exception) {
            Log.w(TAG, "BuildConfig.BASE_URL not available, using default", e)
            getDefaultBaseUrl()
        }
    }
    
    /**
     * Get default base URL (fallback)
     * Using HTTP for now (HTTPS not configured yet)
     */
    private fun getDefaultBaseUrl(): String {
        // Using HTTP - Change to HTTPS when ready
        return "http://82.29.168.120/"
    }
    
    /**
     * Set base URL at runtime (for testing or environment switching)
     * WARNING: Only use for testing! Production should use BuildConfig
     */
    fun setBaseUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BASE_URL, url).apply()
        Log.d(TAG, "Base URL updated to: $url")
    }
    
    /**
     * Clear runtime base URL (revert to BuildConfig)
     */
    fun clearBaseUrl(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_BASE_URL).apply()
        Log.d(TAG, "Base URL cleared, will use BuildConfig")
    }
    
    /**
     * Get API version
     */
    fun getApiVersion(context: Context? = null): String {
        context?.let {
            val prefs = it.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedVersion = prefs.getString(KEY_API_VERSION, null)
            if (savedVersion != null) {
                return savedVersion
            }
        }
        
        return try {
            val buildConfigClass = Class.forName("com.example.deviceowner.BuildConfig")
            val apiVersionField = buildConfigClass.getField("API_VERSION")
            apiVersionField.get(null) as? String ?: "v1"
        } catch (e: Exception) {
            "v1"
        }
    }
    
    /**
     * Check if logging is enabled
     */
    fun isLoggingEnabled(): Boolean {
        return try {
            val buildConfigClass = Class.forName("com.example.deviceowner.BuildConfig")
            val loggingField = buildConfigClass.getField("ENABLE_LOGGING")
            loggingField.get(null) as? Boolean ?: false
        } catch (e: Exception) {
            false // Default to false for production safety
        }
    }
    
    /**
     * Get full API endpoint URL
     */
    fun getApiEndpoint(context: Context? = null, endpoint: String): String {
        val baseUrl = getBaseUrl(context)
        val apiVersion = getApiVersion(context)
        
        // Ensure base URL ends with /
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        
        // Ensure endpoint starts with /
        val normalizedEndpoint = if (endpoint.startsWith("/")) endpoint else "/$endpoint"
        
        return "$normalizedBase$apiVersion$normalizedEndpoint"
    }
}
