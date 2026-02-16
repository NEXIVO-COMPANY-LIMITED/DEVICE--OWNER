package com.example.deviceowner.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Secure configuration manager for sensitive data like API keys.
 * Uses EncryptedSharedPreferences to store sensitive configuration.
 *
 * Features:
 * - Encrypted storage using Android Keystore
 * - Automatic key rotation support
 * - Fallback to secure configuration server
 * - Cache with TTL
 */
object SecureConfigManager {
    private const val TAG = "SecureConfigManager"
    
    private const val PREFS_NAME = "secure_config"
    private const val KEY_API_KEY = "device_api_key"
    private const val KEY_API_KEY_TIMESTAMP = "device_api_key_timestamp"
    private const val KEY_CONFIG_SERVER_URL = "config_server_url"
    
    // Cache with TTL
    private var cachedApiKey: String? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_TTL_MS = 3600_000L // 1 hour
    
    private val lock = ReentrantReadWriteLock()
    
    private fun getEncryptedPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create encrypted preferences: ${e.message}", e)
            // Fallback to regular preferences (less secure but functional)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    /**
     * Get API key with caching and fallback mechanisms.
     * Priority:
     * 1. Cache (if not expired)
     * 2. Encrypted local storage
     * 3. Configuration server (if available)
     * 4. Fallback to AppConfig (for backward compatibility)
     */
    fun getApiKey(context: Context): String? {
        lock.readLock().lock()
        try {
            // Check cache first
            if (cachedApiKey != null && System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) {
                Log.d(TAG, "✅ API key from cache")
                return cachedApiKey
            }
        } finally {
            lock.readLock().unlock()
        }
        
        // Cache miss - fetch from storage
        lock.writeLock().lock()
        try {
            // Try encrypted local storage first
            val prefs = getEncryptedPreferences(context)
            var apiKey = prefs.getString(KEY_API_KEY, null)
            
            if (apiKey != null) {
                Log.d(TAG, "✅ API key from encrypted storage")
                cachedApiKey = apiKey
                cacheTimestamp = System.currentTimeMillis()
                return apiKey
            }
            
            // Try configuration server
            apiKey = fetchFromConfigServer(context)
            if (apiKey != null) {
                Log.d(TAG, "✅ API key from configuration server")
                saveApiKey(context, apiKey)
                cachedApiKey = apiKey
                cacheTimestamp = System.currentTimeMillis()
                return apiKey
            }
            
            // Fallback to AppConfig (for backward compatibility)
            // TODO: Remove this after migration period
            apiKey = com.example.deviceowner.AppConfig.DEVICE_API_KEY
            if (apiKey.isNotEmpty() && !apiKey.contains("8f3d2c9a")) { // Check if not placeholder
                Log.w(TAG, "⚠️ Using API key from AppConfig (should migrate to secure storage)")
                saveApiKey(context, apiKey)
                cachedApiKey = apiKey
                cacheTimestamp = System.currentTimeMillis()
                return apiKey
            }
            
            Log.e(TAG, "❌ No API key available")
            return null
        } finally {
            lock.writeLock().unlock()
        }
    }
    
    /**
     * Save API key to encrypted storage.
     */
    fun saveApiKey(context: Context, apiKey: String) {
        if (apiKey.isBlank()) {
            Log.e(TAG, "❌ Attempted to save blank API key")
            return
        }
        
        lock.writeLock().lock()
        try {
            val prefs = getEncryptedPreferences(context)
            prefs.edit().apply {
                putString(KEY_API_KEY, apiKey)
                putLong(KEY_API_KEY_TIMESTAMP, System.currentTimeMillis())
                apply()
            }
            
            // Update cache
            cachedApiKey = apiKey
            cacheTimestamp = System.currentTimeMillis()
            
            Log.i(TAG, "✅ API key saved to encrypted storage")
        } finally {
            lock.writeLock().unlock()
        }
    }
    
    /**
     * Clear cached API key (e.g., on logout or key rotation).
     */
    fun clearCache() {
        lock.writeLock().lock()
        try {
            cachedApiKey = null
            cacheTimestamp = 0
            Log.d(TAG, "🗑️ API key cache cleared")
        } finally {
            lock.writeLock().unlock()
        }
    }
    
    /**
     * Rotate API key (fetch new one from server).
     */
    fun rotateApiKey(context: Context): Boolean {
        lock.writeLock().lock()
        try {
            val newApiKey = fetchFromConfigServer(context) ?: return false
            saveApiKey(context, newApiKey)
            Log.i(TAG, "✅ API key rotated successfully")
            return true
        } finally {
            lock.writeLock().unlock()
        }
    }
    
    /**
     * Fetch API key from configuration server.
     * This is a placeholder - implement actual server communication.
     */
    private fun fetchFromConfigServer(context: Context): String? {
        return try {
            // TODO: Implement actual configuration server communication
            // This should:
            // 1. Use device ID for authentication
            // 2. Verify server certificate
            // 3. Implement timeout
            // 4. Handle errors gracefully
            
            Log.d(TAG, "📡 Fetching API key from configuration server...")
            
            // Placeholder - return null to fall back to other sources
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch from config server: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get configuration server URL.
     */
    fun getConfigServerUrl(context: Context): String? {
        val prefs = getEncryptedPreferences(context)
        return prefs.getString(KEY_CONFIG_SERVER_URL, null)
    }
    
    /**
     * Set configuration server URL.
     */
    fun setConfigServerUrl(context: Context, url: String) {
        val prefs = getEncryptedPreferences(context)
        prefs.edit().putString(KEY_CONFIG_SERVER_URL, url).apply()
        Log.d(TAG, "✅ Configuration server URL set")
    }
}
