package com.deviceowner.compatibility

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Manages compatibility state and caching for offline enforcement
 */
class CompatibilityStateManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("compatibility_state", Context.MODE_PRIVATE)
    
    companion object {
        private const val PREF_LAST_CHECK = "last_compatibility_check"
        private const val PREF_COMPATIBILITY_STATUS = "compatibility_status"
        private const val PREF_DEVICE_INFO = "device_info"
        private const val PREF_LAST_SYNC = "last_sync_time"
        private const val CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    /**
     * Store compatibility check result
     */
    fun storeCompatibilityCheck(result: CompatibilityCheckResult, deviceInfo: DeviceCompatibility) {
        val json = JSONObject().apply {
            put("compatible", result.compatible)
            put("errors", result.errors.joinToString("|"))
            put("warnings", result.warnings.joinToString("|"))
            put("recommendations", result.recommendations.joinToString("|"))
            put("timestamp", System.currentTimeMillis())
        }
        
        val deviceJson = JSONObject().apply {
            put("manufacturer", deviceInfo.manufacturer)
            put("model", deviceInfo.model)
            put("osVersion", deviceInfo.osVersion)
            put("apiLevel", deviceInfo.apiLevel)
            put("features", deviceInfo.features.joinToString("|"))
            put("issues", deviceInfo.issues.joinToString("|"))
        }
        
        prefs.edit().apply {
            putString(PREF_COMPATIBILITY_STATUS, json.toString())
            putString(PREF_DEVICE_INFO, deviceJson.toString())
            putLong(PREF_LAST_CHECK, System.currentTimeMillis())
        }.apply()
    }
    
    /**
     * Get cached compatibility check result
     */
    fun getCachedCompatibilityCheck(): CompatibilityCheckResult? {
        val json = prefs.getString(PREF_COMPATIBILITY_STATUS, null) ?: return null
        
        return try {
            val obj = JSONObject(json)
            CompatibilityCheckResult(
                compatible = obj.optBoolean("compatible", false),
                errors = obj.optString("errors", "").split("|").filter { it.isNotEmpty() },
                warnings = obj.optString("warnings", "").split("|").filter { it.isNotEmpty() },
                recommendations = obj.optString("recommendations", "").split("|").filter { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get cached device info
     */
    fun getCachedDeviceInfo(): DeviceCompatibility? {
        val json = prefs.getString(PREF_DEVICE_INFO, null) ?: return null
        
        return try {
            val obj = JSONObject(json)
            DeviceCompatibility(
                isCompatible = true,
                manufacturer = obj.optString("manufacturer", ""),
                model = obj.optString("model", ""),
                osVersion = obj.optInt("osVersion", 0),
                apiLevel = obj.optInt("apiLevel", 0),
                features = obj.optString("features", "").split("|").filter { it.isNotEmpty() }.toSet(),
                issues = obj.optString("issues", "").split("|").filter { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if cache is valid
     */
    fun isCacheValid(): Boolean {
        val lastCheck = prefs.getLong(PREF_LAST_CHECK, 0)
        val now = System.currentTimeMillis()
        return (now - lastCheck) < CACHE_VALIDITY_MS
    }
    
    /**
     * Get last check time
     */
    fun getLastCheckTime(): Long {
        return prefs.getLong(PREF_LAST_CHECK, 0)
    }
    
    /**
     * Record sync time
     */
    fun recordSyncTime() {
        prefs.edit().putLong(PREF_LAST_SYNC, System.currentTimeMillis()).apply()
    }
    
    /**
     * Get last sync time
     */
    fun getLastSyncTime(): Long {
        return prefs.getLong(PREF_LAST_SYNC, 0)
    }
    
    /**
     * Clear compatibility state
     */
    fun clearState() {
        prefs.edit().apply {
            remove(PREF_LAST_CHECK)
            remove(PREF_COMPATIBILITY_STATUS)
            remove(PREF_DEVICE_INFO)
            remove(PREF_LAST_SYNC)
        }.apply()
    }
}
