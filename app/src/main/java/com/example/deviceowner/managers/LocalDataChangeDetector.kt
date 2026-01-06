package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects changes in device data locally
 * Monitors critical device properties for unauthorized modifications
 */
class LocalDataChangeDetector(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "local_data_detector",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val heartbeatManager = HeartbeatDataManager(context)
    
    companion object {
        private const val TAG = "LocalDataChangeDetector"
        private const val KEY_BASELINE_DATA = "baseline_data"
        private const val KEY_CHANGE_HISTORY = "change_history"
        private const val KEY_LAST_CHECK = "last_check"
        private const val MAX_HISTORY_SIZE = 50
    }
    
    /**
     * Initialize baseline data for comparison
     * Must be called from a coroutine
     */
    suspend fun initializeBaseline() {
        return withContext(Dispatchers.IO) {
            try {
                val currentData = heartbeatManager.collectHeartbeatData()
                val json = gson.toJson(currentData)
                prefs.edit().apply {
                    putString(KEY_BASELINE_DATA, json)
                    putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                    apply()
                }
                Log.d(TAG, "Baseline data initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing baseline", e)
            }
        }
    }
    
    /**
     * Check for changes since last baseline
     * Must be called from a coroutine
     */
    suspend fun checkForChanges(): LocalDataChangeDetectionResult {
        return withContext(Dispatchers.IO) {
            try {
                val currentData = heartbeatManager.collectHeartbeatData()
                val baselineJson = prefs.getString(KEY_BASELINE_DATA, null)
                
                if (baselineJson == null) {
                    Log.w(TAG, "No baseline data found, initializing")
                    initializeBaseline()
                    return@withContext LocalDataChangeDetectionResult(
                        hasChanges = false,
                        changes = emptyList(),
                        severity = "NONE"
                    )
                }
                
                val baselineData = gson.fromJson(baselineJson, HeartbeatData::class.java)
                val changes = mutableListOf<LocalDataChangeDetail>()
                var maxSeverity = "NONE"
                
                // Check critical identifiers
                if (currentData.imei != baselineData.imei) {
                    changes.add(LocalDataChangeDetail(
                        field = "IMEI",
                        baselineValue = baselineData.imei,
                        currentValue = currentData.imei,
                        severity = "CRITICAL",
                        changeType = "IDENTIFIER_CHANGE"
                    ))
                    maxSeverity = "CRITICAL"
                }
                
                if (currentData.serialNumber != baselineData.serialNumber) {
                    changes.add(LocalDataChangeDetail(
                        field = "Serial Number",
                        baselineValue = baselineData.serialNumber,
                        currentValue = currentData.serialNumber,
                        severity = "CRITICAL",
                        changeType = "IDENTIFIER_CHANGE"
                    ))
                    maxSeverity = "CRITICAL"
                }
                
                if (currentData.androidId != baselineData.androidId) {
                    changes.add(LocalDataChangeDetail(
                        field = "Android ID",
                        baselineValue = baselineData.androidId,
                        currentValue = currentData.androidId,
                        severity = "CRITICAL",
                        changeType = "IDENTIFIER_CHANGE"
                    ))
                    maxSeverity = "CRITICAL"
                }
                
                if (currentData.deviceFingerprint != baselineData.deviceFingerprint) {
                    changes.add(LocalDataChangeDetail(
                        field = "Device Fingerprint",
                        baselineValue = baselineData.deviceFingerprint,
                        currentValue = currentData.deviceFingerprint,
                        severity = "CRITICAL",
                        changeType = "FINGERPRINT_CHANGE"
                    ))
                    maxSeverity = "CRITICAL"
                }
                
                // Check security flags
                if (currentData.isDeviceRooted != baselineData.isDeviceRooted) {
                    changes.add(LocalDataChangeDetail(
                        field = "Root Status",
                        baselineValue = baselineData.isDeviceRooted.toString(),
                        currentValue = currentData.isDeviceRooted.toString(),
                        severity = "HIGH",
                        changeType = "SECURITY_FLAG_CHANGE"
                    ))
                    if (maxSeverity != "CRITICAL") maxSeverity = "HIGH"
                }
                
                if (currentData.isUSBDebuggingEnabled != baselineData.isUSBDebuggingEnabled) {
                    changes.add(LocalDataChangeDetail(
                        field = "USB Debugging",
                        baselineValue = baselineData.isUSBDebuggingEnabled.toString(),
                        currentValue = currentData.isUSBDebuggingEnabled.toString(),
                        severity = "HIGH",
                        changeType = "SECURITY_FLAG_CHANGE"
                    ))
                    if (maxSeverity != "CRITICAL") maxSeverity = "HIGH"
                }
                
                if (currentData.isDeveloperModeEnabled != baselineData.isDeveloperModeEnabled) {
                    changes.add(LocalDataChangeDetail(
                        field = "Developer Mode",
                        baselineValue = baselineData.isDeveloperModeEnabled.toString(),
                        currentValue = currentData.isDeveloperModeEnabled.toString(),
                        severity = "HIGH",
                        changeType = "SECURITY_FLAG_CHANGE"
                    ))
                    if (maxSeverity != "CRITICAL") maxSeverity = "HIGH"
                }
                
                // Check build properties
                if (currentData.bootloader != baselineData.bootloader) {
                    changes.add(LocalDataChangeDetail(
                        field = "Bootloader",
                        baselineValue = baselineData.bootloader,
                        currentValue = currentData.bootloader,
                        severity = "MEDIUM",
                        changeType = "BUILD_PROPERTY_CHANGE"
                    ))
                    if (maxSeverity == "NONE") maxSeverity = "MEDIUM"
                }
                
                if (currentData.hardware != baselineData.hardware) {
                    changes.add(LocalDataChangeDetail(
                        field = "Hardware",
                        baselineValue = baselineData.hardware,
                        currentValue = currentData.hardware,
                        severity = "MEDIUM",
                        changeType = "BUILD_PROPERTY_CHANGE"
                    ))
                    if (maxSeverity == "NONE") maxSeverity = "MEDIUM"
                }
                
                // Check app integrity
                if (currentData.installedAppsHash != baselineData.installedAppsHash) {
                    changes.add(LocalDataChangeDetail(
                        field = "Installed Apps",
                        baselineValue = baselineData.installedAppsHash,
                        currentValue = currentData.installedAppsHash,
                        severity = "MEDIUM",
                        changeType = "APP_INTEGRITY_CHANGE"
                    ))
                    if (maxSeverity == "NONE") maxSeverity = "MEDIUM"
                }
                
                // Check system properties
                if (currentData.systemPropertiesHash != baselineData.systemPropertiesHash) {
                    changes.add(LocalDataChangeDetail(
                        field = "System Properties",
                        baselineValue = baselineData.systemPropertiesHash,
                        currentValue = currentData.systemPropertiesHash,
                        severity = "MEDIUM",
                        changeType = "SYSTEM_PROPERTY_CHANGE"
                    ))
                    if (maxSeverity == "NONE") maxSeverity = "MEDIUM"
                }
                
                // Update last check time
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                
                // Add to history if changes detected
                if (changes.isNotEmpty()) {
                    addToChangeHistory(changes)
                    Log.w(TAG, "Detected ${changes.size} changes, max severity: $maxSeverity")
                }
                
                return@withContext LocalDataChangeDetectionResult(
                    hasChanges = changes.isNotEmpty(),
                    changes = changes,
                    severity = maxSeverity
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for changes", e)
                return@withContext LocalDataChangeDetectionResult(
                    hasChanges = false,
                    changes = emptyList(),
                    severity = "ERROR"
                )
            }
        }
    }
    
    /**
     * Update baseline after verification
     * Must be called from a coroutine
     */
    suspend fun updateBaseline() {
        return withContext(Dispatchers.IO) {
            try {
                val currentData = heartbeatManager.collectHeartbeatData()
                val json = gson.toJson(currentData)
                prefs.edit().apply {
                    putString(KEY_BASELINE_DATA, json)
                    putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                    apply()
                }
                Log.d(TAG, "Baseline updated")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating baseline", e)
            }
        }
    }
    
    /**
     * Get change history
     */
    fun getChangeHistory(): List<LocalDataChangeDetail> {
        return try {
            val json = prefs.getString(KEY_CHANGE_HISTORY, "[]") ?: "[]"
            val type = object : com.google.gson.reflect.TypeToken<List<LocalDataChangeDetail>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving change history", e)
            emptyList()
        }
    }
    
    /**
     * Add changes to history
     */
    private fun addToChangeHistory(changes: List<LocalDataChangeDetail>) {
        try {
            val history = getChangeHistory().toMutableList()
            history.addAll(changes)
            
            // Keep only last MAX_HISTORY_SIZE entries
            if (history.size > MAX_HISTORY_SIZE) {
                history.removeAll(history.take(history.size - MAX_HISTORY_SIZE))
            }
            
            val json = gson.toJson(history)
            prefs.edit().putString(KEY_CHANGE_HISTORY, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to change history", e)
        }
    }
    
    /**
     * Clear change history
     */
    fun clearChangeHistory() {
        try {
            prefs.edit().remove(KEY_CHANGE_HISTORY).apply()
            Log.d(TAG, "Change history cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing change history", e)
        }
    }
    
    /**
     * Get last check time
     */
    fun getLastCheckTime(): Long {
        return prefs.getLong(KEY_LAST_CHECK, 0L)
    }
    
    /**
     * Get time since last check
     */
    fun getTimeSinceLastCheck(): Long {
        return System.currentTimeMillis() - getLastCheckTime()
    }
}

/**
 * Result of local data change detection
 */
data class LocalDataChangeDetectionResult(
    val hasChanges: Boolean,
    val changes: List<LocalDataChangeDetail>,
    val severity: String // CRITICAL, HIGH, MEDIUM, LOW, NONE, ERROR
)

/**
 * Detail of a detected local data change
 */
data class LocalDataChangeDetail(
    val field: String,
    val baselineValue: String,
    val currentValue: String,
    val severity: String,
    val changeType: String, // IDENTIFIER_CHANGE, FINGERPRINT_CHANGE, SECURITY_FLAG_CHANGE, etc.
    val detectedAt: Long = System.currentTimeMillis()
)
