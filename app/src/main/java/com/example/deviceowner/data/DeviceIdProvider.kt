package com.example.deviceowner.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Centralized Device ID provider - single source of truth for device identification.
 * Ensures consistent device ID across all API calls and prevents heartbeat failures.
 *
 * Features:
 * - Single source of truth (device_data.device_id_for_heartbeat)
 * - Automatic sync to backup locations
 * - Validation at save time (prevents invalid IDs)
 * - Thread-safe with ReentrantReadWriteLock
 * - Cache with TTL for performance
 * - Explicit error handling and logging
 *
 * Device ID sources (in priority order):
 * 1. device_id_for_heartbeat (primary - from server)
 * 2. device_registration.device_id (fallback)
 * 3. device_owner_prefs.device_id (fallback)
 * 4. control_prefs.device_id (fallback)
 *
 * Validation:
 * - Must not be null or empty
 * - Must not be "unknown" or locally-generated (ANDROID-*)
 * - Must be from server (not locally generated)
 */
object DeviceIdProvider {
    private const val TAG = "DeviceIdProvider"
    
    // Preference keys across different files
    private const val PREF_DEVICE_DATA = "device_data"
    private const val PREF_DEVICE_REGISTRATION = "device_registration"
    private const val PREF_DEVICE_OWNER = "device_owner_prefs"
    private const val PREF_CONTROL = "control_prefs"
    
    private const val KEY_DEVICE_ID_FOR_HEARTBEAT = "device_id_for_heartbeat"
    private const val KEY_DEVICE_ID = "device_id"
    
    // Cache with TTL to reduce SharedPreferences access
    private var cachedDeviceId: String? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_TTL_MS = 30_000L // 30 seconds (shorter for reliability)
    
    private val lock = ReentrantReadWriteLock()
    
    /**
     * Get device ID with validation and caching.
     * Returns null if no valid device ID found.
     * Thread-safe and handles all error cases.
     */
    fun getDeviceId(context: Context): String? {
        lock.readLock().lock()
        try {
            // Check cache first
            if (cachedDeviceId != null && System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) {
                Log.d(TAG, "✅ Device ID from cache: ${cachedDeviceId?.take(8)}...")
                return cachedDeviceId
            }
        } finally {
            lock.readLock().unlock()
        }
        
        // Cache miss - fetch from preferences
        lock.writeLock().lock()
        try {
            val deviceId = retrieveDeviceIdFromPreferences(context)
            
            if (deviceId != null && isValidDeviceId(deviceId)) {
                cachedDeviceId = deviceId
                cacheTimestamp = System.currentTimeMillis()
                Log.d(TAG, "✅ Valid device ID retrieved: ${deviceId.take(8)}...")
                return deviceId
            } else {
                Log.w(TAG, "⚠️ Invalid or missing device ID: $deviceId")
                cachedDeviceId = null
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }
    
    /**
     * Save device ID from server response with validation.
     * Validates BEFORE saving to prevent invalid IDs in storage.
     * Automatically syncs to all backup locations.
     * Should be called after successful registration.
     */
    fun saveDeviceId(context: Context, deviceId: String) {
        // VALIDATE BEFORE SAVING (Issue #4 fix)
        if (!isValidDeviceId(deviceId)) {
            Log.e(TAG, "❌ Attempted to save invalid device ID: $deviceId")
            Log.e(TAG, "   Validation failed - device ID not saved to any location")
            return
        }
        
        lock.writeLock().lock()
        try {
            // Save to primary location (single source of truth)
            val prefs = context.getSharedPreferences(PREF_DEVICE_DATA, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_DEVICE_ID_FOR_HEARTBEAT, deviceId).apply()
            Log.i(TAG, "✅ Device ID saved to primary location (device_data.device_id_for_heartbeat)")
            
            // SYNC to backup locations (Issue #1 fix - consistency)
            syncDeviceIdToBackupLocations(context, deviceId)
            
            // Update cache
            cachedDeviceId = deviceId
            cacheTimestamp = System.currentTimeMillis()
            
            Log.i(TAG, "✅ Device ID saved and synced: ${deviceId.take(8)}...")
        } finally {
            lock.writeLock().unlock()
        }
    }
    
    /**
     * Sync device ID to all backup locations to prevent inconsistency.
     * Called automatically after saving to primary location.
     */
    private fun syncDeviceIdToBackupLocations(context: Context, deviceId: String) {
        try {
            context.getSharedPreferences(PREF_DEVICE_REGISTRATION, Context.MODE_PRIVATE)
                .edit().putString(KEY_DEVICE_ID, deviceId).apply()
            Log.d(TAG, "   ✓ Synced to device_registration.device_id")
        } catch (e: Exception) {
            Log.w(TAG, "   ⚠️ Failed to sync to device_registration: ${e.message}")
        }
        
        try {
            context.getSharedPreferences(PREF_DEVICE_OWNER, Context.MODE_PRIVATE)
                .edit().putString(KEY_DEVICE_ID, deviceId).apply()
            Log.d(TAG, "   ✓ Synced to device_owner_prefs.device_id")
        } catch (e: Exception) {
            Log.w(TAG, "   ⚠️ Failed to sync to device_owner_prefs: ${e.message}")
        }
        
        try {
            context.getSharedPreferences(PREF_CONTROL, Context.MODE_PRIVATE)
                .edit().putString(KEY_DEVICE_ID, deviceId).apply()
            Log.d(TAG, "   ✓ Synced to control_prefs.device_id")
        } catch (e: Exception) {
            Log.w(TAG, "   ⚠️ Failed to sync to control_prefs: ${e.message}")
        }
    }
    
    /**
     * Verify and repair device ID consistency across all locations.
     * Called periodically to detect and fix sync issues.
     * Returns true if all locations are consistent, false if repairs were needed.
     */
    fun verifyAndRepairConsistency(context: Context): Boolean {
        lock.writeLock().lock()
        try {
            val primaryId = context.getSharedPreferences(PREF_DEVICE_DATA, Context.MODE_PRIVATE)
                .getString(KEY_DEVICE_ID_FOR_HEARTBEAT, null)
            
            if (primaryId == null) {
                Log.w(TAG, "⚠️ Primary device ID is null - cannot repair")
                return false
            }
            
            if (!isValidDeviceId(primaryId)) {
                Log.w(TAG, "⚠️ Primary device ID is invalid - cannot repair")
                return false
            }
            
            // Check all backup locations
            val backupId1 = context.getSharedPreferences(PREF_DEVICE_REGISTRATION, Context.MODE_PRIVATE)
                .getString(KEY_DEVICE_ID, null)
            val backupId2 = context.getSharedPreferences(PREF_DEVICE_OWNER, Context.MODE_PRIVATE)
                .getString(KEY_DEVICE_ID, null)
            val backupId3 = context.getSharedPreferences(PREF_CONTROL, Context.MODE_PRIVATE)
                .getString(KEY_DEVICE_ID, null)
            
            // Check consistency
            val allConsistent = primaryId == backupId1 && primaryId == backupId2 && primaryId == backupId3
            
            if (allConsistent) {
                Log.d(TAG, "✅ Device ID consistency verified across all locations")
                return true
            }
            
            // Repair: sync primary to all backups
            Log.w(TAG, "⚠️ Device ID inconsistency detected - repairing...")
            Log.w(TAG, "   Primary: ${primaryId.take(8)}...")
            Log.w(TAG, "   Backup1: ${backupId1?.take(8)}...")
            Log.w(TAG, "   Backup2: ${backupId2?.take(8)}...")
            Log.w(TAG, "   Backup3: ${backupId3?.take(8)}...")
            
            syncDeviceIdToBackupLocations(context, primaryId)
            Log.i(TAG, "✅ Device ID consistency repaired")
            return false
        } finally {
            lock.writeLock().unlock()
        }
    }
    
    /**
     * Clear cached device ID (e.g., on deactivation).
     * Does NOT clear SharedPreferences - only clears in-memory cache.
     */
    fun clearCache() {
        lock.writeLock().lock()
        try {
            cachedDeviceId = null
            cacheTimestamp = 0
            Log.d(TAG, "🗑️ Device ID cache cleared")
        } finally {
            lock.writeLock().unlock()
        }
    }
    
    /**
     * Clear all device ID data (SharedPreferences + cache).
     * Called during deactivation or device reset.
     */
    fun clearAllDeviceIdData(context: Context) {
        lock.writeLock().lock()
        try {
            context.getSharedPreferences(PREF_DEVICE_DATA, Context.MODE_PRIVATE)
                .edit().remove(KEY_DEVICE_ID_FOR_HEARTBEAT).apply()
            context.getSharedPreferences(PREF_DEVICE_REGISTRATION, Context.MODE_PRIVATE)
                .edit().remove(KEY_DEVICE_ID).apply()
            context.getSharedPreferences(PREF_DEVICE_OWNER, Context.MODE_PRIVATE)
                .edit().remove(KEY_DEVICE_ID).apply()
            context.getSharedPreferences(PREF_CONTROL, Context.MODE_PRIVATE)
                .edit().remove(KEY_DEVICE_ID).apply()
            
            cachedDeviceId = null
            cacheTimestamp = 0
            
            Log.i(TAG, "🗑️ All device ID data cleared")
        } finally {
            lock.writeLock().unlock()
        }
    }
    
    /**
     * Validate device ID format and source.
     * Returns true only if device ID is from server (not locally generated).
     */
    private fun isValidDeviceId(deviceId: String?): Boolean {
        if (deviceId.isNullOrBlank()) {
            Log.w(TAG, "❌ Device ID is null or blank")
            return false
        }
        
        if (deviceId.equals("unknown", ignoreCase = true)) {
            Log.w(TAG, "❌ Device ID is 'unknown' (locally generated)")
            return false
        }
        
        if (deviceId.startsWith("ANDROID-", ignoreCase = true)) {
            Log.w(TAG, "❌ Device ID appears to be locally generated: $deviceId")
            return false
        }
        
        // Valid device IDs should be UUID or alphanumeric from server
        if (!deviceId.matches(Regex("^[a-zA-Z0-9\\-]{8,}$"))) {
            Log.w(TAG, "❌ Device ID format invalid: $deviceId")
            return false
        }
        
        return true
    }
    
    /**
     * Retrieve device ID from preferences (checks all locations).
     */
    private fun retrieveDeviceIdFromPreferences(context: Context): String? {
        // Priority 1: device_id_for_heartbeat (primary)
        var deviceId = context.getSharedPreferences(PREF_DEVICE_DATA, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_ID_FOR_HEARTBEAT, null)
        if (deviceId != null) {
            Log.d(TAG, "📍 Device ID from device_data.device_id_for_heartbeat")
            return deviceId
        }
        
        // Priority 2: device_registration.device_id
        deviceId = context.getSharedPreferences(PREF_DEVICE_REGISTRATION, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_ID, null)
        if (deviceId != null) {
            Log.d(TAG, "📍 Device ID from device_registration.device_id (fallback)")
            return deviceId
        }
        
        // Priority 3: device_owner_prefs.device_id
        deviceId = context.getSharedPreferences(PREF_DEVICE_OWNER, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_ID, null)
        if (deviceId != null) {
            Log.d(TAG, "📍 Device ID from device_owner_prefs.device_id (fallback)")
            return deviceId
        }
        
        // Priority 4: control_prefs.device_id
        deviceId = context.getSharedPreferences(PREF_CONTROL, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_ID, null)
        if (deviceId != null) {
            Log.d(TAG, "📍 Device ID from control_prefs.device_id (fallback)")
            return deviceId
        }
        
        Log.w(TAG, "❌ Device ID not found in any preference location")
        return null
    }
    
    /**
     * Get device ID or throw exception if not available.
     * Use when device ID is required for operation.
     */
    fun getDeviceIdOrThrow(context: Context): String {
        return getDeviceId(context) ?: throw IllegalStateException(
            "Device ID not available. Device must be registered first."
        )
    }
    
    /**
     * Check if device is registered (has valid device ID).
     */
    fun isDeviceRegistered(context: Context): Boolean {
        return getDeviceId(context) != null
    }
}
