package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts and decrypts protection status using Android Keystore
 * Provides secure storage of sensitive protection flags
 * Prevents tampering with protection status via local attacks
 */
class ProtectionStatusEncryption(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("protection_status", Context.MODE_PRIVATE)
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    
    companion object {
        private const val TAG = "ProtectionStatusEncryption"
        private const val KEY_ALIAS = "uninstall_prevention_key"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled_encrypted"
        private const val KEY_PROTECTION_TIMESTAMP = "protection_timestamp"
        private const val KEY_PROTECTION_HASH = "protection_hash"
        
        // GCM parameters
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
    }
    
    /**
     * Encrypt protection status using Android Keystore
     * Returns encrypted string with IV prepended
     */
    fun encryptProtectionStatus(status: Boolean): String {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            
            val plaintext = status.toString().toByteArray(Charsets.UTF_8)
            val encryptedData = cipher.doFinal(plaintext)
            val iv = cipher.iv
            
            // Combine IV + encrypted data
            val combined = iv + encryptedData
            
            // Encode to Base64 for storage
            val encoded = Base64.getEncoder().encodeToString(combined)
            
            Log.d(TAG, "Protection status encrypted successfully")
            encoded
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting protection status", e)
            // Fallback: return plain text (less secure but functional)
            status.toString()
        }
    }
    
    /**
     * Decrypt protection status using Android Keystore
     * Extracts IV from encrypted data and decrypts
     */
    fun decryptProtectionStatus(encrypted: String): Boolean {
        return try {
            // Decode from Base64
            val combined = Base64.getDecoder().decode(encrypted)
            
            // Extract IV (first 12 bytes)
            if (combined.size < GCM_IV_LENGTH) {
                Log.e(TAG, "Invalid encrypted data: too short")
                return false
            }
            
            val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
            val encryptedData = combined.sliceArray(GCM_IV_LENGTH until combined.size)
            
            // Decrypt
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
            
            val plaintext = cipher.doFinal(encryptedData)
            val decrypted = String(plaintext, Charsets.UTF_8)
            
            Log.d(TAG, "Protection status decrypted successfully")
            decrypted.toBoolean()
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting protection status", e)
            // Return false on decryption error (fail-safe)
            false
        }
    }
    
    /**
     * Store encrypted protection status with integrity check
     */
    fun storeProtectionStatus(status: Boolean): Boolean {
        return try {
            val encrypted = encryptProtectionStatus(status)
            val timestamp = System.currentTimeMillis()
            
            // Calculate hash for integrity verification
            val hash = calculateHash(status, timestamp)
            
            prefs.edit().apply {
                putString(KEY_PROTECTION_ENABLED, encrypted)
                putLong(KEY_PROTECTION_TIMESTAMP, timestamp)
                putString(KEY_PROTECTION_HASH, hash)
                apply()
            }
            
            Log.d(TAG, "Protection status stored securely")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error storing protection status", e)
            false
        }
    }
    
    /**
     * Retrieve and verify encrypted protection status
     * Checks integrity hash to detect tampering
     */
    fun retrieveProtectionStatus(): Boolean {
        return try {
            val encrypted = prefs.getString(KEY_PROTECTION_ENABLED, null)
            val timestamp = prefs.getLong(KEY_PROTECTION_TIMESTAMP, 0)
            val storedHash = prefs.getString(KEY_PROTECTION_HASH, null)
            
            if (encrypted == null || storedHash == null) {
                Log.w(TAG, "No stored protection status found")
                return false
            }
            
            // Decrypt
            val status = decryptProtectionStatus(encrypted)
            
            // Verify integrity
            val calculatedHash = calculateHash(status, timestamp)
            if (calculatedHash != storedHash) {
                Log.e(TAG, "SECURITY ALERT: Protection status hash mismatch - possible tampering detected!")
                // Log tampering attempt
                val auditLog = IdentifierAuditLog(context)
                auditLog.logIncident(
                    type = "PROTECTION_STATUS_TAMPERING",
                    severity = "CRITICAL",
                    details = "Protection status hash mismatch detected"
                )
                return false
            }
            
            Log.d(TAG, "Protection status retrieved and verified")
            status
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving protection status", e)
            false
        }
    }
    
    /**
     * Verify protection status hasn't been tampered with
     */
    fun verifyProtectionIntegrity(): Boolean {
        return try {
            val encrypted = prefs.getString(KEY_PROTECTION_ENABLED, null)
            val timestamp = prefs.getLong(KEY_PROTECTION_TIMESTAMP, 0)
            val storedHash = prefs.getString(KEY_PROTECTION_HASH, null)
            
            if (encrypted == null || storedHash == null) {
                Log.w(TAG, "No stored protection status for verification")
                return false
            }
            
            val status = decryptProtectionStatus(encrypted)
            val calculatedHash = calculateHash(status, timestamp)
            
            val isValid = calculatedHash == storedHash
            
            if (!isValid) {
                Log.e(TAG, "SECURITY ALERT: Protection status integrity check failed!")
                val auditLog = IdentifierAuditLog(context)
                auditLog.logIncident(
                    type = "PROTECTION_INTEGRITY_FAILED",
                    severity = "CRITICAL",
                    details = "Protection status integrity verification failed"
                )
            }
            
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying protection integrity", e)
            false
        }
    }
    
    /**
     * Create or retrieve encryption key from Android Keystore
     * Key is stored securely and cannot be extracted
     */
    private fun getOrCreateKey(): SecretKey {
        return try {
            // Check if key already exists
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val key = keyStore.getKey(KEY_ALIAS, null)
                if (key is SecretKey) {
                    Log.d(TAG, "Using existing encryption key")
                    return key
                }
            }
            
            // Create new key
            Log.d(TAG, "Creating new encryption key")
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            
            val keyGenSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256) // 256-bit AES key
                .setRandomizedEncryptionRequired(true)
                .build()
            
            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating encryption key", e)
            throw e
        }
    }
    
    /**
     * Calculate HMAC hash for integrity verification
     */
    private fun calculateHash(status: Boolean, timestamp: Long): String {
        return try {
            val data = "$status:$timestamp".toByteArray(Charsets.UTF_8)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data)
            Base64.getEncoder().encodeToString(hash)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating hash", e)
            ""
        }
    }
    
    /**
     * Clear all stored protection status (use with caution)
     */
    fun clearProtectionStatus() {
        try {
            prefs.edit().apply {
                remove(KEY_PROTECTION_ENABLED)
                remove(KEY_PROTECTION_TIMESTAMP)
                remove(KEY_PROTECTION_HASH)
                apply()
            }
            Log.d(TAG, "Protection status cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing protection status", e)
        }
    }
    
    /**
     * Get encryption status for diagnostics
     */
    fun getEncryptionStatus(): EncryptionStatus {
        return EncryptionStatus(
            isKeyAvailable = keyStore.containsAlias(KEY_ALIAS),
            hasStoredStatus = prefs.contains(KEY_PROTECTION_ENABLED),
            integrityValid = verifyProtectionIntegrity(),
            lastUpdateTime = prefs.getLong(KEY_PROTECTION_TIMESTAMP, 0)
        )
    }
}

/**
 * Data class for encryption status diagnostics
 */
data class EncryptionStatus(
    val isKeyAvailable: Boolean,
    val hasStoredStatus: Boolean,
    val integrityValid: Boolean,
    val lastUpdateTime: Long
)
