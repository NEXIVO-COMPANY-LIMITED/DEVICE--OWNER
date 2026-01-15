package com.example.deviceowner.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.deviceowner.data.models.UnifiedHeartbeatData
import com.example.deviceowner.data.models.OfflineHeartbeatData
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Unified Heartbeat Storage Manager
 * Handles BOTH online and offline storage with identical format
 * 
 * Storage locations:
 * 1. Protected cache: /data/data/app/cache/protected_heartbeat/
 * 2. SharedPreferences: heartbeat_storage
 * 3. Room Database: device_heartbeat_history
 * 
 * All use the same UnifiedHeartbeatData format
 */
class UnifiedHeartbeatStorage(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "heartbeat_storage",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val database = AppDatabase.getInstance(context)
    
    private val protectedCacheDir: File by lazy {
        val cacheDir = File(context.cacheDir, "protected_heartbeat")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            setDirectoryProtection(cacheDir)
        }
        cacheDir
    }
    
    companion object {
        private const val TAG = "UnifiedHeartbeatStorage"
        
        // Cache file names
        private const val CURRENT_HEARTBEAT_FILE = "current_heartbeat.cache"
        private const val VERIFIED_HEARTBEAT_FILE = "verified_heartbeat.cache"
        private const val BASELINE_HEARTBEAT_FILE = "baseline_heartbeat.cache"
        private const val HEARTBEAT_HISTORY_FILE = "heartbeat_history.cache"
        private const val OFFLINE_BASELINE_FILE = "offline_baseline.cache"
        
        // SharedPreferences keys
        private const val KEY_CURRENT_HEARTBEAT = "current_heartbeat"
        private const val KEY_VERIFIED_HEARTBEAT = "verified_heartbeat"
        private const val KEY_BASELINE_HEARTBEAT = "baseline_heartbeat"
        private const val KEY_ENCRYPTION_KEY = "hb_encryption_key"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_SYNC_STATUS = "sync_status"
        private const val KEY_OFFLINE_BASELINE = "offline_baseline"
        
        // Max history entries
        private const val MAX_HISTORY_ENTRIES = 100
    }
    
    /**
     * Save current heartbeat data (collected from device)
     * Used for both online and offline modes
     */
    suspend fun saveCurrentHeartbeat(data: UnifiedHeartbeatData): Boolean {
        return try {
            Log.d(TAG, "Saving current heartbeat: ${data.deviceId}")
            
            val json = data.toJson()
            
            // Save to protected cache file
            val cacheFile = File(protectedCacheDir, CURRENT_HEARTBEAT_FILE)
            val encryptedData = encryptData(json.toByteArray())
            cacheFile.writeBytes(encryptedData)
            setFileProtection(cacheFile)
            
            // Save to SharedPreferences as backup
            prefs.edit().putString(KEY_CURRENT_HEARTBEAT, json).apply()
            
            // Save to database history
            addToHistory(data)
            
            Log.d(TAG, "✓ Current heartbeat saved")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error saving current heartbeat", e)
            false
        }
    }
    
    /**
     * Get current heartbeat data
     */
    suspend fun getCurrentHeartbeat(): UnifiedHeartbeatData? {
        return try {
            // Try to read from protected cache first
            val cacheFile = File(protectedCacheDir, CURRENT_HEARTBEAT_FILE)
            if (cacheFile.exists()) {
                val encryptedData = cacheFile.readBytes()
                val decryptedData = decryptData(encryptedData)
                val json = String(decryptedData)
                return UnifiedHeartbeatData.fromJson(json)
            }
            
            // Fallback to SharedPreferences
            val json = prefs.getString(KEY_CURRENT_HEARTBEAT, null) ?: return null
            UnifiedHeartbeatData.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error retrieving current heartbeat", e)
            null
        }
    }
    
    /**
     * Save verified heartbeat data (from server or local baseline)
     * This is the reference data for comparison
     */
    suspend fun saveVerifiedHeartbeat(data: UnifiedHeartbeatData): Boolean {
        return try {
            Log.d(TAG, "Saving verified heartbeat: ${data.deviceId}")
            
            val json = data.toJson()
            
            // Save to protected cache file
            val cacheFile = File(protectedCacheDir, VERIFIED_HEARTBEAT_FILE)
            val encryptedData = encryptData(json.toByteArray())
            cacheFile.writeBytes(encryptedData)
            setFileProtection(cacheFile)
            
            // Save to SharedPreferences as backup
            prefs.edit().putString(KEY_VERIFIED_HEARTBEAT, json).apply()
            
            // Update sync status
            prefs.edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()
            prefs.edit().putString(KEY_SYNC_STATUS, "SYNCED").apply()
            
            Log.d(TAG, "✓ Verified heartbeat saved")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error saving verified heartbeat", e)
            false
        }
    }
    
    /**
     * Get verified heartbeat data
     */
    suspend fun getVerifiedHeartbeat(): UnifiedHeartbeatData? {
        return try {
            // Try to read from protected cache first
            val cacheFile = File(protectedCacheDir, VERIFIED_HEARTBEAT_FILE)
            if (cacheFile.exists()) {
                val encryptedData = cacheFile.readBytes()
                val decryptedData = decryptData(encryptedData)
                val json = String(decryptedData)
                return UnifiedHeartbeatData.fromJson(json)
            }
            
            // Fallback to SharedPreferences
            val json = prefs.getString(KEY_VERIFIED_HEARTBEAT, null) ?: return null
            UnifiedHeartbeatData.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error retrieving verified heartbeat", e)
            null
        }
    }
    
    /**
     * Save baseline heartbeat (initial registration data)
     * Used for offline comparison when server is unreachable
     */
    suspend fun saveBaselineHeartbeat(data: UnifiedHeartbeatData): Boolean {
        return try {
            Log.d(TAG, "Saving baseline heartbeat: ${data.deviceId}")
            
            val json = data.toJson()
            
            // Save to protected cache file
            val cacheFile = File(protectedCacheDir, BASELINE_HEARTBEAT_FILE)
            val encryptedData = encryptData(json.toByteArray())
            cacheFile.writeBytes(encryptedData)
            setFileProtection(cacheFile)
            
            // Save to SharedPreferences as backup
            prefs.edit().putString(KEY_BASELINE_HEARTBEAT, json).apply()
            
            Log.d(TAG, "✓ Baseline heartbeat saved")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error saving baseline heartbeat", e)
            false
        }
    }
    
    /**
     * Get baseline heartbeat data
     */
    suspend fun getBaselineHeartbeat(): UnifiedHeartbeatData? {
        return try {
            // Try to read from protected cache first
            val cacheFile = File(protectedCacheDir, BASELINE_HEARTBEAT_FILE)
            if (cacheFile.exists()) {
                val encryptedData = cacheFile.readBytes()
                val decryptedData = decryptData(encryptedData)
                val json = String(decryptedData)
                return UnifiedHeartbeatData.fromJson(json)
            }
            
            // Fallback to SharedPreferences
            val json = prefs.getString(KEY_BASELINE_HEARTBEAT, null) ?: return null
            UnifiedHeartbeatData.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error retrieving baseline heartbeat", e)
            null
        }
    }
    
    /**
     * Add heartbeat to history
     */
    private suspend fun addToHistory(data: UnifiedHeartbeatData) {
        try {
            val history = getHeartbeatHistory().toMutableList()
            history.add(data)
            
            // Keep only last MAX_HISTORY_ENTRIES
            if (history.size > MAX_HISTORY_ENTRIES) {
                history.removeAt(0)
            }
            
            // Save to cache
            val json = gson.toJson(history)
            val cacheFile = File(protectedCacheDir, HEARTBEAT_HISTORY_FILE)
            val encryptedData = encryptData(json.toByteArray())
            cacheFile.writeBytes(encryptedData)
            setFileProtection(cacheFile)
            
            Log.d(TAG, "✓ Added to history: ${history.size} entries")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error adding to history", e)
        }
    }
    
    /**
     * Get heartbeat history
     */
    suspend fun getHeartbeatHistory(): List<UnifiedHeartbeatData> {
        return try {
            val cacheFile = File(protectedCacheDir, HEARTBEAT_HISTORY_FILE)
            if (cacheFile.exists()) {
                val encryptedData = cacheFile.readBytes()
                val decryptedData = decryptData(encryptedData)
                val json = String(decryptedData)
                val type = object : com.google.gson.reflect.TypeToken<List<UnifiedHeartbeatData>>() {}.type
                return gson.fromJson(json, type) ?: emptyList()
            }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error retrieving history", e)
            emptyList()
        }
    }
    
    /**
     * Get sync status
     */
    fun getSyncStatus(): String {
        return prefs.getString(KEY_SYNC_STATUS, "PENDING") ?: "PENDING"
    }
    
    /**
     * Get last sync time
     */
    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }
    
    /**
     * Update sync status
     */
    fun updateSyncStatus(status: String) {
        prefs.edit().putString(KEY_SYNC_STATUS, status).apply()
    }
    
    /**
     * Clear all heartbeat data (on device wipe)
     */
    suspend fun clearAllHeartbeatData(): Boolean {
        return try {
            Log.w(TAG, "Clearing all heartbeat data")
            
            // Clear cache files
            File(protectedCacheDir, CURRENT_HEARTBEAT_FILE).delete()
            File(protectedCacheDir, VERIFIED_HEARTBEAT_FILE).delete()
            File(protectedCacheDir, BASELINE_HEARTBEAT_FILE).delete()
            File(protectedCacheDir, HEARTBEAT_HISTORY_FILE).delete()
            File(protectedCacheDir, OFFLINE_BASELINE_FILE).delete()
            
            // Clear SharedPreferences
            prefs.edit().clear().apply()
            
            Log.d(TAG, "✓ All heartbeat data cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error clearing heartbeat data", e)
            false
        }
    }
    
    // ==================== OFFLINE DETECTION METHODS ====================
    
    /**
     * Save offline baseline for local tamper detection
     * This is the reference data used when server is unreachable
     */
    suspend fun saveOfflineBaseline(data: UnifiedHeartbeatData): Boolean {
        return try {
            Log.d(TAG, "Saving offline baseline: ${data.deviceId}")
            
            val offlineData = data.toCompactOfflineData()
            val json = offlineData.toJson()
            
            // Save to protected cache file
            val cacheFile = File(protectedCacheDir, OFFLINE_BASELINE_FILE)
            val encryptedData = encryptData(json.toByteArray())
            cacheFile.writeBytes(encryptedData)
            setFileProtection(cacheFile)
            
            // Save to SharedPreferences as backup
            prefs.edit().putString(KEY_OFFLINE_BASELINE, json).apply()
            
            Log.d(TAG, "✓ Offline baseline saved")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error saving offline baseline", e)
            false
        }
    }
    
    /**
     * Get offline baseline for comparison
     */
    suspend fun getOfflineBaseline(): OfflineHeartbeatData? {
        return try {
            // Try to read from protected cache first
            val cacheFile = File(protectedCacheDir, OFFLINE_BASELINE_FILE)
            if (cacheFile.exists()) {
                val encryptedData = cacheFile.readBytes()
                val decryptedData = decryptData(encryptedData)
                val json = String(decryptedData)
                return OfflineHeartbeatData.fromJson(json)
            }
            
            // Fallback to SharedPreferences
            val json = prefs.getString(KEY_OFFLINE_BASELINE, null) ?: return null
            OfflineHeartbeatData.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error retrieving offline baseline", e)
            null
        }
    }
    
    /**
     * Check if offline baseline exists
     */
    fun hasOfflineBaseline(): Boolean {
        return try {
            val cacheFile = File(protectedCacheDir, OFFLINE_BASELINE_FILE)
            cacheFile.exists() || prefs.contains(KEY_OFFLINE_BASELINE)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get time since offline baseline was created
     */
    suspend fun getOfflineBaselineAge(): Long {
        return try {
            val baseline = getOfflineBaseline() ?: return -1L
            System.currentTimeMillis() - baseline.timestamp
        } catch (e: Exception) {
            -1L
        }
    }
    
    // ==================== Encryption/Decryption ====================
    
    /**
     * Encrypt data using AES-256
     */
    private fun encryptData(data: ByteArray): ByteArray {
        return try {
            val key = getOrCreateEncryptionKey()
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting data", e)
            data // Return unencrypted if encryption fails
        }
    }
    
    /**
     * Decrypt data using AES-256
     */
    private fun decryptData(encryptedData: ByteArray): ByteArray {
        return try {
            val key = getOrCreateEncryptionKey()
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.DECRYPT_MODE, key)
            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting data", e)
            encryptedData // Return as-is if decryption fails
        }
    }
    
    /**
     * Get or create encryption key
     */
    private fun getOrCreateEncryptionKey(): SecretKey {
        return try {
            val keyString = prefs.getString(KEY_ENCRYPTION_KEY, null)
            if (keyString != null) {
                val decodedKey = android.util.Base64.decode(keyString, android.util.Base64.DEFAULT)
                return SecretKeySpec(decodedKey, 0, decodedKey.size, "AES")
            }
            
            // Generate new key
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            val key = keyGenerator.generateKey()
            
            // Save key
            val encodedKey = android.util.Base64.encodeToString(key.encoded, android.util.Base64.DEFAULT)
            prefs.edit().putString(KEY_ENCRYPTION_KEY, encodedKey).apply()
            
            key
        } catch (e: Exception) {
            Log.e(TAG, "Error getting encryption key", e)
            throw e
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
        } catch (e: Exception) {
            Log.e(TAG, "Error setting file protection", e)
        }
    }
    
    /**
     * Set directory protection
     */
    private fun setDirectoryProtection(dir: File) {
        try {
            dir.setReadable(false, false)
            dir.setReadable(true, true)
            dir.setWritable(false, false)
            dir.setWritable(true, true)
            dir.setExecutable(false, false)
            dir.setExecutable(true, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting directory protection", e)
        }
    }
}
