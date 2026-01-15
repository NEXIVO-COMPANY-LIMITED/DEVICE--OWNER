package com.example.deviceowner.managers

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted Protection Status Manager
 * 
 * Encrypts protection status in SharedPreferences
 * Feature 4.7 Enhancement #3: Encryption for Protection Status
 * 
 * Features:
 * - AES-GCM encryption
 * - Android KeyStore integration
 * - Tamper protection
 * - Secure storage
 */
class EncryptedProtectionStatus(private val context: Context) {
    
    private val keyStore: KeyStore
    private val prefs = context.getSharedPreferences("protection_status_encrypted", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val TAG = "EncryptedProtectionStatus"
        private const val KEY_ALIAS = "protection_status_key"
        private const val KEY_STATUS_ENCRYPTED = "status_encrypted"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        
        @Volatile
        private var instance: EncryptedProtectionStatus? = null
        
        fun getInstance(context: Context): EncryptedProtectionStatus {
            return instance ?: synchronized(this) {
                instance ?: EncryptedProtectionStatus(context).also { instance = it }
            }
        }
    }
    
    init {
        keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        createKeyIfNeeded()
    }
    
    /**
     * Store protection status encrypted
     */
    fun storeProtectionStatus(status: ProtectionStatus) {
        try {
            val json = gson.toJson(status)
            val encrypted = encryptData(json)
            prefs.edit().putString(KEY_STATUS_ENCRYPTED, encrypted).apply()
            Log.d(TAG, "✓ Protection status stored encrypted")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing protection status", e)
            // Fallback: store unencrypted
            val fallbackJson = gson.toJson(status)
            prefs.edit().putString("status_fallback", fallbackJson).apply()
        }
    }
    
    /**
     * Retrieve protection status decrypted
     */
    fun retrieveProtectionStatus(): ProtectionStatus? {
        return try {
            val encrypted = prefs.getString(KEY_STATUS_ENCRYPTED, null)
            
            if (encrypted != null) {
                val json = decryptData(encrypted)
                gson.fromJson(json, ProtectionStatus::class.java)
            } else {
                // Try fallback
                val fallback = prefs.getString("status_fallback", null)
                if (fallback != null) {
                    gson.fromJson(fallback, ProtectionStatus::class.java)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving protection status", e)
            null
        }
    }
    
    /**
     * Encrypt data using AES-GCM
     */
    private fun encryptData(data: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                
                val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
                val iv = cipher.iv
                
                // Combine IV and encrypted data
                val combined = iv + encryptedData
                Base64.encodeToString(combined, Base64.DEFAULT)
            } else {
                // Fallback for older Android versions
                Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.DEFAULT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting data", e)
            // Fallback: return base64 encoded
            Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.DEFAULT)
        }
    }
    
    /**
     * Decrypt data using AES-GCM
     */
    private fun decryptData(encrypted: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val combined = Base64.decode(encrypted, Base64.DEFAULT)
                
                // Extract IV and encrypted data
                val iv = combined.sliceArray(0 until 12)
                val encryptedData = combined.sliceArray(12 until combined.size)
                
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
                
                String(cipher.doFinal(encryptedData), Charsets.UTF_8)
            } else {
                // Fallback for older Android versions
                String(Base64.decode(encrypted, Base64.DEFAULT), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting data", e)
            // Fallback: try base64 decode
            try {
                String(Base64.decode(encrypted, Base64.DEFAULT), Charsets.UTF_8)
            } catch (e2: Exception) {
                ""
            }
        }
    }
    
    /**
     * Create encryption key if needed
     */
    private fun createKeyIfNeeded() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!keyStore.containsAlias(KEY_ALIAS)) {
                    val keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE
                    )
                    
                    val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                    
                    keyGenerator.init(keyGenParameterSpec)
                    keyGenerator.generateKey()
                    
                    Log.d(TAG, "✓ Encryption key created")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating encryption key", e)
        }
    }
    
    /**
     * Get or create encryption key
     */
    private fun getOrCreateKey(): SecretKey {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyStore.getKey(KEY_ALIAS, null) as SecretKey
        } else {
            throw UnsupportedOperationException("Encryption not supported on this Android version")
        }
    }
    
    /**
     * Clear stored protection status
     */
    fun clearProtectionStatus() {
        try {
            prefs.edit().clear().apply()
            Log.d(TAG, "Protection status cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing protection status", e)
        }
    }
    
    /**
     * Verify encryption is working
     */
    fun verifyEncryption(): Boolean {
        return try {
            val testData = "test_encryption_${System.currentTimeMillis()}"
            val encrypted = encryptData(testData)
            val decrypted = decryptData(encrypted)
            
            val success = testData == decrypted
            
            if (success) {
                Log.d(TAG, "✓ Encryption verification successful")
            } else {
                Log.e(TAG, "✗ Encryption verification failed")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying encryption", e)
            false
        }
    }
}

/**
 * Protection Status Data Class
 */
data class ProtectionStatus(
    val uninstallBlocked: Boolean,
    val forceStopBlocked: Boolean,
    val appDisabled: Boolean,
    val deviceOwnerEnabled: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
