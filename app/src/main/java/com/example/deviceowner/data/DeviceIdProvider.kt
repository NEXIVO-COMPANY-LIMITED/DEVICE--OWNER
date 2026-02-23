package com.example.deviceowner.data

import android.content.Context
import android.os.Build
import android.util.Log
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Centralized Device ID provider with Auto-Recovery and Direct Boot support.
 * Stores and retrieves the server-assigned device ID (e.g., DEV-E324C72EDEC2).
 */
object DeviceIdProvider {
    private const val TAG = "DeviceIdProvider"
    
    private const val PREF_DEVICE_DATA = "device_data"
    private const val PREF_DEVICE_REGISTRATION = "device_registration"
    private const val KEY_DEVICE_ID_PRIMARY = "device_id_primary"
    private const val KEY_DEVICE_ID_BACKUP = "device_id"
    
    private val lock = ReentrantReadWriteLock()

    /**
     * Get device ID from storage.
     * Returns the server-assigned device ID (e.g., DEV-E324C72EDEC2)
     * Returns null if device is not registered.
     */
    fun getDeviceId(context: Context): String? {
        lock.readLock().lock()
        try {
            // 1. Check current context (might be device-protected if called from BootReceiver)
            var id = context.getSharedPreferences(PREF_DEVICE_DATA, Context.MODE_PRIVATE)
                .getString(KEY_DEVICE_ID_PRIMARY, null)
            
            // 2. Fallback to backup location in current context
            if (id.isNullOrBlank()) {
                id = context.getSharedPreferences(PREF_DEVICE_REGISTRATION, Context.MODE_PRIVATE)
                    .getString(KEY_DEVICE_ID_BACKUP, null)
            }
            
            // 3. If still null and on Android N+, try to check Device Protected Storage explicitly
            // if current context is NOT device-protected.
            if (id.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !context.isDeviceProtectedStorage) {
                try {
                    val safeContext = context.createDeviceProtectedStorageContext()
                    id = safeContext.getSharedPreferences(PREF_DEVICE_DATA, Context.MODE_PRIVATE)
                        .getString(KEY_DEVICE_ID_PRIMARY, null)
                } catch (_: Exception) {}
            }
            
            if (!id.isNullOrBlank()) {
                Log.d(TAG, "🔍 Retrieved Device ID: $id")
                return id
            }
            
            return null
        } finally {
            lock.readLock().unlock()
        }
    }
    
    /**
     * Save device ID from server registration response.
     * Saves to both Credential-Encrypted and Device-Protected storage for boot persistence.
     */
    fun saveDeviceId(context: Context, deviceId: String) {
        if (deviceId.isBlank()) {
            Log.e(TAG, "❌ REJECTED: Attempted to save blank device ID")
            return
        }
        
        lock.writeLock().lock()
        try {
            // Save to primary and backup locations in normal storage
            context.getSharedPreferences(PREF_DEVICE_DATA, Context.MODE_PRIVATE).edit()
                .putString(KEY_DEVICE_ID_PRIMARY, deviceId).apply()
            
            context.getSharedPreferences(PREF_DEVICE_REGISTRATION, Context.MODE_PRIVATE).edit()
                .putString(KEY_DEVICE_ID_BACKUP, deviceId).apply()
            
            // Sync to Device Protected Storage for persistence during Direct Boot
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val safeContext = if (context.isDeviceProtectedStorage) context 
                                     else context.createDeviceProtectedStorageContext()
                    
                    safeContext.getSharedPreferences(PREF_DEVICE_DATA, Context.MODE_PRIVATE).edit()
                        .putString(KEY_DEVICE_ID_PRIMARY, deviceId).apply()
                        
                    Log.i(TAG, "💾 Mirrored Device ID to Protected Storage")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to mirror to protected storage: ${e.message}")
                }
            }
            
            Log.i(TAG, "✅ Device ID saved successfully: $deviceId")
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun isDeviceRegistered(context: Context): Boolean {
        return getDeviceId(context) != null
    }

    /**
     * Repairs consistency. If one storage is wiped but the other remains, it restores the missing one.
     */
    fun verifyAndRepairConsistency(context: Context): Boolean {
        lock.writeLock().lock()
        try {
            val idFromCurrent = context.getSharedPreferences(PREF_DEVICE_DATA, Context.MODE_PRIVATE)
                .getString(KEY_DEVICE_ID_PRIMARY, null)
            
            var idFromProtected: String? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val safeContext = if (context.isDeviceProtectedStorage) context 
                                     else context.createDeviceProtectedStorageContext()
                    idFromProtected = safeContext.getSharedPreferences(PREF_DEVICE_DATA, Context.MODE_PRIVATE)
                        .getString(KEY_DEVICE_ID_PRIMARY, null)
                } catch (_: Exception) {}
            }
            
            val finalId = idFromCurrent ?: idFromProtected
            
            if (finalId != null) {
                if (idFromCurrent == null || idFromProtected == null) {
                    Log.w(TAG, "⚠ Repairing Device ID across storage boundaries...")
                    saveDeviceId(context, finalId)
                }
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Consistency repair failed: ${e.message}")
            return false
        } finally {
            lock.writeLock().unlock()
        }
    }
}
