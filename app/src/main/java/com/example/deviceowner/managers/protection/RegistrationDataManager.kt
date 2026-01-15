package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import java.io.File

/**
 * Manages device registration data storage and retrieval
 * Stores all comparison data collected during registration
 * Used for future tamper detection comparisons
 */
class RegistrationDataManager(private val context: Context) {
    
    companion object {
        private const val TAG = "RegistrationDataManager"
        private const val PREFS_NAME = "registration_data"
        private const val KEY_REGISTRATION_DATA = "registration_data"
        private const val CACHE_REGISTRATION_FILE = "reg_data.cache"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Protected cache directory
    private val protectedCacheDir: File by lazy {
        val cacheDir = File(context.cacheDir, "protected_registration_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            cacheDir.setReadable(false, false)
            cacheDir.setReadable(true, true)
            cacheDir.setWritable(false, false)
            cacheDir.setWritable(true, true)
            cacheDir.setExecutable(false, false)
            cacheDir.setExecutable(true, true)
        }
        cacheDir
    }
    
    /**
     * Save registration data locally
     * Called after successful backend registration
     */
    fun saveRegistrationData(data: com.example.deviceowner.data.api.DeviceRegistrationPayload) {
        try {
            val json = gson.toJson(data)
            
            // Save to protected cache file
            val cacheFile = File(protectedCacheDir, CACHE_REGISTRATION_FILE)
            cacheFile.writeText(json)
            setFileProtection(cacheFile)
            
            // Also save to SharedPreferences as backup
            prefs.edit().apply {
                putString(KEY_REGISTRATION_DATA, json)
                putLong("registration_time", System.currentTimeMillis())
                apply()
            }
            
            Log.d(TAG, "Registration data saved to protected cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving registration data", e)
        }
    }
    
    /**
     * Get stored registration data
     */
    fun getRegistrationData(): com.example.deviceowner.data.api.DeviceRegistrationPayload? {
        return try {
            // Try to read from protected cache first
            val cacheFile = File(protectedCacheDir, CACHE_REGISTRATION_FILE)
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                return gson.fromJson(json, com.example.deviceowner.data.api.DeviceRegistrationPayload::class.java)
            }
            
            // Fallback to SharedPreferences
            val json = prefs.getString(KEY_REGISTRATION_DATA, null) ?: return null
            gson.fromJson(json, com.example.deviceowner.data.api.DeviceRegistrationPayload::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving registration data", e)
            null
        }
    }
    
    /**
     * Set file protection
     */
    private fun setFileProtection(file: File) {
        try {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
            file.setExecutable(false, false)
            file.setExecutable(false, true)
            Log.d(TAG, "File protection set: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting file protection: ${e.message}")
        }
    }
}

/**
 * Data class for displaying registration information
 * Only shows essential information to user
 */
data class RegistrationDisplayData(
    val deviceId: String,
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val registrationTime: Long,
    val verificationStatus: String,
    val isTrusted: Boolean
)
