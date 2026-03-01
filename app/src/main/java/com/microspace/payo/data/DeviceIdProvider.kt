package com.microspace.payo.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.microspace.payo.security.crypto.EncryptionManager
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Centralized Device ID provider with Auto-Recovery and Direct Boot support.
 * Stores and retrieves the server-assigned device ID securely.
 * Uses EncryptedSharedPreferences for data at rest protection.
 */
object DeviceIdProvider {
    private const val TAG = "DeviceIdProvider"
    
    private const val PREF_DEVICE_DATA = "device_data_secure"
    private const val PREF_DEVICE_REGISTRATION = "device_registration_secure"
    private const val KEY_DEVICE_ID_PRIMARY = "device_id_primary"
    private const val KEY_DEVICE_ID_BACKUP = "device_id"
    
    private val lock = ReentrantReadWriteLock()

    private fun getPrefs(context: Context, name: String) = 
        EncryptionManager(context).getEncryptedSharedPreferences(name)

    /**
     * Get device ID from storage.
     * Returns null if device is not registered.
     */
    fun getDeviceId(context: Context): String? {
        lock.readLock().lock()
        try {
            // 1. Check primary encrypted storage
            var id = getPrefs(context, PREF_DEVICE_DATA).getString(KEY_DEVICE_ID_PRIMARY, null)
            
            // 2. Fallback to backup encrypted location
            if (id.isNullOrBlank()) {
                id = getPrefs(context, PREF_DEVICE_REGISTRATION).getString(KEY_DEVICE_ID_BACKUP, null)
            }
            
            // 3. Android N+ Device Protected Storage Check
            // Note: EncryptedSharedPreferences may have limitations in Direct Boot 
            // depending on keystore availability, but we use it here for security.
            if (id.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !context.isDeviceProtectedStorage) {
                try {
                    val safeContext = context.createDeviceProtectedStorageContext()
                    id = getPrefs(safeContext, PREF_DEVICE_DATA).getString(KEY_DEVICE_ID_PRIMARY, null)
                } catch (_: Exception) {}
            }
            
            // 4. Legacy migration (one-time if switching from plain text)
            if (id.isNullOrBlank()) {
                id = context.getSharedPreferences("device_data", Context.MODE_PRIVATE)
                    .getString("device_id_primary", null)
                if (id != null) {
                    saveDeviceId(context, id) // Migrate to encrypted
                }
            }
            
            if (!id.isNullOrBlank()) {
                Log.d(TAG, "ðŸ” Retrieved Device ID (secure): $id")
                return id
            }
            
            return null
        } finally {
            lock.readLock().unlock()
        }
    }
    
    /**
     * Save device ID securely to both CE and DE storage.
     */
    fun saveDeviceId(context: Context, deviceId: String) {
        if (deviceId.isBlank()) {
            Log.e(TAG, "âŒ REJECTED: Attempted to save blank device ID")
            return
        }
        
        lock.writeLock().lock()
        try {
            // Save to primary and backup locations in encrypted storage
            getPrefs(context, PREF_DEVICE_DATA).edit()
                .putString(KEY_DEVICE_ID_PRIMARY, deviceId).apply()
            
            getPrefs(context, PREF_DEVICE_REGISTRATION).edit()
                .putString(KEY_DEVICE_ID_BACKUP, deviceId).apply()
            
            // Mirror to Device Protected Storage
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val safeContext = if (context.isDeviceProtectedStorage) context 
                                     else context.createDeviceProtectedStorageContext()
                    
                    getPrefs(safeContext, PREF_DEVICE_DATA).edit()
                        .putString(KEY_DEVICE_ID_PRIMARY, deviceId).apply()
                        
                    Log.i(TAG, "ðŸ’¾ Mirrored Device ID to Protected Encrypted Storage")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to mirror to protected storage: ${e.message}")
                }
            }
            
            Log.i(TAG, "âœ… Device ID saved securely: $deviceId")
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun isDeviceRegistered(context: Context): Boolean {
        return getDeviceId(context) != null
    }

    fun verifyAndRepairConsistency(context: Context): Boolean {
        lock.writeLock().lock()
        try {
            val idFromCurrent = getPrefs(context, PREF_DEVICE_DATA).getString(KEY_DEVICE_ID_PRIMARY, null)
            
            var idFromProtected: String? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val safeContext = if (context.isDeviceProtectedStorage) context 
                                     else context.createDeviceProtectedStorageContext()
                    idFromProtected = getPrefs(safeContext, PREF_DEVICE_DATA).getString(KEY_DEVICE_ID_PRIMARY, null)
                } catch (_: Exception) {}
            }
            
            val finalId = idFromCurrent ?: idFromProtected
            
            if (finalId != null) {
                if (idFromCurrent == null || idFromProtected == null) {
                    Log.w(TAG, "âš  Repairing Secure Device ID across storage boundaries...")
                    saveDeviceId(context, finalId)
                }
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Consistency repair failed: ${e.message}")
            return false
        } finally {
            lock.writeLock().unlock()
        }
    }
}




